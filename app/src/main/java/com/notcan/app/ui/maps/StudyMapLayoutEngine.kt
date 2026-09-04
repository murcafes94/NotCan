package com.notcan.app.ui.maps

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StudyMapLayoutEngine {
    private fun estimatedNodeHeight(node: StudyMapNode, base: Float, width: Float = 210f): Float {
        // Map cards are intentionally summaries. The full node text remains in the artifact,
        // while the canvas reserves a bounded number of lines so one verbose node cannot
        // collapse an entire layout into a tall overlapping column.
        val usableWidth = (width - 34f).coerceAtLeast(120f)
        val titleCharsPerLine = (usableWidth / 10.2f).toInt().coerceIn(13, 30)
        val bodyCharsPerLine = (usableWidth / 8.0f).toInt().coerceIn(17, 36)
        val titleLines = ceil(node.title.length.coerceAtLeast(1) / titleCharsPerLine.toFloat())
            .toInt().coerceIn(1, 4)
        val descriptionLines = node.description?.takeIf { it.isNotBlank() }
            ?.let { ceil(it.length / bodyCharsPerLine.toFloat()).toInt().coerceIn(1, 6) } ?: 0
        val sourceLines = if (node.sourceRefs.isNotEmpty()) 1 else 0
        val measured = 30f + titleLines * 27f + descriptionLines * 21f + sourceLines * 22f
        return max(base, measured + 16f)
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
        val root = byId[map.rootNodeId] ?: return emptyList()
        val result = mutableListOf<PositionedStudyMapNode>()
        val verticalGap = 54f
        val horizontalGap = 84f
        val rootWidth = 270f
        val rootHeight = estimatedNodeHeight(root, 112f, rootWidth)
        val subtreeCache = mutableMapOf<String, Float>()

        fun cardWidth(depth: Int): Float = when {
            depth == 0 -> rootWidth
            depth == 1 -> 250f
            depth == 2 -> 225f
            else -> 205f
        }

        fun cardHeight(id: String, depth: Int): Float {
            val node = byId[id] ?: return 90f
            return estimatedNodeHeight(node, if (depth <= 1) 104f else 90f, cardWidth(depth))
        }

        fun subtreeHeight(id: String, depth: Int, seen: Set<String> = emptySet()): Float {
            subtreeCache[id]?.let { return it }
            if (id in seen) return cardHeight(id, depth)
            val childIds = children[id].orEmpty().filterNot { it in seen }
            val own = cardHeight(id, depth)
            val descendants = if (childIds.isEmpty()) 0f else {
                childIds.sumOf { subtreeHeight(it, depth + 1, seen + id).toDouble() }.toFloat() +
                    verticalGap * (childIds.size - 1).coerceAtLeast(0)
            }
            return max(own, descendants).also { subtreeCache[id] = it }
        }

        val totalHeight = subtreeHeight(map.rootNodeId, 0)
        val contentTop = max(54f, (height - totalHeight) / 2f)
        val rootY = contentTop + totalHeight / 2f - rootHeight / 2f
        val rootX = 64f
        result += PositionedStudyMapNode(root, rootX, rootY, rootWidth, rootHeight)

        fun placeChildren(parentId: String, depth: Int, left: Float, top: Float, seen: Set<String>) {
            val childIds = children[parentId].orEmpty().filterNot { it in seen }
            if (childIds.isEmpty()) return
            var cursor = top
            childIds.forEach { childId ->
                val node = byId[childId] ?: return@forEach
                val branchHeight = subtreeHeight(childId, depth, seen + parentId)
                val w = cardWidth(depth)
                val h = cardHeight(childId, depth)
                val y = cursor + branchHeight / 2f - h / 2f
                result += PositionedStudyMapNode(node, left, y, w, h)
                placeChildren(childId, depth + 1, left + w + horizontalGap, cursor, seen + parentId + childId)
                cursor += branchHeight + verticalGap
            }
        }

        placeChildren(map.rootNodeId, 1, rootX + rootWidth + horizontalGap, contentTop, setOf(map.rootNodeId))
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
        val root = byId[map.rootNodeId] ?: return emptyList()
        val result = mutableListOf<PositionedStudyMapNode>()
        val horizontalGap = 58f
        val verticalGap = 118f
        val leafWidth = 245f
        val subtreeWidthCache = mutableMapOf<String, Float>()

        fun nodeWidth(depth: Int): Float = if (depth == 0) 270f else if (depth == 1) 235f else 210f
        fun nodeHeight(id: String, depth: Int): Float {
            val node = byId[id] ?: return 92f
            return estimatedNodeHeight(node, if (depth == 0) 112f else 94f, nodeWidth(depth))
        }

        fun subtreeWidth(id: String, depth: Int, seen: Set<String> = emptySet()): Float {
            subtreeWidthCache[id]?.let { return it }
            if (id in seen) return nodeWidth(depth)
            val childIds = children[id].orEmpty().filterNot { it in seen }
            val own = nodeWidth(depth)
            val descendants = if (childIds.isEmpty()) leafWidth else {
                childIds.sumOf { subtreeWidth(it, depth + 1, seen + id).toDouble() }.toFloat() +
                    horizontalGap * (childIds.size - 1).coerceAtLeast(0)
            }
            return max(own, descendants).also { subtreeWidthCache[id] = it }
        }

        val levels = collectLevels(map.rootNodeId, children)
        val maxHeightByDepth = levels.mapValues { (depth, ids) -> ids.maxOfOrNull { nodeHeight(it, depth) } ?: 96f }
        val yByDepth = mutableMapOf<Int, Float>()
        var yCursor = 56f
        for (depth in 0..(levels.keys.maxOrNull() ?: 0)) {
            yByDepth[depth] = yCursor
            yCursor += (maxHeightByDepth[depth] ?: 96f) + verticalGap
        }

        val totalWidth = subtreeWidth(map.rootNodeId, 0)
        val contentLeft = max(54f, (width - totalWidth) / 2f)

        fun place(id: String, depth: Int, left: Float, seen: Set<String>) {
            val node = byId[id] ?: return
            if (id in seen) return
            val branchWidth = subtreeWidth(id, depth, seen)
            val w = nodeWidth(depth)
            val h = nodeHeight(id, depth)
            val x = left + branchWidth / 2f - w / 2f
            val y = yByDepth[depth] ?: 56f
            result += PositionedStudyMapNode(node, x, y, w, h)

            val childIds = children[id].orEmpty().filterNot { it in seen }
            var childLeft = left
            childIds.forEach { childId ->
                val cw = subtreeWidth(childId, depth + 1, seen + id)
                place(childId, depth + 1, childLeft, seen + id)
                childLeft += cw + horizontalGap
            }
        }

        place(root.id, 0, contentLeft, emptySet())
        return result
    }

    private fun constellation(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        val result = mutableListOf<PositionedStudyMapNode>()
        val placed = mutableSetOf<String>()

        val rootWidth = 270f
        val rootHeight = estimatedNodeHeight(root, 132f, rootWidth)
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)
        placed += root.id

        // A constellation is made of irregular branch clusters, not concentric rings. Each main
        // branch gets its own sector and descendants fan out around that sector.
        val firstLevel = children[map.rootNodeId].orEmpty()
        firstLevel.forEachIndexed { branchIndex, branchId ->
            val branch = byId[branchId] ?: return@forEachIndexed
            if (!placed.add(branchId)) return@forEachIndexed
            val baseAngle = (2.0 * PI / max(firstLevel.size, 1)) * branchIndex - PI / 2.0
            val skew = if (branchIndex % 2 == 0) 0.18 else -0.14
            val angle = baseAngle + skew
            val branchRadius = 500f + (branchIndex % 3) * 115f
            val bx = centerX + branchRadius * cos(angle).toFloat()
            val by = centerY + branchRadius * sin(angle).toFloat()
            val branchWidth = 235f
            val branchHeight = estimatedNodeHeight(branch, 112f, branchWidth)
            result += PositionedStudyMapNode(branch, bx - branchWidth / 2f, by - branchHeight / 2f, branchWidth, branchHeight)

            val queue = ArrayDeque<Pair<String, Int>>()
            children[branchId].orEmpty().forEach { queue.addLast(it to 1) }
            var localIndex = 0
            while (queue.isNotEmpty()) {
                val (childId, localDepth) = queue.removeFirst()
                if (!placed.add(childId)) continue
                val child = byId[childId] ?: continue
                val fanSlot = (localIndex % 5) - 2
                val fanAngle = angle + fanSlot * 0.24 + (localDepth - 1) * 0.055
                val ring = localIndex / 5
                val childRadius = branchRadius + 330f + localDepth * 125f + ring * 190f
                val cx = centerX + childRadius * cos(fanAngle).toFloat()
                val cy = centerY + childRadius * sin(fanAngle).toFloat()
                val childWidth = if (localDepth == 1) 215f else 195f
                val childHeight = estimatedNodeHeight(child, if (localDepth == 1) 96f else 84f, childWidth)
                result += PositionedStudyMapNode(child, cx - childWidth / 2f, cy - childHeight / 2f, childWidth, childHeight)
                localIndex++
                children[childId].orEmpty().forEach { queue.addLast(it to (localDepth + 1)) }
            }
        }

        // Keep disconnected/orphan nodes visible on an outer irregular ring.
        map.nodes.filterNot { it.id in placed }.forEachIndexed { index, node ->
            val angle = index * 2.399963229728653 + 0.35
            val radius = 820f + (index % 4) * 125f
            val cx = centerX + radius * cos(angle).toFloat()
            val cy = centerY + radius * sin(angle).toFloat()
            val cardWidth = 195f
            val cardHeight = estimatedNodeHeight(node, 84f, cardWidth)
            result += PositionedStudyMapNode(node, cx - cardWidth / 2f, cy - cardHeight / 2f, cardWidth, cardHeight)
        }

        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 86f, iterations = 130)
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
