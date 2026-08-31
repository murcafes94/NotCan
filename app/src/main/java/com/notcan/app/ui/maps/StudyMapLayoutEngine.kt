package com.notcan.app.ui.maps

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StudyMapLayoutEngine {
    private fun estimatedNodeHeight(node: StudyMapNode, base: Float, width: Float = 210f): Float {
        // Estimate the real rendered height from the available card width. Compose may grow a card
        // beyond heightIn(), so the layout engine must reserve that space before positioning siblings.
        val usableWidth = (width - 30f).coerceAtLeast(120f)
        val titleCharsPerLine = (usableWidth / 10.5f).toInt().coerceIn(13, 28)
        val bodyCharsPerLine = (usableWidth / 8.2f).toInt().coerceIn(16, 34)
        val titleLines = ceil(node.title.length.coerceAtLeast(1) / titleCharsPerLine.toFloat()).toInt().coerceAtLeast(1)
        val descriptionLines = node.description?.takeIf { it.isNotBlank() }
            ?.let { ceil(it.length / bodyCharsPerLine.toFloat()).toInt().coerceAtLeast(1) } ?: 0
        val sourceLines = if (node.sourceRefs.isNotEmpty()) 1 else 0
        val measured = 28f + titleLines * 27f + descriptionLines * 22f + sourceLines * 23f
        return max(base, measured + 14f)
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
        var previousRadius = 0f

        levels.toSortedMap().forEach { (depth, ids) ->
            if (depth == 0) {
                val root = byId[ids.firstOrNull()] ?: return@forEach
                val rootWidth = 250f
                val rootHeight = estimatedNodeHeight(root, 112f, rootWidth)
                result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)
            } else {
                val cardWidth = if (depth == 1) 230f else 205f
                val cardGap = if (depth == 1) 74f else 62f
                val countRadius = (ids.size * (cardWidth + cardGap) / (2f * PI.toFloat())).coerceAtLeast(0f)
                val depthRadius = if (depth == 1) 390f else previousRadius + 330f
                val radius = max(depthRadius, countRadius + 70f)
                previousRadius = radius
                ids.forEachIndexed { index, id ->
                    val node = byId[id] ?: return@forEachIndexed
                    val angle = (2.0 * PI / max(ids.size, 1)) * index - PI / 2.0
                    val x = centerX + radius * cos(angle).toFloat()
                    val y = centerY + radius * sin(angle).toFloat()
                    val cardHeight = estimatedNodeHeight(node, if (depth == 1) 100f else 88f, cardWidth)
                    result += PositionedStudyMapNode(node, x - cardWidth / 2f, y - cardHeight / 2f, cardWidth, cardHeight)
                }
            }
        }
        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 54f)
    }

    private fun radialCards(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        val rootWidth = 260f
        val rootHeight = estimatedNodeHeight(root, 120f, rootWidth)
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty()
        val radiusX = max(width * 0.34f, 520f)
        val radiusY = max(height * 0.31f, 450f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = (2.0 * PI / max(firstLevel.size, 1)) * index - PI / 2.0
            val centerNodeX = centerX + radiusX * cos(angle).toFloat()
            val centerNodeY = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 235f
            val cardHeight = estimatedNodeHeight(node, 126f, cardWidth)
            result += PositionedStudyMapNode(node, centerNodeX - cardWidth / 2f, centerNodeY - cardHeight / 2f, cardWidth, cardHeight)

            val grandchildren = children[id].orEmpty().take(5)
            val outwardX = cos(angle).toFloat()
            val outwardY = sin(angle).toFloat()
            val tangentX = -outwardY
            val tangentY = outwardX
            val childBaseX = centerNodeX + outwardX * 320f
            val childBaseY = centerNodeY + outwardY * 270f
            val spacing = 235f
            val offsetStart = -(grandchildren.size - 1) * spacing / 2f
            grandchildren.forEachIndexed { childIndex, childId ->
                val child = byId[childId] ?: return@forEachIndexed
                val childWidth = 205f
                val childHeight = estimatedNodeHeight(child, 90f, childWidth)
                val offset = offsetStart + childIndex * spacing
                val childCenterX = childBaseX + tangentX * offset
                val childCenterY = childBaseY + tangentY * offset
                result += PositionedStudyMapNode(child, childCenterX - childWidth / 2f, childCenterY - childHeight / 2f, childWidth, childHeight)
            }
        }
        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 58f)
    }

    private fun ideaBoard(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        val rootWidth = 270f
        val rootHeight = estimatedNodeHeight(root, 136f, rootWidth)
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty().take(8)
        val slots = listOf(-PI / 2.0, -PI / 6.0, PI / 6.0, PI / 2.0, 5.0 * PI / 6.0, -5.0 * PI / 6.0, 0.0, PI)
        val radiusX = max(width * 0.35f, 560f)
        val radiusY = max(height * 0.32f, 470f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = slots[index % slots.size]
            val cx = centerX + radiusX * cos(angle).toFloat()
            val cy = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 245f
            val cardHeight = estimatedNodeHeight(node, 148f, cardWidth)
            result += PositionedStudyMapNode(node, cx - cardWidth / 2f, cy - cardHeight / 2f, cardWidth, cardHeight)

            val details = children[id].orEmpty().take(3)
            val outwardX = cos(angle).toFloat()
            val outwardY = sin(angle).toFloat()
            val tangentX = -outwardY
            val tangentY = outwardX
            details.forEachIndexed { detailIndex, detailId ->
                val detail = byId[detailId] ?: return@forEachIndexed
                val detailWidth = 205f
                val detailHeight = estimatedNodeHeight(detail, 92f, detailWidth)
                val spread = (detailIndex - (details.size - 1) / 2f) * 235f
                val detailCx = cx + outwardX * 325f + tangentX * spread
                val detailCy = cy + outwardY * 285f + tangentY * spread
                result += PositionedStudyMapNode(detail, detailCx - detailWidth / 2f, detailCy - detailHeight / 2f, detailWidth, detailHeight)
            }
        }
        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 62f)
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
        val base = radial(map, width, height)
        val jittered = base.mapIndexed { index, positioned ->
            if (positioned.node.id == map.rootNodeId) positioned
            else positioned.copy(
                x = positioned.x + ((((index * 37) % 17) - 8) * 5f),
                y = positioned.y + ((((index * 53) % 15) - 7) * 5f)
            )
        }
        return resolveOverlaps(jittered, map.rootNodeId, width, height, minGap = 66f, iterations = 90)
    }

    private fun resolveOverlaps(
        source: List<PositionedStudyMapNode>,
        rootId: String,
        canvasWidth: Float,
        canvasHeight: Float,
        minGap: Float,
        iterations: Int = 70
    ): List<PositionedStudyMapNode> {
        if (source.size < 2) return source
        val nodes = source.toMutableList()
        repeat(iterations) {
            var moved = false
            for (i in 0 until nodes.lastIndex) {
                for (j in i + 1 until nodes.size) {
                    val a = nodes[i]
                    val b = nodes[j]
                    val acx = a.x + a.width / 2f
                    val acy = a.y + a.height / 2f
                    val bcx = b.x + b.width / 2f
                    val bcy = b.y + b.height / 2f
                    val overlapX = (a.width + b.width) / 2f + minGap - abs(acx - bcx)
                    val overlapY = (a.height + b.height) / 2f + minGap - abs(acy - bcy)
                    if (overlapX <= 0f || overlapY <= 0f) continue
                    moved = true
                    val aFixed = a.node.id == rootId
                    val bFixed = b.node.id == rootId
                    if (overlapX < overlapY) {
                        val direction = if (bcx >= acx) 1f else -1f
                        val push = overlapX + 2f
                        val aPush = if (aFixed) 0f else if (bFixed) push else push / 2f
                        val bPush = if (bFixed) 0f else if (aFixed) push else push / 2f
                        nodes[i] = a.copy(x = a.x - direction * aPush)
                        nodes[j] = b.copy(x = b.x + direction * bPush)
                    } else {
                        val direction = if (bcy >= acy) 1f else -1f
                        val push = overlapY + 2f
                        val aPush = if (aFixed) 0f else if (bFixed) push else push / 2f
                        val bPush = if (bFixed) 0f else if (aFixed) push else push / 2f
                        nodes[i] = a.copy(y = a.y - direction * aPush)
                        nodes[j] = b.copy(y = b.y + direction * bPush)
                    }
                }
            }
            for (index in nodes.indices) {
                val n = nodes[index]
                if (n.node.id == rootId) continue
                nodes[index] = n.copy(
                    x = n.x.coerceIn(24f, (canvasWidth - n.width - 24f).coerceAtLeast(24f)),
                    y = n.y.coerceIn(24f, (canvasHeight - n.height - 24f).coerceAtLeast(24f))
                )
            }
            if (!moved) return nodes
        }
        return nodes
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
