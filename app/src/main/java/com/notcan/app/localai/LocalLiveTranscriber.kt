package com.notcan.app.localai

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Transcripción provisional en vivo, totalmente local.
 *
 * Moonshine ES sigue siendo el reconocedor. Cuando Silero VAD está disponible,
 * el audio se agrupa por frases reales y pausas; si VAD no puede iniciarse,
 * NotCan conserva automáticamente el comportamiento compatible por bloques.
 * Ese fallback usa 400 ms de solapamiento para no perder palabras cortadas y
 * elimina únicamente la repetición evidente producida por ese solapamiento.
 */
class LocalLiveTranscriber(
    private val modelManager: LiveTranscriptionModelManager,
    private val onTranscriptChunk: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private val vadPending = ByteArrayOutputStream(VAD_WINDOW_BYTES * 4)
    private val fallbackPending = ByteArrayOutputStream(FALLBACK_CHUNK_BYTES * 2)
    private var lastFallbackText: String = ""

    fun start(): Boolean {
        if (modelManager.state() != LiveTranscriptionModelState.INSTALLED) {
            onStatus("Descarga Moonshine ES + detección de voz para transcripción en vivo")
            return false
        }
        return try {
            vadPending.reset()
            fallbackPending.reset()
            lastFallbackText = ""
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
            vad = createVadOrNull()
            onStatus(
                if (vad != null) "Transcripción en vivo · Moonshine ES + detección de voz"
                else "Transcripción en vivo · Moonshine ES"
            )
            true
        } catch (t: Throwable) {
            onStatus("No se pudo iniciar la transcripción en vivo: ${t.message ?: "error"}")
            false
        }
    }

    fun acceptPcm16k(pcm: ByteArray) {
        val active = recognizer ?: return
        val activeVad = vad
        if (activeVad != null) {
            vadPending.write(pcm)
            drainVadWindows(active, activeVad)
        } else {
            acceptFallback(active, pcm)
        }
    }

    fun close() {
        val active = recognizer
        if (active != null) {
            val activeVad = vad
            if (activeVad != null) {
                if (vadPending.size() > 0) {
                    val remaining = vadPending.toByteArray()
                    val padded = ByteArray(VAD_WINDOW_BYTES)
                    remaining.copyInto(padded, endIndex = remaining.size.coerceAtMost(VAD_WINDOW_BYTES))
                    activeVad.acceptWaveform(pcmToFloat(padded))
                    vadPending.reset()
                }
                runCatching { activeVad.flush() }
                drainCompletedSpeech(active, activeVad)
            } else if (fallbackPending.size() >= MIN_FINAL_BYTES) {
                decode(active, fallbackPending.toByteArray(), dedupeFallbackOverlap = true)
            }
        }

        vadPending.reset()
        fallbackPending.reset()
        lastFallbackText = ""
        runCatching { vad?.release() }
        vad = null
        runCatching { recognizer?.release() }
        recognizer = null
        onStatus("Transcripción en vivo finalizada")
    }

    private fun createVadOrNull(): Vad? = runCatching {
        val file = modelManager.vadFile()
        require(file.exists()) { "Silero VAD no está instalado" }
        Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = file.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.45f,
                    minSpeechDuration = 0.25f,
                    windowSize = VAD_WINDOW_SAMPLES,
                    maxSpeechDuration = 12.0f
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )
        )
    }.getOrNull()

    private fun drainVadWindows(active: OfflineRecognizer, activeVad: Vad) {
        while (vadPending.size() >= VAD_WINDOW_BYTES) {
            val all = vadPending.toByteArray()
            val window = all.copyOfRange(0, VAD_WINDOW_BYTES)
            vadPending.reset()
            if (all.size > VAD_WINDOW_BYTES) {
                vadPending.write(all, VAD_WINDOW_BYTES, all.size - VAD_WINDOW_BYTES)
            }
            runCatching { activeVad.acceptWaveform(pcmToFloat(window)) }
                .onFailure {
                    // VAD es una mejora, nunca un punto único de fallo: volvemos al
                    // procesamiento solapado por bloques sin detener la grabación.
                    runCatching { activeVad.release() }
                    vad = null
                    if (all.isNotEmpty()) acceptFallback(active, all)
                    onStatus("Transcripción en vivo · modo compatible con solapamiento")
                    return
                }
            drainCompletedSpeech(active, activeVad)
        }
    }

    private fun drainCompletedSpeech(active: OfflineRecognizer, activeVad: Vad) {
        while (!activeVad.empty()) {
            val segment = activeVad.front()
            if (segment.samples.isNotEmpty()) decodeSamples(active, segment.samples)
            activeVad.pop()
        }
    }

    private fun acceptFallback(active: OfflineRecognizer, pcm: ByteArray) {
        fallbackPending.write(pcm)
        while (fallbackPending.size() >= FALLBACK_CHUNK_BYTES) {
            val all = fallbackPending.toByteArray()
            val chunk = all.copyOfRange(0, FALLBACK_CHUNK_BYTES)
            fallbackPending.reset()

            // Avanzamos 5.6 s en cada bloque de 6 s: los últimos 400 ms vuelven
            // a entrar en el siguiente bloque para conservar palabras en el borde.
            val keepFrom = (FALLBACK_CHUNK_BYTES - FALLBACK_OVERLAP_BYTES).coerceAtLeast(0)
            if (all.size > keepFrom) {
                fallbackPending.write(all, keepFrom, all.size - keepFrom)
            }
            decode(active, chunk, dedupeFallbackOverlap = true)
        }
    }

    private fun decode(
        active: OfflineRecognizer,
        pcm: ByteArray,
        dedupeFallbackOverlap: Boolean = false
    ) {
        if (pcm.size < 2) return
        decodeSamples(active, pcmToFloat(pcm), dedupeFallbackOverlap)
    }

    private fun decodeSamples(
        active: OfflineRecognizer,
        samples: FloatArray,
        dedupeFallbackOverlap: Boolean = false
    ) {
        if (samples.isEmpty()) return
        val stream = active.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            active.decode(stream)
            val raw = active.getResult(stream).text.trim()
            if (raw.isBlank()) return

            val resolved = if (dedupeFallbackOverlap) {
                val withoutOverlap = removeRepeatedOverlap(lastFallbackText, raw)
                lastFallbackText = raw
                withoutOverlap
            } else {
                raw
            }
            resolved.takeIf { it.isNotBlank() }?.let(onTranscriptChunk)
        } catch (t: Throwable) {
            onStatus("Transcripción provisional interrumpida: ${t.message ?: "error"}")
        } finally {
            stream.release()
        }
    }

    private fun removeRepeatedOverlap(previous: String, current: String): String {
        if (previous.isBlank() || current.isBlank()) return current
        val previousWords = previous.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val currentWords = current.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (previousWords.size < 2 || currentWords.size < 2) return current

        val maxWords = minOf(MAX_DEDUPE_WORDS, previousWords.size, currentWords.size)
        for (count in maxWords downTo MIN_DEDUPE_WORDS) {
            val suffix = previousWords.takeLast(count).map(::normalizeWord)
            val prefix = currentWords.take(count).map(::normalizeWord)
            if (suffix.any { it.isBlank() } || suffix != prefix) continue
            val matchedChars = prefix.sumOf { it.length }
            if (count >= 3 || matchedChars >= MIN_DEDUPE_CHARS) {
                return currentWords.drop(count).joinToString(" ")
            }
        }
        return current
    }

    private fun normalizeWord(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun pcmToFloat(pcm: ByteArray): FloatArray {
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
        return samples
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val VAD_WINDOW_SAMPLES = 512
        private const val VAD_WINDOW_BYTES = VAD_WINDOW_SAMPLES * 2
        private const val FALLBACK_CHUNK_SECONDS = 6
        private const val FALLBACK_CHUNK_BYTES = SAMPLE_RATE * 2 * FALLBACK_CHUNK_SECONDS
        private const val FALLBACK_OVERLAP_MS = 400
        private const val FALLBACK_OVERLAP_BYTES = SAMPLE_RATE * 2 * FALLBACK_OVERLAP_MS / 1_000
        private const val MIN_FINAL_BYTES = SAMPLE_RATE * 2
        private const val MAX_DEDUPE_WORDS = 10
        private const val MIN_DEDUPE_WORDS = 2
        private const val MIN_DEDUPE_CHARS = 14
    }
}
