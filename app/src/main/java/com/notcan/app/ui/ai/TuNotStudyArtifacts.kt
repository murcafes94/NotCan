package com.notcan.app.ui.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class StudyFlashcard(
    val question: String,
    val answer: String,
    val sourceRef: String? = null
)

internal data class ParsedFlashcardArtifact(
    val title: String,
    val cards: List<StudyFlashcard>
)

internal object StudyFlashcardArtifactParser {
    const val START_MARKER = "<<<NOTCAN_FLASHCARDS>>>"
    const val END_MARKER = "<<<END_NOTCAN_FLASHCARDS>>>"
    private const val MAX_CARDS = 24

    fun parse(value: String): ParsedFlashcardArtifact? {
        val start = value.indexOf(START_MARKER)
        val end = value.indexOf(END_MARKER)
        if (start < 0 || end <= start) return null
        val jsonText = value.substring(start + START_MARKER.length, end).trim()
        return runCatching {
            val root = JSONObject(jsonText)
            val title = compact(root.optString("title").ifBlank { "Tarjetas de estudio" }, 70)
            val array = root.optJSONArray("cards") ?: return@runCatching null
            val cards = buildList {
                for (i in 0 until minOf(array.length(), MAX_CARDS)) {
                    val item = array.optJSONObject(i) ?: continue
                    val question = compact(item.optString("question"), 220)
                    val answer = compact(item.optString("answer"), 520)
                    if (question.isBlank() || answer.isBlank()) continue
                    add(
                        StudyFlashcard(
                            question = question,
                            answer = answer,
                            sourceRef = compact(item.optString("source_ref"), 50).takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            if (cards.isEmpty()) null else ParsedFlashcardArtifact(title, cards)
        }.getOrNull()
    }

    fun stripArtifact(value: String): String {
        val start = value.indexOf(START_MARKER)
        val end = value.indexOf(END_MARKER)
        if (start < 0 || end <= start) return value
        return (value.substring(0, start) + value.substring(end + END_MARKER.length))
            .trim()
            .ifBlank { "Preparé las tarjetas. Puedes abrirlas y comenzar el repaso." }
    }

    private fun compact(value: String, maxChars: Int): String {
        val clean = value
            .replace(Regex("[*_#`]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.length <= maxChars) return clean
        val head = clean.take(maxChars)
        val cut = head.lastIndexOf(' ').takeIf { it >= maxChars / 2 } ?: maxChars
        return clean.take(cut).trimEnd(' ', ',', ';', ':', '-') + "…"
    }
}

internal data class StoredTuNotMessage(
    val role: String,
    val rawContent: String
)

/**
 * Persistencia ligera del historial visible de TuNot por materia/clase.
 * Conserva el contenido crudo para poder reconstruir mapas y tarjetas al volver al chat.
 */
internal class TuNotChatStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(scope: String): List<StoredTuNotMessage> = runCatching {
        val raw = preferences.getString(key(scope), null) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role")
                val content = item.optString("content")
                if (role.isBlank() || content.isBlank()) continue
                add(StoredTuNotMessage(role, content))
            }
        }
    }.getOrDefault(emptyList())

    fun save(scope: String, messages: List<StoredTuNotMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            array.put(JSONObject().put("role", message.role).put("content", message.rawContent))
        }
        preferences.edit().putString(key(scope), array.toString()).apply()
    }

    fun clear(scope: String) {
        preferences.edit().remove(key(scope)).apply()
    }

    private fun key(scope: String): String = "history_${scope.hashCode()}"

    companion object {
        private const val PREFS_NAME = "tunot_chat_history"
        private const val MAX_MESSAGES = 80
    }
}
