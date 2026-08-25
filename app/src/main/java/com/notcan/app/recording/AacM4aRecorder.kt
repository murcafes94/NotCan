package com.notcan.app.recording

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Captures the microphone exactly once. The same 16-bit PCM stream is encoded to AAC/M4A
 * locally and, when enabled, forwarded to Gemini Live. Local recording never depends on AI.
 */
class AacM4aRecorder(
    private val outputFile: File,
    private val scope: CoroutineScope,
    private val onPcmChunk: (ByteArray) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val samplesEncoded = AtomicLong(0L)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var muxerTrack = -1
    private val bufferInfo = MediaCodec.BufferInfo()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (!running.compareAndSet(false, true)) return
        outputFile.parentFile?.mkdirs()
        setupCodec()
        setupAudioRecord()
        captureJob = scope.launch(Dispatchers.IO) { captureLoop() }
    }

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun isPaused(): Boolean = paused.get()
    fun elapsedMs(): Long = samplesEncoded.get() * 1000L / SAMPLE_RATE

    suspend fun stop(): Long {
        running.set(false)
        captureJob?.join()
        captureJob = null
        return elapsedMs()
    }

    private fun setupCodec() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PCM_BUFFER_BYTES)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioRecord() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val actualBuffer = max(PCM_BUFFER_BYTES, minBuffer * 2)
        audioRecord = AudioRecord.Builder()
            .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(actualBuffer)
            .build()
    }

    private fun captureLoop() {
        val mic = audioRecord ?: return
        val pcm = ByteArray(PCM_BUFFER_BYTES)
        try {
            mic.startRecording()
            while (running.get()) {
                val count = mic.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                if (paused.get()) continue
                val chunk = pcm.copyOf(count)
                onPcmChunk(chunk)
                feedEncoder(chunk)
                drainEncoder(endOfStream = false)
            }
            queueEndOfStream()
            drainEncoder(endOfStream = true)
        } finally {
            try { mic.stop() } catch (_: Throwable) { }
            try { mic.release() } catch (_: Throwable) { }
            audioRecord = null
            releaseCodecAndMuxer()
        }
    }

    private fun feedEncoder(bytes: ByteArray) {
        val encoder = codec ?: return
        var offset = 0
        while (offset < bytes.size) {
            val inputIndex = encoder.dequeueInputBuffer(10_000)
            if (inputIndex < 0) continue
            val input = encoder.getInputBuffer(inputIndex) ?: continue
            input.clear()
            val toWrite = minOf(input.remaining(), bytes.size - offset)
            input.put(bytes, offset, toWrite)
            val samplesBefore = samplesEncoded.get()
            val presentationTimeUs = samplesBefore * 1_000_000L / SAMPLE_RATE
            encoder.queueInputBuffer(inputIndex, 0, toWrite, presentationTimeUs, 0)
            samplesEncoded.addAndGet((toWrite / BYTES_PER_SAMPLE).toLong())
            offset += toWrite
        }
    }

    private fun queueEndOfStream() {
        val encoder = codec ?: return
        while (true) {
            val index = encoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val pts = samplesEncoded.get() * 1_000_000L / SAMPLE_RATE
                encoder.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val encoder = codec ?: return
        var idleCount = 0
        while (true) {
            when (val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++idleCount > 50) return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) error("El formato AAC cambió dos veces")
                    muxerTrack = muxer!!.addTrack(encoder.outputFormat)
                    muxer!!.start()
                    muxerStarted = true
                }
                else -> if (outputIndex >= 0) {
                    val output = encoder.getOutputBuffer(outputIndex)
                    if (output != null && bufferInfo.size > 0 && muxerStarted &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        muxer?.writeSampleData(muxerTrack, output, bufferInfo)
                    }
                    val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (eos) return
                }
            }
        }
    }

    private fun releaseCodecAndMuxer() {
        try { codec?.stop() } catch (_: Throwable) { }
        try { codec?.release() } catch (_: Throwable) { }
        codec = null
        if (muxerStarted) try { muxer?.stop() } catch (_: Throwable) { }
        try { muxer?.release() } catch (_: Throwable) { }
        muxer = null
        muxerStarted = false
        muxerTrack = -1
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val AAC_BIT_RATE = 64_000
        private const val PCM_BUFFER_BYTES = 4096
    }
}
