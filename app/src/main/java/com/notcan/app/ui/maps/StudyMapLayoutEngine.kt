package com.notcan.app.ui.maps

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StudyMapLayoutEngine {
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
        val rootWidth = 205f
        val rootHeight = 86f
        val rootX = 54f
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

        val totalLeaves = children[map.rootNodeId].orEmpty().sumOf { leafCount(it) }.coerceAtLeast(1)
        val top = 38f
        val bottom = (height - 38f).coerceAtLeast(top + 260f)
        val availableHeight = bottom - top
        val maxDepth = depthOf(map.rootNodeId, children).coerceAtLeast(1)
        val horizontalStep = ((width - rootX - rootWidth - 80f) / maxDepth).coerceIn(205f, 285f)

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
                    depth <= 1 -> 190f
                    depth == 2 -> 168f
                    else -> 152f
                }
                val cardHeight = when {
                    depth <= 1 -> 76f
                    depth == 2 -> 68f
                    else -> 62f
                }
                val x = rootX + rootWidth + 42f + (depth - 1) * horizontalStep
                val y = centerY - cardHeight / 2f

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

        val rootChildren = children[map.rootNodeId].orEmpty()
        if (rootChildren.isNotEmpty()) {
            placeChildren(map.rootNodeId, 1, top, top + availableHeight * (totalLeaves.toFloat() / totalLeaves), setOf(map.rootNodeId))
        }
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
                result += PositionedStudyMapNode(root, centerX - 95f, centerY - 42f, 190f, 84f)
            } else {
                val radius = 190f + (depth - 1) * 190f
                ids.forEachIndexed { index, id ->
                    val node = byId[id] ?: return@forEachIndexed
                    val angle = (2.0 * PI / max(ids.size, 1)) * index - PI / 2.0
                    val x = centerX + radius * cos(angle).toFloat()
                    val y = centerY + radius * sin(angle).toFloat()
                    val cardWidth = if (depth == 1) 180f else 156f
                    val cardHeight = if (depth == 1) 78f else 68f
                    result += PositionedStudyMapNode(
                        node,
                        x - cardWidth / 2f,
                        y - cardHeight / 2f,
                        cardWidth,
                        cardHeight
                    )
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
        val rootWidth = 210f
        val rootHeight = 100f
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty()
        val radiusX = (width * 0.34f).coerceIn(280f, 480f)
        val radiusY = (height * 0.34f).coerceIn(210f, 340f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = (2.0 * PI / max(firstLevel.size, 1)) * index - PI / 2.0
            val centerNodeX = centerX + radiusX * cos(angle).toFloat()
            val centerNodeY = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 205f
            val cardHeight = 116f
            val x = centerNodeX - cardWidth / 2f
            val y = centerNodeY - cardHeight / 2f
            result += PositionedStudyMapNode(node, x, y, cardWidth, cardHeight)

            val grandchildren = children[id].orEmpty()
            if (grandchildren.isNotEmpty()) {
                val outwardX = cos(angle).toFloat()
                val outwardY = sin(angle).toFloat()
                val tangentX = -outwardY
                val tangentY = outwardX
                val childBaseX = centerNodeX + outwardX * 185f
                val childBaseY = centerNodeY + outwardY * 135f
                val spacing = 86f
                val offsetStart = -(grandchildren.size - 1) * spacing / 2f

                grandchildren.take(5).forEachIndexed { childIndex, childId ->
                    val child = byId[childId] ?: return@forEachIndexed
                    val offset = offsetStart + childIndex * spacing
                    val childCenterX = childBaseX + tangentX * offset
                    val childCenterY = childBaseY + tangentY * offset
                    val childWidth = 150f
                    val childHeight = 68f
                    result += PositionedStudyMapNode(
                        child,
                        childCenterX - childWidth / 2f,
                        childCenterY - childHeight / 2f,
                        childWidth,
                        childHeight
                    )
                }
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
        val rootWidth = 230f
        val rootHeight = 120f
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty().take(8)
        val slots = listOf(
            -PI / 2.0,
            -PI / 6.0,
            PI / 6.0,
            PI / 2.0,
            5.0 * PI / 6.0,
            -5.0 * PI / 6.0,
            0.0,
            PI
        )
        val radiusX = (width * 0.34f).coerceIn(300f, 500f)
        val radiusY = (height * 0.34f).coerceIn(230f, 360f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = slots[index % slots.size]
            val cx = centerX + radiusX * cos(angle).toFloat()
            val cy = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 220f
            val cardHeight = 132f
            result += PositionedStudyMapNode(
                node,
                cx - cardWidth / 2f,
                cy - cardHeight / 2f,
                cardWidth,
                cardHeight
            )

            val details = children[id].orEmpty().take(3)
            details.forEachIndexed { detailIndex, detailId ->
                val detail = byId[detailId] ?: return@forEachIndexed
                val outwardX = cos(angle).toFloat()
                val outwardY = sin(angle).toFloat()
                val tangentX = -outwardY
                val tangentY = outwardX
                val spread = (detailIndex - (details.size - 1) / 2f) * 72f
                val detailCx = cx + outwardX * 165f + tangentX * spread
                val detailCy = cy + outwardY * 125f + tangentY * spread
                val detailWidth = 148f
                val detailHeight = 64f
                result += PositionedStudyMapNode(
                    detail,
                    detailCx - detailWidth / 2f,
                    detailCy - detailHeight / 2f,
                    detailWidth,
                    detailHeight
                )
            }
        }
        return result
    }

    private fun tree(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val levels = collectLevels(map.rootNodeId, children)
        val result = mutableListOf<PositionedStudyMapNode>()
        val usableHeight = (height - 80f).coerceAtLeast(300f)
        val maxDepth = (levels.keys.maxOrNull() ?: 0).coerceAtLeast(1)
        val horizontalStep = ((width - 260f).coerceAtLeast(260f)) / maxDepth

        levels.forEach { (depth, ids) ->
            val verticalStep = usableHeight / (ids.size + 1)
            ids.forEachIndexed { index, id ->
                val node = byId[id] ?: return@forEachIndexed
                result += PositionedStudyMapNode(
                    node = node,
                    x = 40f + depth * horizontalStep,
                    y = 40f + verticalStep * (index + 1),
                    width = if (depth == 0) 190f else 170f,
                    height = if (depth == 0) 84f else 72f
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

    private fun collectLevels(
        rootId: String,
        children: Map<String, List<String>>
    ): Map<Int, List<String>> {
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
