package com.notcan.app.sources

import java.text.Normalizer
import kotlin.math.min

/**
 * Lightweight local retrieval for indexed study documents.
 *
 * It intentionally avoids a hosted/vector dependency: documents are split into bounded chunks,
 * page markers are preserved for PDFs and candidates are ranked lexically. A future embedding
 * provider can rerank the same chunks without changing the source storage format.
 */
object SourceRetrieval {
    private val pageMarker = Regex("(?m)^\\[\\[NOTCAN_PAGE:(\\d+)]]\\s*$")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]{2,}")

    data class Chunk(
        val text: String,
        val pageNumber: Int?,
        val offset: Int
    )

    data class RankedChunk(
        val chunk: Chunk,
        val score: Double
    )

    fun pageCount(indexedText: String): Int =
        pageMarker.findAll(indexedText).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0

    fun split(
        indexedText: String,
        chunkChars: Int = 1_800,
        overlapChars: Int = 220
    ): List<Chunk> {
        if (indexedText.isBlank()) return emptyList()
        require(chunkChars >= 300) { "chunkChars demasiado pequeño" }
        require(overlapChars >= 0 && overlapChars < chunkChars) { "overlapChars inválido" }

        val markers = pageMarker.findAll(indexedText).toList()
        if (markers.isEmpty()) return splitSegment(indexedText, null, 0, chunkChars, overlapChars)

        val out = mutableListOf<Chunk>()
        val prefix = indexedText.substring(0, markers.first().range.first).trim()
        if (prefix.isNotBlank()) {
            out += splitSegment(prefix, null, 0, chunkChars, overlapChars)
        }

        markers.forEachIndexed { index, match ->
            val page = match.groupValues[1].toIntOrNull()
            val segmentStart = match.range.last + 1
            val segmentEnd = if (index + 1 < markers.size) markers[index + 1].range.first else indexedText.length
            if (segmentEnd <= segmentStart) return@forEachIndexed
            val segment = indexedText.substring(segmentStart, segmentEnd)
            out += splitSegment(segment, page, segmentStart, chunkChars, overlapChars)
        }
        return out
    }

    fun retrieve(
        indexedText: String,
        query: String,
        sourceName: String = "",
        maxHits: Int = 8,
        chunkChars: Int = 1_800
    ): List<RankedChunk> {
        if (maxHits <= 0 || indexedText.isBlank() || query.isBlank()) return emptyList()
        val queryTerms = meaningfulTerms(query)
        val normalizedQuery = normalize(query).trim()
        if (queryTerms.isEmpty() && normalizedQuery.length < 2) return emptyList()

        val normalizedSourceName = normalize(sourceName)
        return split(indexedText, chunkChars = chunkChars)
            .asSequence()
            .map { chunk ->
                val normalizedText = normalize(chunk.text)
                val score = score(
                    normalizedText = normalizedText,
                    normalizedQuery = normalizedQuery,
                    terms = queryTerms,
                    normalizedSourceName = normalizedSourceName
                )
                RankedChunk(chunk, score)
            }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<RankedChunk> { it.score }.thenBy { it.chunk.offset })
            .take(maxHits)
            .toList()
    }

    fun sample(indexedText: String, maxChunks: Int = 2, chunkChars: Int = 1_600): List<Chunk> {
        if (maxChunks <= 0) return emptyList()
        val chunks = split(indexedText, chunkChars = chunkChars)
        if (chunks.size <= maxChunks) return chunks
        if (maxChunks == 1) return listOf(chunks.first())

        // Spread broad summaries over the document instead of taking only its beginning.
        val lastIndex = chunks.lastIndex
        return (0 until maxChunks)
            .map { slot -> ((slot.toDouble() * lastIndex) / (maxChunks - 1)).toInt() }
            .distinct()
            .map(chunks::get)
    }

    fun isBroadSourceRequest(query: String): Boolean {
        val q = normalize(query)
        return BROAD_HINTS.any { it in q }
    }

    private fun splitSegment(
        segment: String,
        pageNumber: Int?,
        absoluteStart: Int,
        chunkChars: Int,
        overlapChars: Int
    ): List<Chunk> {
        val out = mutableListOf<Chunk>()
        var start = 0
        while (start < segment.length) {
            var end = min(segment.length, start + chunkChars)
            if (end < segment.length) {
                val minimumBreak = start + (chunkChars * 0.58).toInt()
                val newline = segment.lastIndexOf('\n', startIndex = end - 1)
                val period = segment.lastIndexOf('.', startIndex = end - 1)
                val candidate = maxOf(newline, period.takeIf { it >= 0 }?.plus(1) ?: -1)
                if (candidate >= minimumBreak) end = candidate
            }
            if (end <= start) end = min(segment.length, start + chunkChars)

            val raw = segment.substring(start, end)
            val clean = raw
                .replace(Regex("[\\t ]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
            if (clean.isNotBlank()) {
                out += Chunk(
                    text = clean,
                    pageNumber = pageNumber,
                    offset = absoluteStart + start
                )
            }
            if (end >= segment.length) break
            val next = (end - overlapChars).coerceAtLeast(start + 1)
            start = next
        }
        return out
    }

    private fun score(
        normalizedText: String,
        normalizedQuery: String,
        terms: List<String>,
        normalizedSourceName: String
    ): Double {
        var score = 0.0
        if (normalizedQuery.length >= 4 && normalizedText.contains(normalizedQuery)) score += 16.0

        var uniqueHits = 0
        terms.forEach { term ->
            val occurrences = countOccurrences(normalizedText, term).coerceAtMost(6)
            if (occurrences > 0) {
                uniqueHits += 1
                score += 2.2 + occurrences * 1.15
            }
            if (normalizedSourceName.contains(term)) score += 1.2
        }
        if (terms.isNotEmpty()) {
            val coverage = uniqueHits.toDouble() / terms.size.toDouble()
            score += coverage * 8.0
            if (coverage >= 0.75 && uniqueHits >= 2) score += 3.0
        }
        return score
    }

    private fun meaningfulTerms(query: String): List<String> = tokenRegex.findAll(normalize(query))
        .map { it.value }
        .filterNot { it in STOP_WORDS }
        .filter { it.length >= 3 || it.any(Char::isDigit) }
        .distinct()
        .take(18)
        .toList()

    private fun countOccurrences(text: String, term: String): Int {
        var count = 0
        var start = 0
        while (start < text.length) {
            val found = text.indexOf(term, startIndex = start)
            if (found < 0) break
            count += 1
            start = found + term.length
        }
        return count
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val BROAD_HINTS = listOf(
        "resume el documento", "resume los documentos", "resumen del documento", "resumen de las fuentes",
        "resume mis fuentes", "resume mis archivos", "todo el documento", "todos los documentos",
        "contenido del documento", "contenido de las fuentes", "ideas principales del documento",
        "ideas principales de las fuentes", "explicame el documento", "explica el documento"
    )

    private val STOP_WORDS = setOf(
        "que", "qué", "como", "cómo", "cual", "cuál", "cuales", "cuáles", "quien", "quién",
        "para", "por", "con", "sin", "del", "las", "los", "una", "uno", "unos", "unas", "este",
        "esta", "estos", "estas", "eso", "esa", "sobre", "entre", "desde", "hasta", "muy", "mas",
        "más", "tambien", "también", "puede", "puedes", "quiero", "necesito", "haz", "dime", "explica",
        "the", "and", "for", "with", "from", "what", "how", "this", "that", "are", "was", "were"
    ).map(::normalize).toSet()
}
