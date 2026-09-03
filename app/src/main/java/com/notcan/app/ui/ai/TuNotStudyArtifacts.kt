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
        if (artifact != null) {
            val strict = runCatching {
                val root = JSONObject(repairStudyJson(artifact.json))
                parseFlashcardRoot(root)
            }.getOrNull()
            if (strict != null) return strict
        }
        return parsePartialFlashcards(value)
    }

    fun stripArtifact(value: String): String {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("cards", "flashcards", "tarjetas") != null }
        if (artifact != null) {
            return stripStudyArtifact(value, artifact)
                .ifBlank { "Preparé las tarjetas. Puedes abrirlas y comenzar el repaso." }
        }
        return if (parsePartialFlashcards(value) != null) {
            "Preparé las tarjetas y recuperé los elementos completos del recurso."
        } else value
    }

    private fun parseFlashcardRoot(root: JSONObject): ParsedFlashcardArtifact? {
        val title = compactStudyText(root.flexString("title").ifBlank { "Tarjetas de estudio" }, 70)
        val array = root.flexArray("cards", "flashcards", "tarjetas") ?: return null
        val cards = buildList {
            for (i in 0 until minOf(array.length(), MAX_CARDS)) {
                array.optJSONObject(i)?.let { parseCard(it)?.let(::add) }
            }
        }
        return if (cards.isEmpty()) null else ParsedFlashcardArtifact(title, cards)
    }

    private fun parsePartialFlashcards(value: String): ParsedFlashcardArtifact? {
        val raw = extractPartialStudyBody(value, START_MARKERS) ?: return null
        val objects = extractCompleteObjectsFromNamedArray(raw, listOf("cards", "flashcards", "tarjetas"), MAX_CARDS)
        val cards = objects.mapNotNull(::parseCard)
        if (cards.isEmpty()) return null
        val title = compactStudyText(looseJsonString(raw, "title").ifBlank { "Tarjetas de estudio" }, 70)
        return ParsedFlashcardArtifact(title, cards)
    }

    private fun parseCard(item: JSONObject): StudyFlashcard? {
        val question = compactStudyText(item.flexString("question", "pregunta"), 220)
        val answer = compactStudyText(item.flexString("answer", "respuesta"), 520)
        if (question.isBlank() || answer.isBlank()) return null
        return StudyFlashcard(
            question = question,
            answer = answer,
            sourceRef = compactStudyText(
                item.flexString("source_ref", "sourceRef", "sourceref", "source"),
                50
            ).takeIf { it.isNotBlank() }
        )
    }
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

internal object StudyQuizArtifactParser {
    const val START_MARKER = "<<<NOTCAN_QUIZ>>>"
    const val END_MARKER = "<<<END_NOTCAN_QUIZ>>>"
    private val START_MARKERS = listOf(START_MARKER, "<<NOTCAN_QUIZ>>", "<NOTCAN_QUIZ>")
    private val END_MARKERS = listOf(END_MARKER, "<<END_NOTCAN_QUIZ>>", "<END_NOTCAN_QUIZ>")
    private const val MAX_QUESTIONS = 30

    fun parse(value: String): ParsedQuizArtifact? {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("questions", "preguntas") != null }
        if (artifact != null) {
            val strict = runCatching {
                val root = JSONObject(repairStudyJson(artifact.json))
                parseQuizRoot(root)
            }.getOrNull()
            if (strict != null) return strict
        }
        return parsePartialQuiz(value)
    }

    fun stripArtifact(value: String): String {
        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("questions", "preguntas") != null }
        if (artifact != null) {
            return stripStudyArtifact(value, artifact)
                .ifBlank { "Preparé el cuestionario. Puedes responderlo directamente en NotCan." }
        }
        return if (parsePartialQuiz(value) != null) {
            "Preparé el cuestionario y recuperé las preguntas completas del recurso."
        } else value
    }

    private fun parseQuizRoot(root: JSONObject): ParsedQuizArtifact? {
        val title = compactStudyText(root.flexString("title").ifBlank { "Cuestionario" }, 80)
        val array = root.flexArray("questions", "preguntas") ?: return null
        val questions = buildList {
            for (i in 0 until minOf(array.length(), MAX_QUESTIONS)) {
                array.optJSONObject(i)?.let { parseQuizItem(it, i)?.let(::add) }
            }
        }
        return if (questions.isEmpty()) null else ParsedQuizArtifact(title, questions)
    }

    private fun parsePartialQuiz(value: String): ParsedQuizArtifact? {
        val raw = extractPartialStudyBody(value, START_MARKERS) ?: return null
        val objects = extractCompleteObjectsFromNamedArray(raw, listOf("questions", "preguntas"), MAX_QUESTIONS)
        val questions = objects.mapIndexedNotNull { index, item -> parseQuizItem(item, index) }
        if (questions.isEmpty()) return null
        val title = compactStudyText(looseJsonString(raw, "title").ifBlank { "Cuestionario" }, 80)
        return ParsedQuizArtifact(title, questions)
    }

    private fun parseQuizItem(item: JSONObject, index: Int): StudyQuizQuestion? {
        val type = when (item.flexString("type", "tipo").lowercase()) {
            "true_false", "verdadero_falso", "boolean", "truefalse" -> StudyQuizQuestionType.TRUE_FALSE
            "short_answer", "open", "pregunta_abierta", "development", "shortanswer" -> StudyQuizQuestionType.SHORT_ANSWER
            else -> StudyQuizQuestionType.MULTIPLE_CHOICE
        }
        val question = compactStudyText(item.flexString("question", "pregunta"), 320)
        val rawCorrect = compactStudyText(
            item.flexString("correct_answer", "correctAnswer", "correctanswer", "answer", "respuesta"),
            520
        )
        if (question.isBlank() || rawCorrect.isBlank()) return null

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
        val correct = resolveCorrectAnswer(rawCorrect, type, options)
        if (type == StudyQuizQuestionType.MULTIPLE_CHOICE && (options.size < 2 || correct !in options)) return null

        return StudyQuizQuestion(
            id = item.flexString("id").ifBlank { "q${index + 1}" },
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
    }

    private fun resolveCorrectAnswer(
        raw: String,
        type: StudyQuizQuestionType,
        options: List<String>
    ): String {
        if (type == StudyQuizQuestionType.TRUE_FALSE) {
            return when (normalizeStudyKey(raw)) {
                "true", "verdadero", "v" -> "Verdadero"
                "false", "falso", "f" -> "Falso"
                else -> raw
            }
        }
        if (type != StudyQuizQuestionType.MULTIPLE_CHOICE) return raw
        options.firstOrNull { it == raw }?.let { return it }
        options.firstOrNull { normalizeStudyKey(it) == normalizeStudyKey(raw) }?.let { return it }
        val token = raw.trim().trim('.', ')', ':').uppercase()
        if (token.length == 1 && token[0] in 'A'..'F') {
            val index = token[0] - 'A'
            options.getOrNull(index)?.let { return it }
        }
        token.toIntOrNull()?.let { number -> options.getOrNull(number - 1)?.let { return it } }
        return raw
    }
}

internal data class StoredTuNotMessage(
    val role: String,
    val rawContent: String
)

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

private fun repairStudyJson(value: String): String {
    val normalized = value.replace('“', '"').replace('”', '"')
    val out = StringBuilder(normalized.length + 32)
    var inString = false
    var escaped = false
    normalized.forEach { ch ->
        if (inString) {
            when {
                escaped -> { out.append(ch); escaped = false }
                ch == '\\' -> { out.append(ch); escaped = true }
                ch == '"' -> { out.append(ch); inString = false }
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        } else {
            if (ch == '"') inString = true
            out.append(ch)
        }
    }
    return out.toString().replace(Regex(",\\s*([}\\]])"), "$1")
}

private fun extractStudyArtifact(
    value: String,
    startMarkers: List<String>,
    endMarkers: List<String>
): StudyArtifactSlice? {
    val markerMatch = startMarkers
        .mapNotNull { marker -> value.indexOf(marker).takeIf { it >= 0 }?.let { it to marker.length } }
        .minByOrNull { it.first }
        ?: return null
    val markerStart = markerMatch.first
    val contentStart = markerStart + markerMatch.second
    val explicitEnd = endMarkers
        .mapNotNull { marker -> value.indexOf(marker, contentStart).takeIf { it >= 0 }?.let { it to marker.length } }
        .minByOrNull { it.first }
    val scanLimit = explicitEnd?.first ?: value.length
    val jsonStart = value.indexOf('{', contentStart).takeIf { it >= contentStart && it < scanLimit } ?: return null
    val balancedEnd = balancedJsonObjectEnd(value, jsonStart, scanLimit)
    val jsonEnd = balancedEnd ?: explicitEnd?.first ?: scanLimit
    if (jsonEnd <= jsonStart) return null
    val json = value.substring(jsonStart, jsonEnd).trim()
    if (json.isBlank()) return null
    val artifactEnd = explicitEnd
        ?.takeIf { it.first >= jsonEnd || balancedEnd == null }
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
    val root = runCatching { JSONObject(repairStudyJson(json)) }.getOrNull() ?: return null
    if (!signature(root)) return null
    return StudyArtifactSlice(json = json, start = jsonStart, endExclusive = jsonEnd)
}

private fun extractPartialStudyBody(value: String, startMarkers: List<String>): String? {
    val marker = startMarkers
        .mapNotNull { token -> value.indexOf(token).takeIf { it >= 0 }?.let { it to token.length } }
        .minByOrNull { it.first }
    val searchFrom = marker?.let { it.first + it.second } ?: 0
    val jsonStart = value.indexOf('{', searchFrom).takeIf { it >= 0 } ?: return null
    return repairStudyJson(value.substring(jsonStart))
}

private fun extractCompleteObjectsFromNamedArray(
    raw: String,
    aliases: List<String>,
    maxItems: Int
): List<JSONObject> {
    val arrayStart = findNamedArrayStart(raw, aliases) ?: return emptyList()
    val result = mutableListOf<JSONObject>()
    var index = arrayStart
    while (index < raw.length && result.size < maxItems) {
        when (raw[index]) {
            ']' -> break
            '{' -> {
                val end = balancedJsonObjectEnd(raw, index, raw.length) ?: break
                val obj = runCatching { JSONObject(repairStudyJson(raw.substring(index, end))) }.getOrNull()
                if (obj != null) result += obj
                index = end
            }
            else -> index += 1
        }
    }
    return result
}

private fun findNamedArrayStart(raw: String, aliases: List<String>): Int? {
    var best: Int? = null
    aliases.forEach { alias ->
        var from = 0
        while (from < raw.length) {
            val keyIndex = raw.indexOf("\"$alias\"", from, ignoreCase = true)
            if (keyIndex < 0) break
            val colon = raw.indexOf(':', keyIndex + alias.length + 2)
            if (colon < 0) break
            val bracket = raw.indexOf('[', colon + 1)
            if (bracket >= 0 && (best == null || bracket < best!!)) best = bracket + 1
            from = keyIndex + alias.length + 2
        }
    }
    return best
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

private fun looseJsonString(value: String, vararg aliases: String): String {
    aliases.forEach { alias ->
        val key = "\"$alias\""
        var keyIndex = value.indexOf(key, ignoreCase = true)
        while (keyIndex >= 0) {
            val colon = value.indexOf(':', keyIndex + key.length)
            if (colon < 0) break
            var start = colon + 1
            while (start < value.length && value[start].isWhitespace()) start += 1
            if (start >= value.length || value[start] != '"') {
                keyIndex = value.indexOf(key, keyIndex + key.length, ignoreCase = true)
                continue
            }
            start += 1
            var end = start
            var escaped = false
            while (end < value.length) {
                val ch = value[end]
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') {
                    return decodeLooseJsonString(value.substring(start, end))
                }
                end += 1
            }
            break
        }
    }
    return ""
}

private fun decodeLooseJsonString(value: String): String = value
    .replace("\\n", " ")
    .replace("\\r", " ")
    .replace("\\t", " ")
    .replace("\\\"", "\"")
    .replace("\\\\", "\\")

private fun stripStudyArtifact(value: String, artifact: StudyArtifactSlice): String =
    (value.substring(0, artifact.start) + value.substring(artifact.endExclusive.coerceAtMost(value.length)))
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
