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

    private fun compact(value: String, maxChars: Int): String = compactStudyText(value, maxChars)
}

internal enum class StudyQuizQuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER
}

internal data class StudyQuizQuestion(
    val id: String,
    val type: StudyQuizQuestionType,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String? = null,
    val sourceRef: String? = null
)

internal data class ParsedQuizArtifact(
    val title: String,
    val questions: List<StudyQuizQuestion>
)

/**
 * Artefacto de cuestionario que NotCan puede corregir localmente.
 * Las preguntas objetivas no necesitan otra llamada a Mistral una vez generado el cuestionario.
 */
internal object StudyQuizArtifactParser {
    const val START_MARKER = "<<<NOTCAN_QUIZ>>>"
    const val END_MARKER = "<<<END_NOTCAN_QUIZ>>>"
    private const val MAX_QUESTIONS = 30

    fun parse(value: String): ParsedQuizArtifact? {
        val start = value.indexOf(START_MARKER)
        val end = value.indexOf(END_MARKER)
        if (start < 0 || end <= start) return null
        val jsonText = value.substring(start + START_MARKER.length, end).trim()
        return runCatching {
            val root = JSONObject(jsonText)
            val title = compactStudyText(root.optString("title").ifBlank { "Cuestionario" }, 80)
            val array = root.optJSONArray("questions") ?: return@runCatching null
            val questions = buildList {
                for (i in 0 until minOf(array.length(), MAX_QUESTIONS)) {
                    val item = array.optJSONObject(i) ?: continue
                    val type = when (item.optString("type").lowercase()) {
                        "true_false", "verdadero_falso", "boolean" -> StudyQuizQuestionType.TRUE_FALSE
                        "short_answer", "open", "pregunta_abierta", "development" -> StudyQuizQuestionType.SHORT_ANSWER
                        else -> StudyQuizQuestionType.MULTIPLE_CHOICE
                    }
                    val question = compactStudyText(item.optString("question"), 320)
                    val correct = compactStudyText(
                        item.optString("correct_answer").ifBlank { item.optString("answer") },
                        520
                    )
                    if (question.isBlank() || correct.isBlank()) continue
                    val options = when (type) {
                        StudyQuizQuestionType.TRUE_FALSE -> listOf("Verdadero", "Falso")
                        StudyQuizQuestionType.SHORT_ANSWER -> emptyList()
                        StudyQuizQuestionType.MULTIPLE_CHOICE -> buildList {
                            val raw = item.optJSONArray("options")
                            if (raw != null) {
                                for (j in 0 until minOf(raw.length(), 6)) {
                                    compactStudyText(raw.optString(j), 180)
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::add)
                                }
                            }
                        }.distinct()
                    }
                    if (type == StudyQuizQuestionType.MULTIPLE_CHOICE && (options.size < 2 || correct !in options)) continue
                    add(
                        StudyQuizQuestion(
                            id = item.optString("id").ifBlank { "q${i + 1}" },
                            type = type,
                            question = question,
                            options = options,
                            correctAnswer = correct,
                            explanation = compactStudyText(item.optString("explanation"), 620).takeIf { it.isNotBlank() },
                            sourceRef = compactStudyText(item.optString("source_ref"), 60).takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            if (questions.isEmpty()) null else ParsedQuizArtifact(title, questions)
        }.getOrNull()
    }

    fun stripArtifact(value: String): String {
        val start = value.indexOf(START_MARKER)
        val end = value.indexOf(END_MARKER)
        if (start < 0 || end <= start) return value
        return (value.substring(0, start) + value.substring(end + END_MARKER.length))
            .trim()
            .ifBlank { "Preparé el cuestionario. Puedes responderlo directamente en NotCan." }
    }
}

internal data class StoredTuNotMessage(
    val role: String,
    val rawContent: String
)

/**
 * Persistencia ligera del historial visible de TuNot por materia/clase.
 * Conserva el contenido crudo para reconstruir mapas, tarjetas y cuestionarios al volver al chat.
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

private fun compactStudyText(value: String, maxChars: Int): String {
    val clean = value
        .replace(Regex("[*_#`]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (clean.length <= maxChars) return clean
    val head = clean.take(maxChars)
    val cut = head.lastIndexOf(' ').takeIf { it >= maxChars / 2 } ?: maxChars
    return clean.take(cut).trimEnd(' ', ',', ';', ':', '-') + "…"
}
