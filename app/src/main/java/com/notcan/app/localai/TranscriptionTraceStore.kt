package com.notcan.app.localai

import org.json.JSONObject
import java.io.File

/**
 * Keeps a private audit trail next to each recording so NotCan can compare what the
 * recognizer heard with the user-facing transcript without polluting Room or TuNot context.
 */
object TranscriptionTraceStore {
    fun writeRaw(audio: File, stage: String, text: String): File? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        return runCatching {
            File("${audio.absolutePath}.$stage.raw.txt").also { it.writeText(clean) }
        }.getOrNull()
    }

    fun writeMetadata(
        audio: File,
        provider: String,
        model: String,
        finalStatus: String,
        academicTermCount: Int,
        rawFile: File?,
        postCorrectionApplied: Boolean
    ) {
        runCatching {
            val json = JSONObject()
                .put("provider", provider)
                .put("model", model)
                .put("finalStatus", finalStatus)
                .put("academicTermCount", academicTermCount)
                .put("recognitionContextUsed", academicTermCount > 0)
                .put("postCorrectionApplied", postCorrectionApplied)
                .put("rawSidecar", rawFile?.name ?: JSONObject.NULL)
                .put("updatedAtEpochMs", System.currentTimeMillis())
            File("${audio.absolutePath}.transcription.json").writeText(json.toString(2))
        }
    }

    fun deleteForAudio(audio: File) {
        val prefix = audio.absolutePath
        listOf(
            "$prefix.moonshine.raw.txt",
            "$prefix.whisper.local.raw.txt",
            "$prefix.whisper.groq.raw.txt",
            "$prefix.transcription.json",
            "$prefix.markers.csv"
        ).forEach { path -> runCatching { File(path).delete() } }
    }
}
