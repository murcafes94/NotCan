package com.notcan.app.localai

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import java.io.File

class LocalWhisperEngine(private val context: Context) {
    private val modelManager = WhisperModelManager(context)

    suspend fun transcribeM4a(audio: File): String {
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
                result.text.trim().ifBlank { "No se detectó voz suficiente para generar una transcripción." }
            } finally {
                Whisper.releaseModel(model)
            }
        } finally {
            wav.delete()
        }
    }
}
