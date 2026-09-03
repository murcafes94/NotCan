from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:180]!r}")
    write(path, text.replace(old, new, 1))


def replace_between(path, start, end, replacement):
    text = read(path)
    i = text.find(start)
    if i < 0:
        raise RuntimeError(f"Start marker not found in {path}: {start!r}")
    j = text.find(end, i)
    if j < 0:
        raise RuntimeError(f"End marker not found in {path}: {end!r}")
    write(path, text[:i] + replacement + text[j:])


# 1) Study cards/quizzes: accept strict JSON, and recover all complete items from a truncated array.
write(
    "app/src/main/java/com/notcan/app/ui/ai/TuNotStudyArtifacts.kt",
    r'''package com.notcan.app.ui.ai

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
'''
)

# 2) Maps: recover completed nodes/edges from a truncated JSON and synthesize missing links.
write(
    "app/src/main/java/com/notcan/app/ui/maps/StudyMapArtifactParser.kt",
    r'''package com.notcan.app.ui.maps

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ParsedStudyMapArtifact(
    val map: StudyMap,
    val preferredLayout: StudyMapLayoutStyle
)

private data class MapArtifactSlice(
    val json: String,
    val start: Int,
    val endExclusive: Int
)

internal object StudyMapArtifactParser {
    const val START_MARKER = "<<<NOTCAN_MAP>>>"
    const val END_MARKER = "<<<END_NOTCAN_MAP>>>"
    private val START_MARKERS = listOf(START_MARKER, "<<NOTCAN_MAP>>", "<NOTCAN_MAP>")
    private val END_MARKERS = listOf(END_MARKER, "<<END_NOTCAN_MAP>>", "<END_NOTCAN_MAP>")
    private const val MAX_NODES = 32

    fun parse(value: String): ParsedStudyMapArtifact? {
        val artifact = extractArtifact(value) ?: return null
        val strict = runCatching { parseJson(JSONObject(repairModelJson(artifact.json))) }.getOrNull()
        if (strict != null) return strict
        return parsePartialMap(artifact.json)
    }

    fun stripArtifact(value: String): String {
        val artifact = extractArtifact(value) ?: return value
        return (value.substring(0, artifact.start) + value.substring(artifact.endExclusive.coerceAtMost(value.length)))
            .replace("```json", "")
            .replace("```", "")
            .trim()
            .ifBlank { "Preparé el mapa. Puedes abrirlo, explorarlo y compartirlo." }
    }

    private fun extractArtifact(value: String): MapArtifactSlice? {
        val markerMatch = START_MARKERS
            .mapNotNull { marker -> value.indexOf(marker).takeIf { it >= 0 }?.let { it to marker.length } }
            .minByOrNull { it.first }
            ?: return extractBareArtifact(value)
        val markerStart = markerMatch.first
        val contentStart = markerStart + markerMatch.second
        val explicitEnd = END_MARKERS
            .mapNotNull { marker -> value.indexOf(marker, contentStart).takeIf { it >= 0 }?.let { it to marker.length } }
            .minByOrNull { it.first }
        val scanLimit = explicitEnd?.first ?: value.length
        val jsonStart = value.indexOf('{', contentStart).takeIf { it >= contentStart && it < scanLimit } ?: return null
        val balancedEnd = balancedObjectEnd(value, jsonStart, scanLimit)
        val jsonEnd = balancedEnd ?: explicitEnd?.first ?: scanLimit
        if (jsonEnd <= jsonStart) return null
        val json = value.substring(jsonStart, jsonEnd).trim()
        if (json.isBlank()) return null
        val artifactEnd = explicitEnd
            ?.takeIf { it.first >= jsonEnd || balancedEnd == null }
            ?.let { it.first + it.second }
            ?: jsonEnd
        return MapArtifactSlice(json = json, start = markerStart, endExclusive = artifactEnd)
    }

    private fun extractBareArtifact(value: String): MapArtifactSlice? {
        val jsonStart = value.indexOf('{').takeIf { it >= 0 } ?: return null
        val balancedEnd = balancedObjectEnd(value, jsonStart, value.length)
        if (balancedEnd != null) {
            val json = value.substring(jsonStart, balancedEnd).trim()
            val root = runCatching { JSONObject(repairModelJson(json)) }.getOrNull() ?: return null
            val nodes = root.flexArray("nodes") ?: return null
            if (nodes.length() == 0) return null
            val type = root.flexString("type").lowercase()
            val edges = root.flexArray("edges")
            val looksLikeMap = type in setOf("mind_map", "concept_map", "conceptual", "concept") || edges != null
            if (!looksLikeMap) return null
            return MapArtifactSlice(json = json, start = jsonStart, endExclusive = balancedEnd)
        }
        val tail = value.substring(jsonStart)
        if (!tail.contains("\"nodes\"", ignoreCase = true)) return null
        return MapArtifactSlice(json = tail, start = jsonStart, endExclusive = value.length)
    }

    private fun balancedObjectEnd(value: String, start: Int, limit: Int): Int? {
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

    private fun repairModelJson(value: String): String {
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

    private fun parseJson(root: JSONObject): ParsedStudyMapArtifact {
        val type = mapType(root.flexString("type"))
        val preferredLayout = mapLayout(root.flexString("layout"))
        val title = compact(root.flexString("title").ifBlank { "Mapa de estudio" }, 96)
        val nodesArray = root.flexArray("nodes") ?: error("El mapa no contiene nodos")
        val allNodes = buildList {
            for (i in 0 until minOf(nodesArray.length(), MAX_NODES)) {
                nodesArray.optJSONObject(i)?.let { nodeFromJson(it, i)?.let(::add) }
            }
        }
        require(allNodes.isNotEmpty()) { "El mapa no contiene nodos" }
        val requestedRootId = root.flexString("root_node_id", "rootNodeId", "rootnodeid")
        val rootNode = allNodes.firstOrNull { it.id == requestedRootId } ?: allNodes.first()
        val nodes = reorderNodes(rootNode, allNodes)
        val edges = parseEdges(root.flexArray("edges"), nodes)
        return buildArtifact(title, type, preferredLayout, rootNode, nodes, ensureConnected(edges, nodes, rootNode))
    }

    private fun parsePartialMap(rawValue: String): ParsedStudyMapArtifact? {
        val raw = repairModelJson(rawValue)
        val nodeObjects = extractCompleteObjectsFromNamedArray(raw, listOf("nodes"), MAX_NODES)
        val allNodes = nodeObjects.mapIndexedNotNull { index, item -> nodeFromJson(item, index) }
        if (allNodes.isEmpty()) return null
        val requestedRootId = looseJsonString(raw, "root_node_id", "rootNodeId", "rootnodeid")
        val rootNode = allNodes.firstOrNull { it.id == requestedRootId } ?: allNodes.first()
        val nodes = reorderNodes(rootNode, allNodes)
        val edgeObjects = extractCompleteObjectsFromNamedArray(raw, listOf("edges"), MAX_NODES * 2)
        val parsedEdges = edgeObjects.mapNotNull { edgeFromJson(it, nodes) }
        val title = compact(looseJsonString(raw, "title").ifBlank { "Mapa de estudio" }, 96)
        return buildArtifact(
            title = title,
            type = mapType(looseJsonString(raw, "type")),
            preferredLayout = mapLayout(looseJsonString(raw, "layout")),
            rootNode = rootNode,
            nodes = nodes,
            edges = ensureConnected(parsedEdges, nodes, rootNode)
        )
    }

    private fun nodeFromJson(item: JSONObject, index: Int): StudyMapNode? {
        val level = item.optInt("level", if (index == 0) 0 else 1).coerceIn(0, 3)
        val title = compact(item.flexString("title").ifBlank { "Concepto" }, if (level == 0) 140 else 120)
        if (title.isBlank()) return null
        return StudyMapNode(
            id = item.flexString("id").ifBlank { if (index == 0) "root" else "n$index" },
            title = title,
            description = item.flexString("description")
                .takeIf { it.isNotBlank() }
                ?.let { compact(it, 1400) },
            level = level,
            sourceRefs = buildList {
                val refs = item.flexArray("source_refs", "sourceRefs", "sourcerefs")
                if (refs != null) {
                    for (j in 0 until refs.length()) add(compact(refs.optString(j), 80))
                } else {
                    item.flexString("source_ref", "sourceRef", "sourceref")
                        .takeIf { it.isNotBlank() }
                        ?.let { add(compact(it, 80)) }
                }
            }.filter { it.isNotBlank() }.distinct().take(3)
        )
    }

    private fun parseEdges(array: JSONArray?, nodes: List<StudyMapNode>): List<StudyMapEdge> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { edgeFromJson(it, nodes)?.let(::add) }
            }
        }
    }

    private fun edgeFromJson(item: JSONObject, nodes: List<StudyMapNode>): StudyMapEdge? {
        val allowedIds = nodes.mapTo(hashSetOf()) { it.id }
        val from = item.flexString("from", "from_id", "fromId")
        val to = item.flexString("to", "to_id", "toId")
        if (from !in allowedIds || to !in allowedIds || from == to) return null
        return StudyMapEdge(
            from = from,
            to = to,
            label = item.flexString("label").takeIf { it.isNotBlank() }?.let { compact(it, 120) }
        )
    }

    private fun reorderNodes(rootNode: StudyMapNode, allNodes: List<StudyMapNode>): List<StudyMapNode> = buildList {
        add(rootNode)
        addAll(
            allNodes.asSequence()
                .filterNot { it.id == rootNode.id }
                .sortedBy { it.level }
                .take(MAX_NODES - 1)
                .toList()
        )
    }

    private fun ensureConnected(
        rawEdges: List<StudyMapEdge>,
        nodes: List<StudyMapNode>,
        rootNode: StudyMapNode
    ): List<StudyMapEdge> {
        val allowed = nodes.mapTo(hashSetOf()) { it.id }
        val edges = rawEdges
            .filter { it.from in allowed && it.to in allowed && it.from != it.to }
            .distinctBy { Triple(it.from, it.to, it.label) }
            .toMutableList()
        val incoming = edges.mapTo(hashSetOf()) { it.to }
        val lastAtLevel = mutableMapOf(0 to rootNode.id)
        nodes.forEach { node ->
            if (node.id == rootNode.id) return@forEach
            if (node.id !in incoming) {
                val parent = (node.level.coerceAtLeast(1) - 1 downTo 0)
                    .firstNotNullOfOrNull { level -> lastAtLevel[level] }
                    ?: rootNode.id
                edges += StudyMapEdge(parent, node.id, null)
                incoming += node.id
            }
            lastAtLevel[node.level] = node.id
            lastAtLevel.keys.filter { it > node.level }.toList().forEach(lastAtLevel::remove)
        }
        return edges.distinctBy { Triple(it.from, it.to, it.label) }
    }

    private fun buildArtifact(
        title: String,
        type: StudyMapType,
        preferredLayout: StudyMapLayoutStyle,
        rootNode: StudyMapNode,
        nodes: List<StudyMapNode>,
        edges: List<StudyMapEdge>
    ): ParsedStudyMapArtifact = ParsedStudyMapArtifact(
        map = StudyMap(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            rootNodeId = rootNode.id,
            nodes = nodes,
            edges = edges
        ),
        preferredLayout = preferredLayout
    )

    private fun mapType(value: String): StudyMapType = when (value.lowercase()) {
        "concept_map", "conceptual", "concept" -> StudyMapType.CONCEPT_MAP
        else -> StudyMapType.MIND_MAP
    }

    private fun mapLayout(value: String): StudyMapLayoutStyle = when (value.lowercase()) {
        "radial" -> StudyMapLayoutStyle.RADIAL
        "radial_cards", "cards", "tarjetas", "visual_cards" -> StudyMapLayoutStyle.RADIAL_CARDS
        "ideas", "idea_board", "mapa_de_ideas", "visual", "creative" -> StudyMapLayoutStyle.IDEA_BOARD
        "tree", "árbol", "arbol" -> StudyMapLayoutStyle.TREE
        "constellation", "constelacion", "constelación" -> StudyMapLayoutStyle.CONSTELLATION
        else -> StudyMapLayoutStyle.HORIZONTAL_BRANCHES
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
                    val end = balancedObjectEnd(raw, index, raw.length) ?: break
                    val obj = runCatching { JSONObject(repairModelJson(raw.substring(index, end))) }.getOrNull()
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
                        return value.substring(start, end)
                            .replace("\\n", " ")
                            .replace("\\r", " ")
                            .replace("\\t", " ")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                    }
                    end += 1
                }
                break
            }
        }
        return ""
    }

    private fun JSONObject.flexString(vararg aliases: String): String {
        aliases.forEach { alias -> if (has(alias)) return optString(alias) }
        val normalized = aliases.mapTo(hashSetOf()) { normalizeKey(it) }
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (normalizeKey(key) in normalized) return optString(key)
        }
        return ""
    }

    private fun JSONObject.flexArray(vararg aliases: String): JSONArray? {
        aliases.forEach { alias -> optJSONArray(alias)?.let { return it } }
        val normalized = aliases.mapTo(hashSetOf()) { normalizeKey(it) }
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (normalizeKey(key) in normalized) optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun normalizeKey(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }

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
'''
)

# 3) Local Gemma: reserve context/output budget for structured resources, lower temperature,
#    preserve structured partials, and allow enough time without raising the model token cache.
p = "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
replace_once(
    p,
    '''            samplerConfig = SamplerConfig(
                topK = TOP_K,
                topP = TOP_P,
                temperature = TEMPERATURE
            )
''',
    '''            samplerConfig = SamplerConfig(
                topK = TOP_K,
                topP = TOP_P,
                temperature = if (isStudyArtifactRequest(intentQuestion)) STRUCTURED_TEMPERATURE else TEMPERATURE
            )
'''
)
replace_once(
    p,
    '''        val tokens = queryTokens(question)
        val broadRequest = isBroadSourceRequest(question)
        val sourceOverviewRequest = isSourceOverviewRequest(question)
''',
    '''        val tokens = queryTokens(question)
        val artifactRequest = isStudyArtifactRequest(question)
        val broadRequest = isBroadSourceRequest(question)
        val sourceOverviewRequest = isSourceOverviewRequest(question)
'''
)
replace_once(
    p,
    '''        val selected = when {
            broadRequest -> evenlySample(scored, BROAD_SELECTED_CHUNKS)
            sourceOverviewRequest && tokens.isNotEmpty() -> scored
''',
    '''        val selected = when {
            artifactRequest -> evenlySample(scored, ARTIFACT_SELECTED_CHUNKS)
            broadRequest -> evenlySample(scored, BROAD_SELECTED_CHUNKS)
            sourceOverviewRequest && tokens.isNotEmpty() -> scored
'''
)
replace_once(
    p,
    '''        val sourceCharLimit = when {
            broadRequest -> MAX_BROAD_SOURCE_CHARS
            sourceOverviewRequest -> MAX_OVERVIEW_SOURCE_CHARS
            else -> MAX_FOCUSED_SOURCE_CHARS
        }
''',
    '''        val sourceCharLimit = when {
            artifactRequest -> MAX_ARTIFACT_SOURCE_CHARS
            broadRequest -> MAX_BROAD_SOURCE_CHARS
            sourceOverviewRequest -> MAX_OVERVIEW_SOURCE_CHARS
            else -> MAX_FOCUSED_SOURCE_CHARS
        }
'''
)
replace_once(
    p,
    '''    private fun generationTimeoutMs(question: String): Long {
        val n = normalize(question)
        val brief = isResponseTransformRequest(question) || listOf("brevemente", "una frase", "muy breve").any(n::contains)
        if (brief) return 90_000L
''',
    '''    private fun generationTimeoutMs(question: String): Long {
        val n = normalize(question)
        if (isStudyArtifactRequest(question)) return 300_000L
        val brief = isResponseTransformRequest(question) || listOf("brevemente", "una frase", "muy breve").any(n::contains)
        if (brief) return 90_000L
'''
)
replace_once(
    p,
    '''    private fun recoverUsefulPartial(raw: String): String? {
        val text = raw.trim()
        if (text.length < MIN_USEFUL_PARTIAL_CHARS) return null
        val lastSentence = maxOf(text.lastIndexOf('.'), text.lastIndexOf('!'), text.lastIndexOf('?'))
''',
    '''    private fun recoverUsefulPartial(raw: String): String? {
        val text = raw.trim()
        if (text.length < MIN_USEFUL_PARTIAL_CHARS) return null
        if (looksLikeStudyArtifact(text)) return text
        val lastSentence = maxOf(text.lastIndexOf('.'), text.lastIndexOf('!'), text.lastIndexOf('?'))
'''
)
replace_once(
    p,
    '''    private fun isResponseTransformRequest(question: String): Boolean {
''',
    '''    private fun looksLikeStudyArtifact(value: String): Boolean =
        value.contains("NOTCAN_MAP", ignoreCase = true) ||
            value.contains("NOTCAN_FLASHCARDS", ignoreCase = true) ||
            value.contains("NOTCAN_QUIZ", ignoreCase = true) ||
            (value.trimStart().startsWith("{") && listOf("\\\"nodes\\\"", "\\\"cards\\\"", "\\\"questions\\\"").any(value::contains))

    private fun isStudyArtifactRequest(question: String): Boolean {
        val n = normalize(question)
        return listOf(
            "mapa mental", "mapa conceptual", "mapa de ideas", "mind map", "concept map",
            "tarjetas didacticas", "flashcards", "cuestionario", "quiz"
        ).any(n::contains)
    }

    private fun isResponseTransformRequest(question: String): Boolean {
'''
)
replace_once(
    p,
    '''        private const val MAX_BROAD_SOURCE_CHARS = 8_500
        private const val MAX_OVERVIEW_SOURCE_CHARS = 5_500
''',
    '''        private const val MAX_BROAD_SOURCE_CHARS = 8_500
        private const val MAX_ARTIFACT_SOURCE_CHARS = 5_200
        private const val MAX_OVERVIEW_SOURCE_CHARS = 5_500
'''
)
replace_once(
    p,
    '''        private const val BROAD_SELECTED_CHUNKS = 7
        private const val OVERVIEW_SELECTED_CHUNKS = 5
''',
    '''        private const val BROAD_SELECTED_CHUNKS = 7
        private const val ARTIFACT_SELECTED_CHUNKS = 5
        private const val OVERVIEW_SELECTED_CHUNKS = 5
'''
)
replace_once(
    p,
    '''        private const val TEMPERATURE = 0.30
        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 30_000L
''',
    '''        private const val TEMPERATURE = 0.30
        private const val STRUCTURED_TEMPERATURE = 0.12
        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 30_000L
'''
)

# 4) Compact, deterministic local artifact prompts. The user can still explicitly request a larger set.
p = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
new_local_question = r'''    private fun buildLocalQuestion(
        cleanQuestion: String,
        mapRequest: Boolean,
        conceptualMapRequest: Boolean,
        ideaMapRequest: Boolean,
        flashcardRequest: Boolean,
        quizRequest: Boolean,
        pedagogicalMode: Boolean
    ): String = buildString {
        appendLine(cleanQuestion)
        if (pedagogicalMode) {
            appendLine()
            appendLine("Actúa como pedagogo académico de NotCan: ayuda a comprender, organizar el estudio, priorizar y elegir técnicas concretas. No hagas diagnósticos psicológicos.")
        }
        if (mapRequest) {
            appendLine()
            appendLine("Devuelve exclusivamente un mapa entre <<<NOTCAN_MAP>>> y <<<END_NOTCAN_MAP>>> con JSON válido y sin markdown.")
            appendLine("Esquema compacto: {\"type\":\"mind_map\",\"title\":\"...\",\"layout\":\"horizontal\",\"root_node_id\":\"root\",\"nodes\":[{\"id\":\"root\",\"title\":\"...\",\"description\":\"...\",\"level\":0}],\"edges\":[{\"from\":\"root\",\"to\":\"n1\"}]}")
            appendLine("Genera normalmente 8–12 nodos. Si el espacio de salida no alcanza, genera menos nodos completos.")
            appendLine("PRIORIDAD ABSOLUTA DE FORMATO: cierra siempre cada objeto, el array, el JSON y el marcador <<<END_NOTCAN_MAP>>>. Nunca empieces un nodo que no puedas terminar.")
            when {
                conceptualMapRequest -> appendLine("Usa type concept_map y prioriza relaciones semánticas etiquetadas.")
                ideaMapRequest -> appendLine("Usa layout ideas y tarjetas breves alrededor del tema central.")
                else -> appendLine("Usa type mind_map y una jerarquía tema central → ramas → subramas.")
            }
        }
        if (flashcardRequest) {
            appendLine()
            appendLine("Devuelve exclusivamente entre <<<NOTCAN_FLASHCARDS>>> y <<<END_NOTCAN_FLASHCARDS>>> un JSON válido: {\"title\":\"...\",\"cards\":[{\"question\":\"...\",\"answer\":\"...\"}]}")
            appendLine("Genera normalmente 10–12 tarjetas atómicas. Respuestas breves: una o dos frases salvo necesidad académica.")
            appendLine("Si el usuario exige más tarjetas, completa tantas como puedas sin sacrificar el cierre del formato.")
            appendLine("PRIORIDAD ABSOLUTA DE FORMATO: cierra siempre cada tarjeta, el array, el JSON y el marcador <<<END_NOTCAN_FLASHCARDS>>>. Nunca empieces una tarjeta que no puedas terminar.")
        }
        if (quizRequest) {
            appendLine()
            appendLine("Devuelve exclusivamente entre <<<NOTCAN_QUIZ>>> y <<<END_NOTCAN_QUIZ>>> un JSON válido: {\"title\":\"...\",\"questions\":[{\"id\":\"q1\",\"type\":\"multiple_choice|true_false|short_answer\",\"question\":\"...\",\"options\":[\"...\"],\"correct_answer\":\"...\",\"explanation\":\"...\"}]}")
            appendLine("Genera normalmente 10–12 preguntas. En opción múltiple usa 4 opciones y una sola respuesta correcta literal; explanation debe ser breve.")
            appendLine("Si el usuario exige más preguntas, completa tantas como puedas sin sacrificar el cierre del formato.")
            appendLine("PRIORIDAD ABSOLUTA DE FORMATO: cierra siempre cada pregunta, el array, el JSON y el marcador <<<END_NOTCAN_QUIZ>>>. Nunca empieces una pregunta que no puedas terminar.")
        }
    }

'''
replace_between(p, "    private fun buildLocalQuestion(\n", "    private fun sourcePlainText", new_local_question)

# Also tell the online path to prefer a smaller complete artifact over invalid JSON.
replace_once(
    p,
    '''                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown y sin comentarios.")
                appendLine("Esquema obligatorio:")
''',
    '''                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown y sin comentarios.")
                appendLine("Prioridad de formato: termina y cierra el JSON y el marcador final. Si falta espacio, reduce el número de elementos antes que truncar el recurso.")
                appendLine("Esquema obligatorio:")
'''
)
replace_once(
    p,
    '''                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown, sin comentarios y sin texto adicional.")
                appendLine("Esquema obligatorio:")
''',
    '''                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown, sin comentarios y sin texto adicional.")
                appendLine("Prioridad de formato: termina y cierra el JSON y el marcador final. Si falta espacio, reduce el número de elementos antes que truncar el recurso.")
                appendLine("Esquema obligatorio:")
'''
)
# second identical occurrence is the quiz block
replace_once(
    p,
    '''                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown, sin comentarios y sin texto adicional.")
                appendLine("Esquema obligatorio:")
''',
    '''                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown, sin comentarios y sin texto adicional.")
                appendLine("Prioridad de formato: termina y cierra el JSON y el marcador final. Si falta espacio, reduce el número de elementos antes que truncar el recurso.")
                appendLine("Esquema obligatorio:")
'''
)

# 5) UI defaults: compact first generation, and never leave auto-save armed after a failed artifact.
p = "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
replace_once(
    p,
    '''        if (autoSaveNextArtifact && !busy && result.isNotBlank()) {
            if (artifactStore.save(artifactScope, result) != null) {
                artifactRevision += 1
                autoSaveNextArtifact = false
            }
        }
''',
    '''        if (autoSaveNextArtifact && !busy && result.isNotBlank()) {
            if (artifactStore.save(artifactScope, result) != null) artifactRevision += 1
            autoSaveNextArtifact = false
        }
'''
)
replace_once(
    p,
    'StudyTool("Tarjetas didácticas", "Repaso activo, una pregunta por tarjeta", Icons.Default.Style, "Crea entre 12 y 20 tarjetas didácticas de esta clase.", NotCanAiService.FLASHCARDS_MARKER),',
    'StudyTool("Tarjetas didácticas", "Repaso activo, una pregunta por tarjeta", Icons.Default.Style, "Crea entre 10 y 12 tarjetas didácticas de esta clase. Prioriza que todas queden completas.", NotCanAiService.FLASHCARDS_MARKER),'
)
replace_once(
    p,
    'StudyTool("Cuestionario", "Respóndelo aquí y repite los errores", Icons.Default.Quiz, "Crea un cuestionario mixto basado exclusivamente en esta clase. Combina opción múltiple, verdadero/falso y algunas preguntas breves de desarrollo.", NotCanAiService.QUIZ_MARKER),',
    'StudyTool("Cuestionario", "Respóndelo aquí y repite los errores", Icons.Default.Quiz, "Crea un cuestionario mixto de 10 a 12 preguntas basado exclusivamente en esta clase. Combina opción múltiple, verdadero/falso y algunas preguntas breves de desarrollo.", NotCanAiService.QUIZ_MARKER),'
)
replace_once(
    p,
    '''    return if (looksStructured) {
        "TuNot generó un recurso de estudio, pero el formato llegó incompleto. Vuelve a generarlo para abrirlo de forma interactiva."
    } else raw
''',
    '''    return if (looksStructured) {
        "TuNot no alcanzó a completar suficientes elementos del recurso para abrirlo de forma interactiva. NotCan intentó recuperar automáticamente las partes completas; vuelve a Estudio para regenerarlo si este aviso aparece."
    } else raw
'''
)

# 6) Version bump.
p = "app/build.gradle.kts"
replace_once(p, '        versionCode = 45\n        versionName = "0.8.22"', '        versionCode = 46\n        versionName = "0.8.23"')

print("Applied NotCan 0.8.23 study artifact recovery patch")
