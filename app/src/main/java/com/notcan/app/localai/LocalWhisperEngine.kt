package com.notcan.app.localai

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import java.io.File

data class WhisperSegmentResult(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class WhisperTranscriptionResult(
    val text: String,
    val segments: List<WhisperSegmentResult>
)

class LocalWhisperEngine(private val context: Context) {
    private val modelManager = WhisperModelManager(context)

    /**
     * Compatibilidad con los consumidores existentes: devuelve solamente el texto.
     * Para conservar los tiempos por segmento usa [transcribeM4aDetailed].
     */
    suspend fun transcribeM4a(audio: File): String = transcribeM4aDetailed(audio).text

    suspend fun transcribeM4aDetailed(audio: File): WhisperTranscriptionResult {
        val modelFile = modelManager.modelFile()
        require(modelFile.exists() && modelFile.length() >= WhisperModelSpec.MIN_VALID_BYTES) {
            "Primero descarga ${WhisperModelSpec.DISPLAY_NAME}."
        }
        require(audio.exists()) { "El audio local ya no existe." }

        val tempDir = File(context.cacheDir, "whisper").apply { mkdirs() }
        val wav = File(tempDir, "${audio.nameWithoutExtension}_${System.currentTimeMillis()}.wav")
        try {
            AudioToWavConverter.convert(audio, wav)
            val model = Whisper.loadModel(context, modelFile.absolutePath)
            return try {
                val result = Whisper.transcribe(
                    model,
                    wav.absolutePath,
                    WhisperConfig(language = "es")
                )
                val text = result.text.trim().ifBlank {
                    "No se detectó voz suficiente para generar una transcripción."
                }
                val segments = result.segments.mapNotNull { segment ->
                    val segmentText = segment.text.trim()
                    segmentText.takeIf { it.isNotBlank() }?.let {
                        WhisperSegmentResult(
                            startMs = segment.startMs.coerceAtLeast(0L),
                            endMs = segment.endMs.coerceAtLeast(segment.startMs),
                            text = it
                        )
                    }
                }
                WhisperTranscriptionResult(text = text, segments = segments)
            } finally {
                Whisper.releaseModel(model)
            }
        } finally {
            wav.delete()
        }
    }
}
