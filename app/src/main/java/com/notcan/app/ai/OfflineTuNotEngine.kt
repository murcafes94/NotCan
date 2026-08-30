package com.notcan.app.ai

import com.notcan.app.ui.ai.TuNotOfflineEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Fallback local de TuNot. No pretende sustituir al modelo online: usa únicamente
 * material guardado para recuperar fragmentos y construir mapas estructurados sin red.
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
        return answerEntries(subjectName ?: "Mapa de estudio", entries, question)
    }

    fun answerEntries(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        if (entries.isEmpty()) {
            return "Modo local: todavía no hay apuntes, transcripciones o documentos guardados para trabajar sin conexión."
        }
        return if (isMapRequest(question)) buildMapArtifact(contextTitle, entries, question)
        else buildExtractiveAnswer(entries, question)
    }

    private fun buildMapArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val normalizedQuestion = normalize(question)
        val type = if (normalizedQuestion.contains("conceptual")) "concept_map" else "mind_map"
        val layout = when {
            normalizedQuestion.contains("ideas") -> "ideas"
            normalizedQuestion.contains("radial") -> "radial_cards"
            type == "concept_map" -> "tree"
            else -> "horizontal"
        }
        val tokens = queryTokens(question)
        val ranked = entries
            .map { it to score(it, tokens) }
            .sortedByDescending { it.second }
            .filter { it.second > 0 || tokens.isEmpty() }
            .take(6)
            .ifEmpty { entries.take(6).map { it to 1 } }

        val title = inferMapTitle(question, contextTitle)
        val nodes = JSONArray()
        val edges = JSONArray()
        nodes.put(
            JSONObject()
                .put("id", "root")
                .put("title", title)
                .put("description", "Generado localmente con material guardado en NotCan")
                .put("level", 0)
                .put("source_refs", JSONArray(ranked.map { it.first.title }.distinct()))
        )

        ranked.forEachIndexed { index, pair ->
            val entry = pair.first
            val branchId = "n${index + 1}"
            val branchTitle = compactTitle(entry.title.ifBlank { entry.subtitle }, 46)
            nodes.put(
                JSONObject()
                    .put("id", branchId)
                    .put("title", branchTitle.ifBlank { "Fuente ${index + 1}" })
                    .put("description", entry.subtitle.take(120))
                    .put("level", 1)
                    .put("source_refs", JSONArray(listOf(entry.title)))
            )
            edges.put(
                JSONObject()
                    .put("from", "root")
                    .put("to", branchId)
                    .put("label", if (type == "concept_map") "incluye" else "")
            )

            keySentences(entry.text, tokens).take(3).forEachIndexed { detailIndex, sentence ->
                val detailId = "n${index + 1}_${detailIndex + 1}"
                nodes.put(
                    JSONObject()
                        .put("id", detailId)
                        .put("title", compactTitle(sentence, 52))
                        .put("description", sentence.take(180))
                        .put("level", 2)
                        .put("source_refs", JSONArray(listOf(entry.title)))
                )
                edges.put(
                    JSONObject()
                        .put("from", branchId)
                        .put("to", detailId)
                        .put("label", if (type == "concept_map") "se relaciona con" else "")
                )
            }
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

    private fun buildExtractiveAnswer(entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val candidates = entries.flatMap { entry ->
            splitSentences(entry.text).map { sentence ->
                Triple(entry, sentence, sentenceScore(sentence, tokens) + score(entry, tokens))
            }
        }.sortedByDescending { it.third }

        val selected = candidates
            .filter { it.third > 0 || tokens.isEmpty() }
            .distinctBy { normalize(it.second).take(100) }
            .take(5)

        if (selected.isEmpty()) {
            return "Modo local: no encontré esa información en el material guardado. Prueba con una palabra, concepto o título que aparezca en tus apuntes o transcripciones."
        }

        return buildString {
            appendLine("Modo local · basado únicamente en tu material guardado")
            selected.forEach { (entry, sentence, _) ->
                append("• ")
                append(sentence.take(260))
                append("  [${entry.title}]")
                appendLine()
            }
        }.trim()
    }

    private fun score(entry: TuNotOfflineEntry, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 1
        val title = normalize(entry.title)
        val subtitle = normalize(entry.subtitle)
        val text = normalize(entry.text)
        return tokens.sumOf { token ->
            when {
                title.contains(token) -> 8
                subtitle.contains(token) -> 5
                text.contains(token) -> 2
                else -> 0
            }
        }
    }

    private fun sentenceScore(sentence: String, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 1
        val normalized = normalize(sentence)
        val hits = tokens.count(normalized::contains) * 4
        val definition = if (Regex("(?i)\\b(es|son|significa|consiste|se define|implica|incluye|comprende)\\b").containsMatchIn(sentence)) 2 else 0
        return hits + definition
    }

    private fun keySentences(text: String, tokens: List<String>): List<String> = splitSentences(text)
        .map { it to sentenceScore(it, tokens) }
        .sortedByDescending { it.second }
        .distinctBy { normalize(it.first).take(90) }
        .map { it.first }
        .take(6)

    private fun splitSentences(text: String): List<String> = text
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .split(Regex("(?<=[.!?;:])\\s+|[•\\n]+"))
        .map { it.trim(' ', '-', '•', '\t') }
        .filter { it.length in 20..420 }

    private fun inferMapTitle(question: String, fallback: String): String {
        val cleaned = question
            .replace(Regex("(?i)hazme|haz|hacer|genera|generar|crea|crear|mapa|mental|conceptual|de ideas|de|del|sobre|con|esta|esa|informacion|información"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', ',', '.', '?', '¿')
        return compactTitle(cleaned.ifBlank { fallback }, 64).ifBlank { "Mapa de estudio" }
    }

    private fun compactTitle(value: String, maxChars: Int): String {
        val cleaned = value.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned
        val shortened = cleaned.take(maxChars).substringBeforeLast(' ', cleaned.take(maxChars))
        return shortened.trimEnd() + "…"
    }

    private fun queryTokens(value: String): List<String> = normalize(value)
        .split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in stopWords }
        .distinct()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private val stopWords = setOf(
        "para", "como", "una", "uno", "unos", "unas", "que", "con", "por", "del", "las", "los",
        "esta", "este", "esa", "ese", "sobre", "mapa", "mental", "conceptual", "hazme", "crea", "genera"
    )
}
