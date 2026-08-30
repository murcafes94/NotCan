package com.notcan.app.ui.maps

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val branchColors = listOf(
    Color(0xFF3478F6),
    Color(0xFF9A6BE8),
    Color(0xFF49C8E8),
    Color(0xFF4FC38A),
    Color(0xFFE1B756)
)

@Composable
fun StudyMapScreen(
    map: StudyMap,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var layoutStyle by remember(map.id) {
        mutableStateOf(
            if (map.type == StudyMapType.MIND_MAP) StudyMapLayoutStyle.HORIZONTAL_BRANCHES
            else StudyMapLayoutStyle.TREE
        )
    }
    var zoom by remember(map.id) { mutableFloatStateOf(1f) }
    var panX by remember(map.id) { mutableFloatStateOf(0f) }
    var panY by remember(map.id) { mutableFloatStateOf(0f) }
    var viewportWidth by remember(map.id) { mutableFloatStateOf(1280f) }
    var viewportHeight by remember(map.id) { mutableFloatStateOf(800f) }
    var collapsedNodes by remember(map.id) { mutableStateOf(setOf<String>()) }

    val visibleMap = remember(map, collapsedNodes) { buildVisibleMap(map, collapsedNodes) }

    fun exportAndShare(format: StudyMapExportFormat, scope: StudyMapExportScope) {
        val sourceMap = if (scope == StudyMapExportScope.FULL_MAP) map else visibleMap
        val currentViewport = StudyMapViewport(
            widthPx = viewportWidth,
            heightPx = viewportHeight,
            zoom = zoom,
            panX = panX,
            panY = panY
        )
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    StudyMapExportManager.export(
                        context = context,
                        map = sourceMap,
                        layoutStyle = layoutStyle,
                        options = StudyMapExportOptions(
                            format = format,
                            scope = scope,
                            theme = StudyMapExportTheme.LIGHT,
                            includeTitle = true,
                            includeSources = true,
                            pdfLandscape = true
                        ),
                        viewport = currentViewport
                    )
                }
            }.onSuccess { file ->
                StudyMapExportManager.share(context, file, format)
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "No se pudo exportar el mapa", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        StudyMapToolbar(
            map = map,
            style = layoutStyle,
            onLayoutChange = {
                layoutStyle = it
                zoom = 1f
                panX = 0f
                panY = 0f
            },
            onExport = ::exportAndShare
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            LaunchedEffect(widthPx, heightPx) {
                viewportWidth = widthPx
                viewportHeight = heightPx
            }
            val nodes = remember(visibleMap, layoutStyle, widthPx, heightPx) {
                StudyMapLayoutEngine.layout(visibleMap, layoutStyle, widthPx, heightPx)
            }
            val positionedById = remember(nodes) { nodes.associateBy { it.node.id } }
            val directChildren = remember(visibleMap) {
                visibleMap.edges.groupBy { it.from }.mapValues { (_, edges) -> edges.map { it.to } }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(map.id, layoutStyle) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.45f, 3f)
                            panX += pan.x
                            panY += pan.y
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = panX
                            translationY = panY
                            scaleX = zoom
                            scaleY = zoom
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        visibleMap.edges.forEach { edge ->
                            val from = positionedById[edge.from] ?: return@forEach
                            val to = positionedById[edge.to] ?: return@forEach
                            val horizontal = layoutStyle == StudyMapLayoutStyle.HORIZONTAL_BRANCHES || layoutStyle == StudyMapLayoutStyle.TREE
                            val start = if (horizontal) {
                                Offset(from.x + from.width, from.y + from.height / 2f)
                            } else {
                                Offset(from.x + from.width / 2f, from.y + from.height / 2f)
                            }
                            val end = if (horizontal) {
                                Offset(to.x, to.y + to.height / 2f)
                            } else {
                                Offset(to.x + to.width / 2f, to.y + to.height / 2f)
                            }
                            val dx = end.x - start.x
                            val branchColor = branchColorFor(edge.to, visibleMap)
                            val path = Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(
                                    start.x + dx * 0.38f,
                                    start.y,
                                    start.x + dx * 0.62f,
                                    end.y,
                                    end.x,
                                    end.y
                                )
                            }
                            drawPath(
                                path = path,
                                color = branchColor.copy(alpha = 0.68f),
                                style = Stroke(width = if (horizontal) 4.5f else 3.5f)
                            )
                        }
                    }

                    nodes.forEach { positioned ->
                        val widthDp = with(density) { positioned.width.toDp() }
                        val minHeightDp = with(density) { positioned.height.toDp() }
                        val totalChildren = map.edges.count { it.from == positioned.node.id }
                        StudyMapNodeCard(
                            node = positioned.node,
                            branchColor = branchColorFor(positioned.node.id, visibleMap),
                            childCount = totalChildren,
                            collapsed = positioned.node.id in collapsedNodes,
                            onToggle = {
                                if (totalChildren > 0) {
                                    collapsedNodes = if (positioned.node.id in collapsedNodes) {
                                        collapsedNodes - positioned.node.id
                                    } else {
                                        collapsedNodes + positioned.node.id
                                    }
                                }
                            },
                            modifier = Modifier
                                .offset { IntOffset(positioned.x.roundToInt(), positioned.y.roundToInt()) }
                                .width(widthDp)
                                .heightIn(min = minHeightDp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyMapToolbar(
    map: StudyMap,
    style: StudyMapLayoutStyle,
    onLayoutChange: (StudyMapLayoutStyle) -> Unit,
    onExport: (StudyMapExportFormat, StudyMapExportScope) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                map.title,
                color = NotCanOffWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Compartir y exportar", tint = NotCanOffWhite)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Compartir PNG · mapa completo") },
                        onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PNG, StudyMapExportScope.FULL_MAP) }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir PNG · vista actual") },
                        onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PNG, StudyMapExportScope.CURRENT_VIEW) }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir PDF · mapa completo") },
                        onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PDF, StudyMapExportScope.FULL_MAP) }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir PDF · vista actual") },
                        onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PDF, StudyMapExportScope.CURRENT_VIEW) }
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = NotCanBlue.copy(alpha = 0.15f)) {
                Text(
                    if (map.type == StudyMapType.MIND_MAP) "Mapa mental" else "Mapa conceptual",
                    color = NotCanBlue,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            MapStyleButton("Ramas", style == StudyMapLayoutStyle.HORIZONTAL_BRANCHES) { onLayoutChange(StudyMapLayoutStyle.HORIZONTAL_BRANCHES) }
            MapStyleButton("Radial", style == StudyMapLayoutStyle.RADIAL) { onLayoutChange(StudyMapLayoutStyle.RADIAL) }
            MapStyleButton("Árbol", style == StudyMapLayoutStyle.TREE) { onLayoutChange(StudyMapLayoutStyle.TREE) }
            MapStyleButton("Constelación", style == StudyMapLayoutStyle.CONSTELLATION) { onLayoutChange(StudyMapLayoutStyle.CONSTELLATION) }
        }
    }
}

@Composable
private fun MapStyleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) NotCanBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    ) {
        TextButton(onClick = onClick) {
            Text(label, color = if (selected) NotCanBlue else NotCanGray)
        }
    }
}

@Composable
private fun StudyMapNodeCard(
    node: StudyMapNode,
    branchColor: Color,
    childCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(enabled = childCount > 0, onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (node.level == 0) branchColor.copy(alpha = 0.22f) else NotCanSurface
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = branchColor, shape = MaterialTheme.shapes.small, modifier = Modifier.size(width = 5.dp, height = 28.dp)) { }
                Spacer(Modifier.width(8.dp))
                Text(
                    node.title,
                    color = NotCanOffWhite,
                    style = if (node.level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (node.level <= 1) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (childCount > 0) {
                    Surface(color = branchColor.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
                        Text(
                            text = if (collapsed) "+$childCount" else "−",
                            color = branchColor,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            node.description?.let {
                Text(
                    it,
                    color = NotCanGray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildVisibleMap(map: StudyMap, collapsed: Set<String>): StudyMap {
    if (collapsed.isEmpty()) return map
    val children = map.edges.groupBy { it.from }.mapValues { (_, edges) -> edges.map { it.to } }
    val visibleIds = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.addLast(map.rootNodeId)
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!visibleIds.add(id)) continue
        if (id in collapsed) continue
        children[id].orEmpty().forEach(queue::addLast)
    }
    return map.copy(
        nodes = map.nodes.filter { it.id in visibleIds },
        edges = map.edges.filter { it.from in visibleIds && it.to in visibleIds }
    )
}

private fun branchColorFor(nodeId: String, map: StudyMap): Color {
    if (nodeId == map.rootNodeId) return branchColors.first()
    val parentByChild = map.edges.associate { it.to to it.from }
    var current = nodeId
    var parent = parentByChild[current]
    while (parent != null && parent != map.rootNodeId) {
        current = parent
        parent = parentByChild[current]
    }
    val rootChildren = map.edges.filter { it.from == map.rootNodeId }.map { it.to }
    val index = rootChildren.indexOf(current).let { if (it < 0) 0 else it }
    return branchColors[index % branchColors.size]
}
