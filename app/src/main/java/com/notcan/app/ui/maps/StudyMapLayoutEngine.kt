package com.notcan.app.ui.maps

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StudyMapLayoutEngine {
    private fun estimatedNodeHeight(node: StudyMapNode, base: Float): Float {
        val chars = node.title.length + (node.description?.length ?: 0)
        // El mapa admite pan/zoom, así que el nodo crece todo lo necesario en vez de truncar.
        val estimatedLines = (chars / 30).coerceAtLeast(0)
        val extra = estimatedLines * 17f
        val sourceExtra = if (node.sourceRefs.isNotEmpty()) 22f else 0f
        return (base + extra + sourceExtra).coerceAtLeast(base)
    }
    fun layout(
        map: StudyMap,
        style: StudyMapLayoutStyle,
        canvasWidth: Float,
        canvasHeight: Float
    ): List<PositionedStudyMapNode> = when (style) {
        StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> horizontalBranches(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.RADIAL -> radial(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.RADIAL_CARDS -> radialCards(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.IDEA_BOARD -> ideaBoard(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.TREE -> tree(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.CONSTELLATION -> constellation(map, canvasWidth, canvasHeight)
    }

    private fun horizontalBranches(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val maxDepth = depthOf(map.rootNodeId, children).coerceAtLeast(1)
        val rootWidth = 240f
        val rootHeight = estimatedNodeHeight(root, 96f)
        val horizontalStep = ((width - rootWidth - 140f) / maxDepth).coerceIn(235f, 315f)
        val estimatedContentWidth = rootWidth + 52f + maxDepth * horizontalStep + 230f
        val rootX = ((width - estimatedContentWidth) / 2f).coerceAtLeast(40f)
        val rootY = (height / 2f) - rootHeight / 2f
        result += PositionedStudyMapNode(root, rootX, rootY, rootWidth, rootHeight)

        val leafCountCache = mutableMapOf<String, Int>()
        fun leafCount(id: String, visiting: MutableSet<String> = mutableSetOf()): Int {
            leafCountCache[id]?.let { return it }
            if (!visiting.add(id)) return 1
            val childIds = children[id].orEmpty()
            val count = if (childIds.isEmpty()) 1 else childIds.sumOf { leafCount(it, visiting.toMutableSet()) }
            return count.coerceAtLeast(1).also { leafCountCache[id] = it }
        }

        val top = 48f
        val bottom = (height - 48f).coerceAtLeast(top + 300f)

        fun placeChildren(parentId: String, depth: Int, bandTop: Float, bandBottom: Float, visiting: Set<String>) {
            val childIds = children[parentId].orEmpty().filterNot { it in visiting }
            if (childIds.isEmpty()) return
            val totalWeight = childIds.sumOf { leafCount(it) }.coerceAtLeast(1)
            var cursor = bandTop

            childIds.forEach { childId ->
                val node = byId[childId] ?: return@forEach
                val weight = leafCount(childId)
                val fraction = weight.toFloat() / totalWeight.toFloat()
                val bandHeight = (bandBottom - bandTop) * fraction
                val centerY = cursor + bandHeight / 2f
                val cardWidth = when {
                    depth <= 1 -> 230f
                    depth == 2 -> 210f
                    else -> 190f
                }
                val cardHeight = estimatedNodeHeight(node, when {
                    depth <= 1 -> 92f
                    depth == 2 -> 82f
                    else -> 74f
                })
                val x = rootX + rootWidth + 52f + (depth - 1) * horizontalStep
                val y = (centerY - cardHeight / 2f).coerceAtLeast(18f)

                result += PositionedStudyMapNode(node, x, y, cardWidth, cardHeight)
                placeChildren(
                    parentId = childId,
                    depth = depth + 1,
                    bandTop = cursor,
                    bandBottom = cursor + bandHeight,
                    visiting = visiting + childId
                )
                cursor += bandHeight
            }
        }

        placeChildren(map.rootNodeId, 1, top, bottom, setOf(map.rootNodeId))
        return result
    }

    private fun radial(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val levels = collectLevels(map.rootNodeId, children)
        val result = mutableListOf<PositionedStudyMapNode>()
        val centerX = width / 2f
        val centerY = height / 2f

        levels.forEach { (depth, ids) ->
            if (depth == 0) {
                val root = byId[ids.firstOrNull()] ?: return@forEach
                result += PositionedStudyMapNode(root, centerX - 115f, centerY - estimatedNodeHeight(root, 96f)/2f, 230f, estimatedNodeHeight(root, 96f))
            } else {
                val radius = 220f + (depth - 1) * 225f
                ids.forEachIndexed { index, id ->
                    val node = byId[id] ?: return@forEachIndexed
                    val angle = (2.0 * PI / max(ids.size, 1)) * index - PI / 2.0
                    val x = centerX + radius * cos(angle).toFloat()
                    val y = centerY + radius * sin(angle).toFloat()
                    val cardWidth = if (depth == 1) 210f else 185f
                    val cardHeight = estimatedNodeHeight(node, if (depth == 1) 90f else 78f)
                    result += PositionedStudyMapNode(node, x - cardWidth / 2f, y - cardHeight / 2f, cardWidth, cardHeight)
                }
            }
        }
        return result
    }

    private fun radialCards(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        val rootWidth = 240f
        val rootHeight = estimatedNodeHeight(root, 108f)
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty()
        val radiusX = (width * 0.35f).coerceIn(300f, 520f)
        val radiusY = (height * 0.35f).coerceIn(230f, 370f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = (2.0 * PI / max(firstLevel.size, 1)) * index - PI / 2.0
            val centerNodeX = centerX + radiusX * cos(angle).toFloat()
            val centerNodeY = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 225f
            val cardHeight = estimatedNodeHeight(node, 124f)
            result += PositionedStudyMapNode(node, centerNodeX - cardWidth / 2f, centerNodeY - cardHeight / 2f, cardWidth, cardHeight)

            val grandchildren = children[id].orEmpty().take(5)
            val outwardX = cos(angle).toFloat()
            val outwardY = sin(angle).toFloat()
            val tangentX = -outwardY
            val tangentY = outwardX
            val childBaseX = centerNodeX + outwardX * 200f
            val childBaseY = centerNodeY + outwardY * 150f
            val spacing = 96f
            val offsetStart = -(grandchildren.size - 1) * spacing / 2f
            grandchildren.forEachIndexed { childIndex, childId ->
                val child = byId[childId] ?: return@forEachIndexed
                val offset = offsetStart + childIndex * spacing
                val childCenterX = childBaseX + tangentX * offset
                val childCenterY = childBaseY + tangentY * offset
                result += PositionedStudyMapNode(child, childCenterX - 90f, childCenterY - estimatedNodeHeight(child, 80f)/2f, 180f, estimatedNodeHeight(child, 80f))
            }
        }
        return result
    }

    private fun ideaBoard(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        result += PositionedStudyMapNode(root, centerX - 125f, centerY - estimatedNodeHeight(root, 130f)/2f, 250f, estimatedNodeHeight(root, 130f))

        val firstLevel = children[map.rootNodeId].orEmpty().take(8)
        val slots = listOf(-PI / 2.0, -PI / 6.0, PI / 6.0, PI / 2.0, 5.0 * PI / 6.0, -5.0 * PI / 6.0, 0.0, PI)
        val radiusX = (width * 0.35f).coerceIn(320f, 540f)
        val radiusY = (height * 0.35f).coerceIn(250f, 390f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = slots[index % slots.size]
            val cx = centerX + radiusX * cos(angle).toFloat()
            val cy = centerY + radiusY * sin(angle).toFloat()
            result += PositionedStudyMapNode(node, cx - 120f, cy - estimatedNodeHeight(node, 144f)/2f, 240f, estimatedNodeHeight(node, 144f))

            val details = children[id].orEmpty().take(3)
            details.forEachIndexed { detailIndex, detailId ->
                val detail = byId[detailId] ?: return@forEachIndexed
                val outwardX = cos(angle).toFloat()
                val outwardY = sin(angle).toFloat()
                val tangentX = -outwardY
                val tangentY = outwardX
                val spread = (detailIndex - (details.size - 1) / 2f) * 82f
                val detailCx = cx + outwardX * 185f + tangentX * spread
                val detailCy = cy + outwardY * 145f + tangentY * spread
                result += PositionedStudyMapNode(detail, detailCx - 90f, detailCy - estimatedNodeHeight(detail, 80f)/2f, 180f, estimatedNodeHeight(detail, 80f))
            }
        }
        return result
    }

    private fun tree(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val levels = collectLevels(map.rootNodeId, children)
        val result = mutableListOf<PositionedStudyMapNode>()
        val usableHeight = (height - 100f).coerceAtLeast(340f)
        val maxDepth = (levels.keys.maxOrNull() ?: 0).coerceAtLeast(1)
        val horizontalStep = ((width - 300f).coerceAtLeast(300f)) / maxDepth

        levels.forEach { (depth, ids) ->
            val verticalStep = usableHeight / (ids.size + 1)
            ids.forEachIndexed { index, id ->
                val node = byId[id] ?: return@forEachIndexed
                val cardWidth = if (depth == 0) 230f else 205f
                val cardHeight = estimatedNodeHeight(node, if (depth == 0) 96f else 84f)
                result += PositionedStudyMapNode(
                    node = node,
                    x = 50f + depth * horizontalStep,
                    y = 36f + verticalStep * (index + 1) - cardHeight / 2f,
                    width = cardWidth,
                    height = cardHeight
                )
            }
        }
        return result
    }

    private fun constellation(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val radial = radial(map, width, height)
        return radial.mapIndexed { index, positioned ->
            if (positioned.node.id == map.rootNodeId) positioned
            else positioned.copy(
                x = positioned.x + ((((index * 37) % 33) - 16) * 5f),
                y = positioned.y + ((((index * 53) % 29) - 14) * 4f)
            )
        }
    }

    private fun depthOf(rootId: String, children: Map<String, List<String>>): Int {
        fun visit(id: String, seen: Set<String>): Int {
            if (id in seen) return 0
            val next = children[id].orEmpty()
            if (next.isEmpty()) return 0
            return 1 + (next.maxOfOrNull { visit(it, seen + id) } ?: 0)
        }
        return visit(rootId, emptySet())
    }

    private fun collectLevels(rootId: String, children: Map<String, List<String>>): Map<Int, List<String>> {
        val result = linkedMapOf<Int, MutableList<String>>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()
        queue.addLast(rootId to 0)
        while (queue.isNotEmpty()) {
            val (id, depth) = queue.removeFirst()
            if (!visited.add(id)) continue
            result.getOrPut(depth) { mutableListOf() }.add(id)
            children[id].orEmpty().forEach { queue.addLast(it to depth + 1) }
        }
        return result
    }
}
