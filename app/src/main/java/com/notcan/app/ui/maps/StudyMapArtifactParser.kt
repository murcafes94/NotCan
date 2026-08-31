package com.notcan.app.ui.maps

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
    private const val MAX_NODES = 16

    fun parse(value: String): ParsedStudyMapArtifact? {
        val artifact = extractArtifact(value) ?: return null
        return runCatching { parseJson(JSONObject(artifact.json)) }.getOrNull()
    }

    fun stripArtifact(value: String): String {
        val artifact = extractArtifact(value) ?: return value
        return (value.substring(0, artifact.start) + value.substring(artifact.endExclusive))
            .replace("```json", "")
            .replace("```", "")
            .trim()
            .ifBlank { "Preparé el mapa. Puedes abrirlo, explorarlo y compartirlo." }
    }

    private fun extractArtifact(value: String): MapArtifactSlice? {
        val markerMatch = START_MARKERS
            .mapNotNull { marker -> value.indexOf(marker).takeIf { it >= 0 }?.let { Triple(it, marker.length, marker) } }
            .minByOrNull { it.first }
            ?: return null
        val markerStart = markerMatch.first
        val contentStart = markerStart + markerMatch.second

        val explicitEnd = END_MARKERS
            .mapNotNull { marker -> value.indexOf(marker, contentStart).takeIf { it >= 0 }?.let { it to marker.length } }
            .minByOrNull { it.first }
        val scanLimit = explicitEnd?.first ?: value.length
        val jsonStart = value.indexOf('{', contentStart).takeIf { it >= contentStart && it < scanLimit } ?: return null
        val jsonEnd = balancedObjectEnd(value, jsonStart, scanLimit)
            ?: explicitEnd?.first
            ?: return null
        val json = value.substring(jsonStart, jsonEnd).trim()
        if (json.isBlank()) return null

        val artifactEnd = explicitEnd
            ?.takeIf { it.first >= jsonEnd }
            ?.let { it.first + it.second }
            ?: jsonEnd
        return MapArtifactSlice(json = json, start = markerStart, endExclusive = artifactEnd)
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

    private fun parseJson(root: JSONObject): ParsedStudyMapArtifact {
        val type = when (root.flexString("type").lowercase()) {
            "concept_map", "conceptual", "concept" -> StudyMapType.CONCEPT_MAP
            else -> StudyMapType.MIND_MAP
        }
        val preferredLayout = when (root.flexString("layout").lowercase()) {
            "radial" -> StudyMapLayoutStyle.RADIAL
            "radial_cards", "cards", "tarjetas", "visual_cards" -> StudyMapLayoutStyle.RADIAL_CARDS
            "ideas", "idea_board", "mapa_de_ideas", "visual", "creative" -> StudyMapLayoutStyle.IDEA_BOARD
            "tree", "árbol", "arbol" -> StudyMapLayoutStyle.TREE
            "constellation", "constelacion", "constelación" -> StudyMapLayoutStyle.CONSTELLATION
            else -> StudyMapLayoutStyle.HORIZONTAL_BRANCHES
        }
        val title = compact(root.flexString("title").ifBlank { "Mapa de estudio" }, 54)
        val nodesArray = root.flexArray("nodes") ?: error("El mapa no contiene nodos")
        val allNodes = mutableListOf<StudyMapNode>()
        for (i in 0 until nodesArray.length()) {
            val item = nodesArray.optJSONObject(i) ?: continue
            val level = item.optInt("level", if (i == 0) 0 else 1).coerceIn(0, 3)
            allNodes += StudyMapNode(
                id = item.flexString("id").ifBlank { UUID.randomUUID().toString() },
                title = compact(item.flexString("title").ifBlank { "Concepto" }, if (level == 0) 48 else 34),
                description = item.flexString("description")
                    .takeIf { it.isNotBlank() }
                    ?.let { compact(it, 150) },
                level = level,
                sourceRefs = buildList {
                    val refs = item.flexArray("source_refs", "sourceRefs", "sourcerefs")
                    if (refs != null) {
                        for (j in 0 until refs.length()) add(compact(refs.optString(j), 28))
                    } else {
                        item.flexString("source_ref", "sourceRef", "sourceref")
                            .takeIf { it.isNotBlank() }
                            ?.let { add(compact(it, 28)) }
                    }
                }.filter { it.isNotBlank() }.distinct().take(3)
            )
        }
        require(allNodes.isNotEmpty()) { "El mapa no contiene nodos" }

        val requestedRootId = root.flexString("root_node_id", "rootNodeId", "rootnodeid")
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
        val edgesArray = root.flexArray("edges")
        if (edgesArray != null) {
            for (i in 0 until edgesArray.length()) {
                val item = edgesArray.optJSONObject(i) ?: continue
                val from = item.flexString("from", "from_id", "fromId")
                val to = item.flexString("to", "to_id", "toId")
                if (from !in allowedIds || to !in allowedIds || from == to) continue
                edges += StudyMapEdge(
                    from = from,
                    to = to,
                    label = item.flexString("label")
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
