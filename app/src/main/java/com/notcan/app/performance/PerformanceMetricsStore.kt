package com.notcan.app.performance

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import kotlin.math.max

/**
 * Lightweight local-only performance telemetry for NotCan.
 * No metric leaves the device and no personally identifiable content is stored.
 */
class PerformanceMetricsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Snapshot(
        val startupMs: Long,
        val subjectOpenMs: Long,
        val gemmaLoadMs: Long,
        val gemmaBackend: String,
        val gemmaFirstTokenMs: Long,
        val gemmaTotalMs: Long,
        val gemmaOutputChars: Int,
        val gemmaPromptChars: Int,
        val transcriptionProvider: String,
        val transcriptionMs: Long,
        val transcriptionAudioMs: Long,
        val whisperConvertMs: Long,
        val whisperModelLoadMs: Long,
        val whisperInferenceMs: Long,
        val updatedAtEpochMs: Long
    ) {
        val estimatedTokensPerSecond: Double
            get() {
                if (gemmaOutputChars <= 0 || gemmaTotalMs <= 0L) return 0.0
                val generationWindowMs = max(1L, gemmaTotalMs - gemmaFirstTokenMs.coerceAtLeast(0L))
                val estimatedTokens = gemmaOutputChars / 4.0
                return estimatedTokens / (generationWindowMs / 1_000.0)
            }

        val transcriptionRealtimeFactor: Double
            get() = if (transcriptionAudioMs > 0L) transcriptionMs.toDouble() / transcriptionAudioMs.toDouble() else 0.0
    }

    data class RuntimeSnapshot(
        val pssMb: Double,
        val javaHeapMb: Double,
        val nativeHeapMb: Double,
        val thermalStatus: Int,
        val thermalLabel: String
    ) {
        val thermallyConstrained: Boolean
            get() = thermalStatus >= THERMAL_SEVERE
    }

    fun snapshot(): Snapshot = Snapshot(
        startupMs = prefs.getLong(KEY_STARTUP_MS, 0L),
        subjectOpenMs = prefs.getLong(KEY_SUBJECT_OPEN_MS, 0L),
        gemmaLoadMs = prefs.getLong(KEY_GEMMA_LOAD_MS, 0L),
        gemmaBackend = prefs.getString(KEY_GEMMA_BACKEND, "").orEmpty(),
        gemmaFirstTokenMs = prefs.getLong(KEY_GEMMA_FIRST_TOKEN_MS, 0L),
        gemmaTotalMs = prefs.getLong(KEY_GEMMA_TOTAL_MS, 0L),
        gemmaOutputChars = prefs.getInt(KEY_GEMMA_OUTPUT_CHARS, 0),
        gemmaPromptChars = prefs.getInt(KEY_GEMMA_PROMPT_CHARS, 0),
        transcriptionProvider = prefs.getString(KEY_TRANSCRIPTION_PROVIDER, "").orEmpty(),
        transcriptionMs = prefs.getLong(KEY_TRANSCRIPTION_MS, 0L),
        transcriptionAudioMs = prefs.getLong(KEY_TRANSCRIPTION_AUDIO_MS, 0L),
        whisperConvertMs = prefs.getLong(KEY_WHISPER_CONVERT_MS, 0L),
        whisperModelLoadMs = prefs.getLong(KEY_WHISPER_MODEL_LOAD_MS, 0L),
        whisperInferenceMs = prefs.getLong(KEY_WHISPER_INFERENCE_MS, 0L),
        updatedAtEpochMs = prefs.getLong(KEY_UPDATED_AT, 0L)
    )

    fun runtimeSnapshot(): RuntimeSnapshot {
        val runtime = Runtime.getRuntime()
        val pssKb = Debug.getPss().coerceAtLeast(0)
        val javaHeap = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val nativeHeap = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                appContext.getSystemService(PowerManager::class.java)?.currentThermalStatus
                    ?: PowerManager.THERMAL_STATUS_NONE
            }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        return RuntimeSnapshot(
            pssMb = pssKb / 1024.0,
            javaHeapMb = javaHeap / (1024.0 * 1024.0),
            nativeHeapMb = nativeHeap / (1024.0 * 1024.0),
            thermalStatus = thermal,
            thermalLabel = thermalLabel(thermal)
        )
    }

    fun recordStartup(ms: Long) = edit {
        putLong(KEY_STARTUP_MS, ms.coerceAtLeast(0L))
    }

    fun recordSubjectOpen(ms: Long) = edit {
        putLong(KEY_SUBJECT_OPEN_MS, ms.coerceAtLeast(0L))
    }

    fun recordGemmaLoad(ms: Long, backend: String) = edit {
        putLong(KEY_GEMMA_LOAD_MS, ms.coerceAtLeast(0L))
        putString(KEY_GEMMA_BACKEND, backend)
    }

    fun recordGemmaGeneration(
        backend: String,
        firstTokenMs: Long,
        totalMs: Long,
        outputChars: Int,
        promptChars: Int
    ) = edit {
        putString(KEY_GEMMA_BACKEND, backend)
        putLong(KEY_GEMMA_FIRST_TOKEN_MS, firstTokenMs.coerceAtLeast(0L))
        putLong(KEY_GEMMA_TOTAL_MS, totalMs.coerceAtLeast(0L))
        putInt(KEY_GEMMA_OUTPUT_CHARS, outputChars.coerceAtLeast(0))
        putInt(KEY_GEMMA_PROMPT_CHARS, promptChars.coerceAtLeast(0))
    }

    fun recordTranscription(provider: String, processingMs: Long, audioMs: Long) = edit {
        putString(KEY_TRANSCRIPTION_PROVIDER, provider)
        putLong(KEY_TRANSCRIPTION_MS, processingMs.coerceAtLeast(0L))
        putLong(KEY_TRANSCRIPTION_AUDIO_MS, audioMs.coerceAtLeast(0L))
    }

    fun recordLocalWhisperStages(convertMs: Long, modelLoadMs: Long, inferenceMs: Long) = edit {
        putLong(KEY_WHISPER_CONVERT_MS, convertMs.coerceAtLeast(0L))
        putLong(KEY_WHISPER_MODEL_LOAD_MS, modelLoadMs.coerceAtLeast(0L))
        putLong(KEY_WHISPER_INFERENCE_MS, inferenceMs.coerceAtLeast(0L))
    }

    fun clearMeasurements() {
        prefs.edit().clear().apply()
    }

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply {
            block()
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }.apply()
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Ligero"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severo"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado térmico"
        else -> "Desconocido"
    }

    companion object {
        private const val PREFS = "notcan_performance_metrics"
        private const val KEY_STARTUP_MS = "startup_ms"
        private const val KEY_SUBJECT_OPEN_MS = "subject_open_ms"
        private const val KEY_GEMMA_LOAD_MS = "gemma_load_ms"
        private const val KEY_GEMMA_BACKEND = "gemma_backend"
        private const val KEY_GEMMA_FIRST_TOKEN_MS = "gemma_first_token_ms"
        private const val KEY_GEMMA_TOTAL_MS = "gemma_total_ms"
        private const val KEY_GEMMA_OUTPUT_CHARS = "gemma_output_chars"
        private const val KEY_GEMMA_PROMPT_CHARS = "gemma_prompt_chars"
        private const val KEY_TRANSCRIPTION_PROVIDER = "transcription_provider"
        private const val KEY_TRANSCRIPTION_MS = "transcription_ms"
        private const val KEY_TRANSCRIPTION_AUDIO_MS = "transcription_audio_ms"
        private const val KEY_WHISPER_CONVERT_MS = "whisper_convert_ms"
        private const val KEY_WHISPER_MODEL_LOAD_MS = "whisper_model_load_ms"
        private const val KEY_WHISPER_INFERENCE_MS = "whisper_inference_ms"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val THERMAL_SEVERE = 3
    }
}
