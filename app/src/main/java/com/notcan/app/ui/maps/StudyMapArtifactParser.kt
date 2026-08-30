package com.notcan.app.ui.maps

import org.json.JSONObject
import java.util.UUID

internal data class ParsedStudyMapArtifact(
    val map: StudyMap,
    val preferredLayout: StudyMapLayoutStyle
)

internal object StudyMapArtifactParser {
    const val START_MARKER = "<<<NOTCAN_MAP>>>"
    const val END_MARKER = "<<<END_NOTCAN_MAP>>>"

    fun parse(value: String): ParsedStudyMapArtifact? {
        val start = value.indexOf(START_MARKER)
        val end = value.indexOf(END_MARKER)
        if (start < 0 || end <= start) return null
        val jsonText = value.substring(start + START_MARKER.length, end).trim()
        return runCatching { parseJson(JSONObject(jsonText)) }.getOrNull()
    }

    fun stripArtifact(value: String): String {
        val start = value.indexOf(START_MARKER)
        val end = value.indexOf(END_MARKER)
        if (start < 0 || end <= start) return value
        return (value.substring(0, start) + value.substring(end + END_MARKER.length))
            .trim()
            .ifBlank { "Preparé el mapa. Puedes abrirlo, explorarlo y compartirlo." }
    }

    private fun parseJson(root: JSONObject): ParsedStudyMapArtifact {
        val type = when (root.optString("type").lowercase()) {
            "concept_map", "conceptual", "concept" -> StudyMapType.CONCEPT_MAP
            else -> StudyMapType.MIND_MAP
        }
        val preferredLayout = when (root.optString("layout").lowercase()) {
            "radial" -> StudyMapLayoutStyle.RADIAL
            "radial_cards", "cards", "tarjetas", "visual_cards" -> StudyMapLayoutStyle.RADIAL_CARDS
            "ideas", "idea_board", "mapa_de_ideas", "visual", "creative" -> StudyMapLayoutStyle.IDEA_BOARD
            "tree", "árbol", "arbol" -> StudyMapLayoutStyle.TREE
            "constellation", "constelacion", "constelación" -> StudyMapLayoutStyle.CONSTELLATION
            else -> StudyMapLayoutStyle.HORIZONTAL_BRANCHES
        }
        val title = root.optString("title").ifBlank { "Mapa de estudio" }
        val nodesArray = root.getJSONArray("nodes")
        val nodes = mutableListOf<StudyMapNode>()
        for (i in 0 until nodesArray.length()) {
            val item = nodesArray.getJSONObject(i)
            nodes += StudyMapNode(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = item.optString("title").ifBlank { "Concepto" },
                description = item.optString("description").takeIf { it.isNotBlank() },
                level = item.optInt("level", if (i == 0) 0 else 1),
                sourceRefs = buildList {
                    val refs = item.optJSONArray("source_refs")
                    if (refs != null) for (j in 0 until refs.length()) add(refs.optString(j))
                }
            )
        }
        require(nodes.isNotEmpty()) { "El mapa no contiene nodos" }

        val edges = mutableListOf<StudyMapEdge>()
        val edgesArray = root.optJSONArray("edges")
        if (edgesArray != null) {
            for (i in 0 until edgesArray.length()) {
                val item = edgesArray.getJSONObject(i)
                val from = item.optString("from")
                val to = item.optString("to")
                if (from.isBlank() || to.isBlank()) continue
                edges += StudyMapEdge(
                    from = from,
                    to = to,
                    label = item.optString("label").takeIf { it.isNotBlank() }
                )
            }
        }

        val rootId = root.optString("root_node_id")
            .takeIf { id -> nodes.any { it.id == id } }
            ?: nodes.first().id

        return ParsedStudyMapArtifact(
            map = StudyMap(
                id = UUID.randomUUID().toString(),
                title = title,
                type = type,
                rootNodeId = rootId,
                nodes = nodes,
                edges = edges
            ),
            preferredLayout = preferredLayout
        )
    }
}
