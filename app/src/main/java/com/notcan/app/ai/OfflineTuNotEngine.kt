package com.notcan.app.ai

import com.notcan.app.ui.ai.TuNotOfflineEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Motor local determinista de TuNot. Trabaja únicamente con material guardado en NotCan:
 * no necesita Mistral, Internet ni un modelo generativo. Las respuestas son extractivas y
 * los artefactos se construyen a partir de frases realmente presentes en las fuentes.
 */
object OfflineTuNotEngine {
    fun isMapRequest(value: String): Boolean {
        val normalized = normalize(value)
        return listOf(
            "mapa mental", "mapa conceptual", "mapa de ideas", "haz un mapa",
            "hazme un mapa", "crea un mapa", "creame un mapa", "organiza en un mapa",
            "organizalo en un mapa", "ponlo en un mapa", "muestralo como mapa",
            "hazlo mas visual"
        ).any(normalized::contains)
    }

    fun isFlashcardRequest(value: String): Boolean {
        val normalized = normalize(value)
        return listOf("tarjetas", "flashcards", "flash cards", "fichas de estudio", "tarjetas didacticas").any(normalized::contains)
    }

    fun isQuizRequest(value: String): Boolean {
        val normalized = normalize(value)
        return listOf("cuestionario", "quiz", "preguntas de estudio", "preguntas para estudiar", "examen de practica").any(normalized::contains)
    }

    fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String
    ): String {
        val entries = buildList {
            if (notes.isNotBlank()) add(TuNotOfflineEntry("Apuntes", subjectName ?: "Material local", notes))
            if (transcript.isNotBlank()) add(TuNotOfflineEntry("Transcripción", subjectName ?: "Material local", transcript))
        }
        return answerEntries(subjectName ?: "Material de estudio", entries, question)
    }

    fun answerEntries(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val usable = entries.filter { cleanText(it.text).isNotBlank() }
        if (usable.isEmpty()) {
            return "Modo local: todavía no hay apuntes, transcripciones o documentos indexados con contenido para trabajar sin conexión."
        }
        return when {
            isFlashcardRequest(question) -> buildFlashcardsArtifact(contextTitle, usable, question)
            isQuizRequest(question) -> buildQuizArtifact(contextTitle, usable, question)
            isMapRequest(question) -> buildMapArtifact(contextTitle, usable, question)
            else -> buildExtractiveAnswer(usable, question)
        }
    }

    private data class Candidate(
        val entry: TuNotOfflineEntry,
        val sentence: String,
        val score: Int
    )

    private fun buildMapArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val normalizedQuestion = normalize(question)
        val type = if (normalizedQuestion.contains("conceptual")) "concept_map" else "mind_map"
        val layout = when {
            normalizedQuestion.contains("ideas") -> "ideas"
            normalizedQuestion.contains("radial") -> "radial_cards"
            type == "concept_map" -> "tree"
            else -> "horizontal"
        }
        val title = inferMapTitle(question, contextTitle)
        val tokens = queryTokens(question)
        val ranked = rankedSentences(entries, tokens)

        val selected = mutableListOf<Candidate>()
        val labels = mutableSetOf<String>()
        for (candidate in ranked) {
            val label = conceptLabel(candidate.sentence)
            val normalizedLabel = normalize(label)
            if (label.length !in 3..64 || normalizedLabel in labels) continue
            labels += normalizedLabel
            selected += candidate
            if (selected.size >= 10) break
        }
        if (selected.isEmpty()) selected += ranked.take(8)

        val nodes = JSONArray()
        val edges = JSONArray()
        nodes.put(
            JSONObject()
                .put("id", "root")
                .put("title", title)
                .put("description", "Mapa local construido únicamente con material guardado en NotCan")
                .put("level", 0)
                .put("source_refs", JSONArray(selected.map { it.entry.title }.distinct()))
        )

        selected.forEachIndexed { index, candidate ->
            val id = "n${index + 1}"
            val label = conceptLabel(candidate.sentence).ifBlank { "Concepto ${index + 1}" }
            nodes.put(
                JSONObject()
                    .put("id", id)
                    .put("title", compactTitle(label, 48))
                    .put("description", compactTitle(candidate.sentence, 180))
                    .put("level", 1)
                    .put("source_refs", JSONArray(listOf(candidate.entry.title)))
            )
            edges.put(
                JSONObject()
                    .put("from", "root")
                    .put("to", id)
                    .put("label", if (type == "concept_map") relationLabel(candidate.sentence) else "")
            )
        }

        val root = JSONObject()
            .put("type", type)
            .put("title", title)
            .put("layout", layout)
            .put("root_node_id", "root")
            .put("nodes", nodes)
            .put("edges", edges)
        return "<<<NOTCAN_MAP>>>\n${root}\n<<<END_NOTCAN_MAP>>>"
    }

    private fun buildFlashcardsArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val candidates = rankedSentences(entries, tokens).take(18)
        val cards = JSONArray()
        val usedQuestions = mutableSetOf<String>()
        for (candidate in candidates) {
            val qa = sentenceToQuestion(candidate.sentence)
            val normalizedQuestion = normalize(qa.first)
            if (normalizedQuestion in usedQuestions) continue
            usedQuestions += normalizedQuestion
            cards.put(
                JSONObject()
                    .put("question", compactTitle(qa.first, 210))
                    .put("answer", compactTitle(candidate.sentence, 500))
                    .put("source_ref", candidate.entry.title)
            )
            if (cards.length() >= 16) break
        }
        if (cards.length() == 0) return "Modo local: no encontré suficiente contenido legible para generar tarjetas."
        val root = JSONObject()
            .put("title", "Tarjetas · ${compactTitle(contextTitle, 58)}")
            .put("cards", cards)
        return "<<<NOTCAN_FLASHCARDS>>>\n${root}\n<<<END_NOTCAN_FLASHCARDS>>>"
    }

    private fun buildQuizArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val candidates = rankedSentences(entries, tokens).take(20)
        val questions = JSONArray()
        val used = mutableSetOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val qa = sentenceToQuestion(candidate.sentence)
            val prompt = if (index % 3 == 0) {
                "Verdadero o falso: ${compactTitle(candidate.sentence, 250)}"
            } else qa.first
            if (!used.add(normalize(prompt))) return@forEachIndexed
            val type = if (index % 3 == 0) "true_false" else "short_answer"
            val correct = if (type == "true_false") "Verdadero" else compactTitle(candidate.sentence, 480)
            val item = JSONObject()
                .put("id", "q${questions.length() + 1}")
                .put("type", type)
                .put("question", compactTitle(prompt, 310))
                .put("options", JSONArray())
                .put("correct_answer", correct)
                .put("explanation", "Según ${candidate.entry.title}: ${compactTitle(candidate.sentence, 430)}")
                .put("source_ref", candidate.entry.title)
            questions.put(item)
        }
        if (questions.length() == 0) return "Modo local: no encontré suficiente contenido legible para generar un cuestionario."
        val root = JSONObject()
            .put("title", "Cuestionario · ${compactTitle(contextTitle, 55)}")
            .put("questions", questions)
        return "<<<NOTCAN_QUIZ>>>\n${root}\n<<<END_NOTCAN_QUIZ>>>"
    }

    private fun buildExtractiveAnswer(entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val selected = rankedSentences(entries, tokens)
            .filter { it.score > 0 || tokens.isEmpty() }
            .distinctBy { normalize(it.sentence).take(120) }
            .take(6)
        if (selected.isEmpty()) {
            return "Modo local: no encontré esa información en el material guardado. Prueba con una palabra, concepto o título que aparezca en tus apuntes o transcripciones."
        }
        return buildString {
            appendLine("Modo local · basado únicamente en tu material guardado")
            selected.forEach { candidate ->
                append("• ")
                append(compactTitle(candidate.sentence, 310))
                append("  [${candidate.entry.title}]")
                appendLine()
            }
        }.trim()
    }

    private fun rankedSentences(entries: List<TuNotOfflineEntry>, tokens: List<String>): List<Candidate> = entries
        .flatMap { entry ->
            splitSentences(entry.text).map { sentence ->
                Candidate(entry, sentence, sentenceScore(sentence, tokens) + score(entry, tokens))
            }
        }
        .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { informationScore(it.sentence) })
        .distinctBy { normalize(it.sentence).take(140) }

    private fun sentenceToQuestion(sentence: String): Pair<String, String> {
        val clean = sentence.trim().trimEnd('.', ';', ':')
        val pattern = Regex("^(.{2,80}?)\\s+(es|son|consiste en|se define como|implica|incluye|comprende)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = pattern.find(clean)
        if (match != null) {
            val subject = compactTitle(match.groupValues[1].trim(), 74)
            val relation = normalize(match.groupValues[2])
            val question = when {
                relation == "son" -> "¿Qué son $subject?"
                relation.contains("implica") -> "¿Qué implica $subject?"
                relation.contains("incluye") || relation.contains("comprende") -> "¿Qué incluye $subject?"
                else -> "¿Qué es $subject?"
            }
            return question to clean
        }
        val label = conceptLabel(clean)
        return "¿Qué indica el material sobre $label?" to clean
    }

    private fun conceptLabel(sentence: String): String {
        val clean = compactTitle(cleanText(sentence), 180).trimEnd('.', ';', ':')
        val prefix = clean.substringBefore(':', "").trim()
        if (prefix.length in 3..46 && ':' in clean) return prefix
        val definition = Regex("^(.{3,58}?)\\s+(es|son|consiste en|se define como|implica|incluye|comprende)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (definition != null) return definition.groupValues[1].trim()
        val words = clean.split(Regex("\\s+"))
            .map { it.trim(' ', ',', '.', ';', ':', '¿', '?', '¡', '!') }
            .filter { it.length >= 3 && normalize(it) !in stopWords }
            .take(5)
        return words.joinToString(" ").ifBlank { clean.take(46) }
    }

    private fun relationLabel(sentence: String): String {
        val n = normalize(sentence)
        return when {
            " implica " in " $n " -> "implica"
            " incluye " in " $n " || " comprende " in " $n " -> "incluye"
            Regex("\\b(es|son|se define)\\b").containsMatchIn(n) -> "define"
            " causa " in " $n " || " consecuencia " in " $n " -> "relaciona"
            else -> "explica"
        }
    }

    private fun score(entry: TuNotOfflineEntry, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 1
        val title = normalize(entry.title)
        val subtitle = normalize(entry.subtitle)
        val text = normalize(entry.text)
        return tokens.fold(0) { total, token ->
            total + when {
                title.contains(token) -> 8
                subtitle.contains(token) -> 5
                text.contains(token) -> 2
                else -> 0
            }
        }
    }

    private fun sentenceScore(sentence: String, tokens: List<String>): Int {
        if (tokens.isEmpty()) return informationScore(sentence)
        val normalized = normalize(sentence)
        val hits = tokens.count(normalized::contains) * 5
        return hits + informationScore(sentence)
    }

    private fun informationScore(sentence: String): Int {
        var score = 0
        if (Regex("(?i)\\b(es|son|significa|consiste|se define|implica|incluye|comprende|causa|consecuencia)\\b").containsMatchIn(sentence)) score += 4
        if (sentence.length in 35..240) score += 2
        if (sentence.count { it == ',' } in 1..4) score += 1
        return score
    }

    private fun splitSentences(text: String): List<String> = cleanText(text)
        .split(Regex("(?<=[.!?;:])\\s+|[•\\n]+"))
        .map { it.trim(' ', '-', '•') }
        .filter { it.length in 20..520 }

    private fun cleanText(text: String): String = text
        .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("[ \\t\\r]+"), " ")
        .replace(Regex("\\n{3,}"), "\\n\\n")
        .trim()

    private fun inferMapTitle(question: String, fallback: String): String {
        val cleaned = question
            .replace(Regex("(?i)hazme|haz|hacer|genera|generar|crea|crear|mapa|mental|conceptual|de ideas|organiza|organizar|muestralo|muéstralo|esta clase|la clase|de la clase|sobre|con"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', ',', '.', '?', '¿')
        val normalized = normalize(cleaned)
        val generic = cleaned.isBlank() || normalized in setOf("clase", "ideas", "estudio", "tema") || cleaned.length < 4
        return compactTitle(if (generic) fallback else cleaned, 64).ifBlank { "Mapa de estudio" }
    }

    private fun compactTitle(value: String, maxChars: Int): String {
        val cleaned = value.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned
        val raw = cleaned.take(maxChars)
        val shortened = raw.substringBeforeLast(' ', raw)
        return shortened.trimEnd() + "…"
    }

    private fun queryTokens(value: String): List<String> = normalize(value)
        .split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in stopWords }
        .distinct()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private val stopWords = setOf(
        "para", "como", "una", "uno", "unos", "unas", "que", "con", "por", "del", "las", "los",
        "esta", "este", "esa", "ese", "sobre", "mapa", "mental", "conceptual", "hazme", "crea", "genera",
        "clase", "material", "fuente", "fuentes", "estudio", "segun", "desde", "entre", "tambien", "donde"
    )
}
