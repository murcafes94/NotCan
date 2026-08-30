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
    private const val MAX_NODES = 16

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
        val title = compact(root.optString("title").ifBlank { "Mapa de estudio" }, 54)
        val nodesArray = root.getJSONArray("nodes")
        val allNodes = mutableListOf<StudyMapNode>()
        for (i in 0 until nodesArray.length()) {
            val item = nodesArray.getJSONObject(i)
            val level = item.optInt("level", if (i == 0) 0 else 1).coerceIn(0, 3)
            allNodes += StudyMapNode(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = compact(item.optString("title").ifBlank { "Concepto" }, if (level == 0) 48 else 34),
                description = item.optString("description")
                    .takeIf { it.isNotBlank() }
                    ?.let { compact(it, 150) },
                level = level,
                sourceRefs = buildList {
                    val refs = item.optJSONArray("source_refs")
                    if (refs != null) for (j in 0 until refs.length()) add(compact(refs.optString(j), 28))
                }.filter { it.isNotBlank() }.distinct().take(3)
            )
        }
        require(allNodes.isNotEmpty()) { "El mapa no contiene nodos" }

        val requestedRootId = root.optString("root_node_id")
        val rootNode = allNodes.firstOrNull { it.id == requestedRootId } ?: allNodes.first()
        val nodes = buildList {
            add(rootNode)
            addAll(
                allNodes
                    .asSequence()
                    .filterNot { it.id == rootNode.id }
                    .sortedBy { it.level }
                    .take(MAX_NODES - 1)
                    .toList()
            )
        }
        val allowedIds = nodes.mapTo(mutableSetOf()) { it.id }

        val edges = mutableListOf<StudyMapEdge>()
        val edgesArray = root.optJSONArray("edges")
        if (edgesArray != null) {
            for (i in 0 until edgesArray.length()) {
                val item = edgesArray.getJSONObject(i)
                val from = item.optString("from")
                val to = item.optString("to")
                if (from !in allowedIds || to !in allowedIds || from == to) continue
                edges += StudyMapEdge(
                    from = from,
                    to = to,
                    label = item.optString("label")
                        .takeIf { it.isNotBlank() }
                        ?.let { compact(it, 22) }
                )
            }
        }

        return ParsedStudyMapArtifact(
            map = StudyMap(
                id = UUID.randomUUID().toString(),
                title = title,
                type = type,
                rootNodeId = rootNode.id,
                nodes = nodes,
                edges = edges.distinctBy { Triple(it.from, it.to, it.label) }
            ),
            preferredLayout = preferredLayout
        )
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
