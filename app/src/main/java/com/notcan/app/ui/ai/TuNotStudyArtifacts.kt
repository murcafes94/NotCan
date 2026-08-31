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

private data class StudyArtifactSlice(
    val json: String,
    val start: Int,
    val endExclusive: Int
)

internal object StudyFlashcardArtifactParser {
    const val START_MARKER = "<<<NOTCAN_FLASHCARDS>>>"
    const val END_MARKER = "<<<END_NOTCAN_FLASHCARDS>>>"
    private val START_MARKERS = listOf(START_MARKER, "<<NOTCAN_FLASHCARDS>>", "<NOTCAN_FLASHCARDS>")
    private val END_MARKERS = listOf(END_MARKER, "<<END_NOTCAN_FLASHCARDS>>", "<END_NOTCAN_FLASHCARDS>")
    private const val MAX_CARDS = 24

    fun parse(value: String): ParsedFlashcardArtifact? {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("cards", "flashcards", "tarjetas") != null }
            ?: return null
        return runCatching {
            val root = JSONObject(artifact.json)
            val title = compact(root.flexString("title").ifBlank { "Tarjetas de estudio" }, 70)
            val array = root.flexArray("cards", "flashcards", "tarjetas") ?: return@runCatching null
            val cards = buildList {
                for (i in 0 until minOf(array.length(), MAX_CARDS)) {
                    val item = array.optJSONObject(i) ?: continue
                    val question = compact(item.flexString("question", "pregunta"), 220)
                    val answer = compact(item.flexString("answer", "respuesta"), 520)
                    if (question.isBlank() || answer.isBlank()) continue
                    add(
                        StudyFlashcard(
                            question = question,
                            answer = answer,
                            sourceRef = compact(
                                item.flexString("source_ref", "sourceRef", "sourceref", "source"),
                                50
                            ).takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            if (cards.isEmpty()) null else ParsedFlashcardArtifact(title, cards)
        }.getOrNull()
    }

    fun stripArtifact(value: String): String {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("cards", "flashcards", "tarjetas") != null }
            ?: return value
        return stripStudyArtifact(value, artifact)
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
    private val START_MARKERS = listOf(START_MARKER, "<<NOTCAN_QUIZ>>", "<NOTCAN_QUIZ>")
    private val END_MARKERS = listOf(END_MARKER, "<<END_NOTCAN_QUIZ>>", "<END_NOTCAN_QUIZ>")
    private const val MAX_QUESTIONS = 30

    fun parse(value: String): ParsedQuizArtifact? {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("questions", "preguntas") != null }
            ?: return null
        return runCatching {
            val root = JSONObject(artifact.json)
            val title = compactStudyText(root.flexString("title").ifBlank { "Cuestionario" }, 80)
            val array = root.flexArray("questions", "preguntas") ?: return@runCatching null
            val questions = buildList {
                for (i in 0 until minOf(array.length(), MAX_QUESTIONS)) {
                    val item = array.optJSONObject(i) ?: continue
                    val type = when (item.flexString("type", "tipo").lowercase()) {
                        "true_false", "verdadero_falso", "boolean", "truefalse" -> StudyQuizQuestionType.TRUE_FALSE
                        "short_answer", "open", "pregunta_abierta", "development", "shortanswer" -> StudyQuizQuestionType.SHORT_ANSWER
                        else -> StudyQuizQuestionType.MULTIPLE_CHOICE
                    }
                    val question = compactStudyText(item.flexString("question", "pregunta"), 320)
                    val correct = compactStudyText(
                        item.flexString("correct_answer", "correctAnswer", "correctanswer", "answer", "respuesta"),
                        520
                    )
                    if (question.isBlank() || correct.isBlank()) continue
                    val options = when (type) {
                        StudyQuizQuestionType.TRUE_FALSE -> listOf("Verdadero", "Falso")
                        StudyQuizQuestionType.SHORT_ANSWER -> emptyList()
                        StudyQuizQuestionType.MULTIPLE_CHOICE -> buildList {
                            val raw = item.flexArray("options", "opciones")
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
                            id = item.flexString("id").ifBlank { "q${i + 1}" },
                            type = type,
                            question = question,
                            options = options,
                            correctAnswer = correct,
                            explanation = compactStudyText(
                                item.flexString("explanation", "explicacion", "explicación"),
                                620
                            ).takeIf { it.isNotBlank() },
                            sourceRef = compactStudyText(
                                item.flexString("source_ref", "sourceRef", "sourceref", "source"),
                                60
                            ).takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            if (questions.isEmpty()) null else ParsedQuizArtifact(title, questions)
        }.getOrNull()
    }

    fun stripArtifact(value: String): String {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("questions", "preguntas") != null }
            ?: return value
        return stripStudyArtifact(value, artifact)
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

private fun extractStudyArtifact(
    value: String,
    startMarkers: List<String>,
    endMarkers: List<String>
): StudyArtifactSlice? {
    val markerMatch = startMarkers
        .mapNotNull { marker -> value.indexOf(marker).takeIf { it >= 0 }?.let { Triple(it, marker.length, marker) } }
        .minByOrNull { it.first }
        ?: return null
    val markerStart = markerMatch.first
    val contentStart = markerStart + markerMatch.second
    val explicitEnd = endMarkers
        .mapNotNull { marker -> value.indexOf(marker, contentStart).takeIf { it >= 0 }?.let { it to marker.length } }
        .minByOrNull { it.first }
    val scanLimit = explicitEnd?.first ?: value.length
    val jsonStart = value.indexOf('{', contentStart).takeIf { it >= contentStart && it < scanLimit } ?: return null
    val jsonEnd = balancedJsonObjectEnd(value, jsonStart, scanLimit)
        ?: explicitEnd?.first
        ?: return null
    val json = value.substring(jsonStart, jsonEnd).trim()
    if (json.isBlank()) return null
    val artifactEnd = explicitEnd
        ?.takeIf { it.first >= jsonEnd }
        ?.let { it.first + it.second }
        ?: jsonEnd
    return StudyArtifactSlice(json = json, start = markerStart, endExclusive = artifactEnd)
}

private fun extractBareStudyArtifact(
    value: String,
    signature: (JSONObject) -> Boolean
): StudyArtifactSlice? {
    val jsonStart = value.indexOf('{').takeIf { it >= 0 } ?: return null
    val jsonEnd = balancedJsonObjectEnd(value, jsonStart, value.length) ?: return null
    val json = value.substring(jsonStart, jsonEnd).trim()
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (!signature(root)) return null
    return StudyArtifactSlice(json = json, start = jsonStart, endExclusive = jsonEnd)
}

private fun balancedJsonObjectEnd(value: String, start: Int, limit: Int): Int? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until limit) {
        val char = value[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return index + 1
            }
        }
    }
    return null
}

private fun stripStudyArtifact(value: String, artifact: StudyArtifactSlice): String =
    (value.substring(0, artifact.start) + value.substring(artifact.endExclusive))
        .replace("```json", "")
        .replace("```", "")
        .trim()

private fun JSONObject.flexString(vararg aliases: String): String {
    aliases.forEach { alias -> if (has(alias)) return optString(alias) }
    val normalized = aliases.mapTo(hashSetOf()) { normalizeStudyKey(it) }
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        if (normalizeStudyKey(key) in normalized) return optString(key)
    }
    return ""
}

private fun JSONObject.flexArray(vararg aliases: String): JSONArray? {
    aliases.forEach { alias -> optJSONArray(alias)?.let { return it } }
    val normalized = aliases.mapTo(hashSetOf()) { normalizeStudyKey(it) }
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        if (normalizeStudyKey(key) in normalized) optJSONArray(key)?.let { return it }
    }
    return null
}

private fun normalizeStudyKey(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }

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
