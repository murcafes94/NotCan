package com.notcan.app.ui.maps

import java.util.UUID

enum class StudyMapType { MIND_MAP, CONCEPT_MAP }
enum class StudyMapLayoutStyle { RADIAL, TREE, CONSTELLATION }

data class StudyMap(
    val id: String,
    val title: String,
    val type: StudyMapType,
    val rootNodeId: String,
    val nodes: List<StudyMapNode>,
    val edges: List<StudyMapEdge>
)

data class StudyMapNode(
    val id: String,
    val title: String,
    val description: String? = null,
    val level: Int = 0,
    val sourceRefs: List<String> = emptyList()
)

data class StudyMapEdge(
    val from: String,
    val to: String,
    val label: String? = null
)

data class PositionedStudyMapNode(
    val node: StudyMapNode,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

object StudyMapGenerator {
    fun buildFromText(title: String, rawText: String, type: StudyMapType): StudyMap {
        val rootId = UUID.randomUUID().toString()
        val nodes = mutableListOf(
            StudyMapNode(rootId, title.ifBlank { "Mapa de estudio" }, level = 0)
        )
        val edges = mutableListOf<StudyMapEdge>()

        val concepts = extractConcepts(rawText)
        concepts.forEachIndexed { index, concept ->
            val childId = UUID.randomUUID().toString()
            nodes += StudyMapNode(
                id = childId,
                title = concept.title,
                description = concept.description,
                level = 1
            )
            edges += StudyMapEdge(
                from = rootId,
                to = childId,
                label = if (type == StudyMapType.CONCEPT_MAP) "incluye" else null
            )

            if (type == StudyMapType.CONCEPT_MAP) {
                concept.details.take(3).forEach { detail ->
                    val detailId = UUID.randomUUID().toString()
                    nodes += StudyMapNode(detailId, detail, level = 2)
                    edges += StudyMapEdge(childId, detailId, "se relaciona con")
                }
            }
        }

        if (concepts.isEmpty()) {
            val childId = UUID.randomUUID().toString()
            nodes += StudyMapNode(childId, "Añade apuntes o una transcripción para generar el mapa", level = 1)
            edges += StudyMapEdge(rootId, childId)
        }

        return StudyMap(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Mapa de estudio" },
            type = type,
            rootNodeId = rootId,
            nodes = nodes,
            edges = edges
        )
    }

    private data class ExtractedConcept(
        val title: String,
        val description: String?,
        val details: List<String>
    )

    private fun extractConcepts(text: String): List<ExtractedConcept> {
        val normalized = text
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return emptyList()

        val sentences = normalized
            .split(Regex("(?<=[.!?])\\s+|[•\\n]+"))
            .map { it.trim(' ', '-', '•', '\t') }
            .filter { it.length >= 18 }
            .distinct()
            .take(9)

        return sentences.map { sentence ->
            val chunks = sentence.split(',', ';', ':').map { it.trim() }.filter { it.length >= 4 }
            val head = chunks.firstOrNull().orEmpty()
            val title = summarizeTitle(head.ifBlank { sentence })
            val description = sentence.takeIf { it != title }?.take(180)
            ExtractedConcept(title, description, chunks.drop(1))
        }.distinctBy { it.title.lowercase() }
    }

    private fun summarizeTitle(value: String): String {
        val words = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(9).joinToString(" ").take(74).ifBlank { "Concepto" }
    }
}
