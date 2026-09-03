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
