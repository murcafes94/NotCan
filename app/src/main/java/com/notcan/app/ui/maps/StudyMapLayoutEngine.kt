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
        StudyMapLayoutStyle.RADIAL -> radial(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.TREE -> tree(map, canvasWidth, canvasHeight)
        StudyMapLayoutStyle.CONSTELLATION -> constellation(map, canvasWidth, canvasHeight)
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
