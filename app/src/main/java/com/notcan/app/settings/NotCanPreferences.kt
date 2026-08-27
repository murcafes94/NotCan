package com.notcan.app.settings

import android.content.Context

class NotCanPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var assistantName: String
        get() = prefs.getString(KEY_ASSISTANT_NAME, "Asistente NotCan") ?: "Asistente NotCan"
        set(value) = prefs.edit().putString(KEY_ASSISTANT_NAME, value.trim().ifBlank { "Asistente NotCan" }).apply()

    var aiInstructions: String
        get() = prefs.getString(
            KEY_AI_INSTRUCTIONS,
            "Responde en español claro, ordenado y académico. Prioriza siempre el material de la clase y señala cuando hagas una inferencia."
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_AI_INSTRUCTIONS, value.trim()).apply()

    var aiDetail: String
        get() = prefs.getString(KEY_AI_DETAIL, "Equilibrado") ?: "Equilibrado"
        set(value) = prefs.edit().putString(KEY_AI_DETAIL, value).apply()

    var autoFocusOnRecording: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FOCUS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FOCUS, value).apply()

    var autoTranscribeAfterRecording: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRANSCRIBE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRANSCRIBE, value).apply()

    var autoDetectAcademicCues: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CUES, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CUES, value).apply()

    companion object {
        private const val PREFS_NAME = "notcan_preferences"
        private const val KEY_ASSISTANT_NAME = "assistant_name"
        private const val KEY_AI_INSTRUCTIONS = "ai_instructions"
        private const val KEY_AI_DETAIL = "ai_detail"
        private const val KEY_AUTO_FOCUS = "auto_focus_recording"
        private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe_recording"
        private const val KEY_AUTO_CUES = "auto_detect_academic_cues"
    }
}
