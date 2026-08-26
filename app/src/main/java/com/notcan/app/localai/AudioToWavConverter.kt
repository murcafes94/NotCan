package com.notcan.app.localai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Converts the AAC/M4A recordings produced by NotCan into a temporary PCM WAV file. */
object AudioToWavConverter {
    fun convert(input: File, output: File) {
        require(input.exists()) { "No existe el audio original" }
        output.parentFile?.mkdirs()
        val raw = File(output.parentFile, output.nameWithoutExtension + ".pcm.tmp")
        if (raw.exists()) raw.delete()

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var sampleRate = 24_000
        var channelCount = 1
        try {
            extractor.setDataSource(input.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("El archivo no contiene una pista de audio compatible")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Formato de audio desconocido")
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            FileOutputStream(raw).use { sink ->
                val info = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec!!.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val buffer = codec!!.getInputBuffer(inputIndex) ?: error("No se pudo obtener buffer de entrada")
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec!!.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec!!.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec!!.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = codec!!.outputFormat
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val buffer = codec!!.getOutputBuffer(outputIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                sink.write(bytes)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec!!.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }

            writeWave(raw, output, sampleRate, channelCount)
        } finally {
            try { codec?.stop() } catch (_: Throwable) { }
            try { codec?.release() } catch (_: Throwable) { }
            try { extractor.release() } catch (_: Throwable) { }
            raw.delete()
        }
    }

    private fun writeWave(raw: File, output: File, sampleRate: Int, channels: Int) {
        val dataSize = raw.length()
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        FileOutputStream(output).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36L + dataSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1.toShort())
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            out.write(header.array())
            raw.inputStream().use { source -> source.copyTo(out, 128 * 1024) }
        }
    }
}
