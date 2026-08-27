package com.notcan.app.localai

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.ByteArrayOutputStream

/**
 * Lightweight provisional Spanish transcription while recording.
 * It receives the same 16 kHz PCM stream used by the recorder and decodes short chunks.
 * The definitive transcript is still generated later by Whisper large-v3-turbo.
 */
class LocalLiveTranscriber(
    private val modelManager: LiveTranscriptionModelManager,
    private val onTranscriptChunk: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var recognizer: OfflineRecognizer? = null
    private val pending = ByteArrayOutputStream(CHUNK_BYTES * 2)

    fun start(): Boolean {
        if (modelManager.state() != LiveTranscriptionModelState.INSTALLED) {
            onStatus("Descarga Moonshine ES para transcripción en vivo")
            return false
        }
        return try {
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        encoder = modelManager.encoderFile().absolutePath,
                        mergedDecoder = modelManager.decoderFile().absolutePath
                    ),
                    tokens = modelManager.tokensFile().absolutePath,
                    numThreads = 2,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
            recognizer = OfflineRecognizer(config = config)
            onStatus("Transcripción provisional en vivo · local")
            true
        } catch (t: Throwable) {
            onStatus("No se pudo iniciar la transcripción en vivo: ${t.message ?: "error"}")
            false
        }
    }

    fun acceptPcm16k(pcm: ByteArray) {
        val active = recognizer ?: return
        pending.write(pcm)
        while (pending.size() >= CHUNK_BYTES) {
            val all = pending.toByteArray()
            val chunk = all.copyOfRange(0, CHUNK_BYTES)
            pending.reset()
            if (all.size > CHUNK_BYTES) pending.write(all, CHUNK_BYTES, all.size - CHUNK_BYTES)
            decode(active, chunk)
        }
    }

    fun close() {
        val active = recognizer
        if (active != null && pending.size() >= MIN_FINAL_BYTES) {
            decode(active, pending.toByteArray())
        }
        pending.reset()
        runCatching { recognizer?.release() }
        recognizer = null
        onStatus("Transcripción en vivo finalizada")
    }

    private fun decode(active: OfflineRecognizer, pcm: ByteArray) {
        if (pcm.size < 2) return
        val sampleCount = pcm.size / 2
        val samples = FloatArray(sampleCount)
        var byteIndex = 0
        for (i in 0 until sampleCount) {
            val lo = pcm[byteIndex].toInt() and 0xff
            val hi = pcm[byteIndex + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort()
            samples[i] = sample / 32768.0f
            byteIndex += 2
        }
        val stream = active.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            active.decode(stream)
            active.getResult(stream).text.trim().takeIf { it.isNotBlank() }?.let(onTranscriptChunk)
        } catch (t: Throwable) {
            onStatus("Transcripción provisional interrumpida: ${t.message ?: "error"}")
        } finally {
            stream.release()
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_SECONDS = 6
        private const val CHUNK_BYTES = SAMPLE_RATE * 2 * CHUNK_SECONDS
        private const val MIN_FINAL_BYTES = SAMPLE_RATE * 2
    }
}
