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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val branchColors = listOf(
    Color(0xFF3478F6),
    Color(0xFF9A6BE8),
    Color(0xFF49C8E8),
    Color(0xFF4FC38A),
    Color(0xFFE1B756),
    Color(0xFFE58B55),
    Color(0xFFE36F9F)
)

@Composable
fun StudyMapScreen(
    map: StudyMap,
    modifier: Modifier = Modifier,
    initialLayout: StudyMapLayoutStyle? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var layoutStyle by remember(map.id, initialLayout) {
        mutableStateOf(
            initialLayout ?: if (map.type == StudyMapType.MIND_MAP) {
                StudyMapLayoutStyle.HORIZONTAL_BRANCHES
            } else {
                StudyMapLayoutStyle.TREE
            }
        )
    }
    var zoom by remember(map.id) { mutableFloatStateOf(1f) }
    var panX by remember(map.id) { mutableFloatStateOf(0f) }
    var panY by remember(map.id) { mutableFloatStateOf(0f) }
    var viewportWidth by remember(map.id) { mutableFloatStateOf(1280f) }
    var viewportHeight by remember(map.id) { mutableFloatStateOf(800f) }
    var collapsedNodes by remember(map.id) { mutableStateOf(setOf<String>()) }
    var fitRequest by remember(map.id) { mutableIntStateOf(0) }
    var centerRequest by remember(map.id) { mutableIntStateOf(0) }

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
        // La barra está fuera del lienzo y por encima de él en el árbol de dibujo.
        Box(Modifier.fillMaxWidth().zIndex(3f)) {
            StudyMapToolbar(
                map = map,
                style = layoutStyle,
                zoom = zoom,
                onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest = 0; zoom = 1f; centerRequest++ },
                onZoomOut = { zoom = (zoom / 1.18f).coerceIn(0.35f, 3.5f) },
                onZoomIn = { zoom = (zoom * 1.18f).coerceIn(0.35f, 3.5f) },
                onFit = { fitRequest++ },
                onCenter = { centerRequest++ },
                onExport = ::exportAndShare
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            LaunchedEffect(widthPx, heightPx) {
                viewportWidth = widthPx
                viewportHeight = heightPx
            }

            // El mapa usa un lienzo virtual grande. No se reduce el texto para intentar meterlo todo
            // en la pantalla: el usuario puede desplazarse y hacer zoom libremente.
            val textDemand = remember(visibleMap) {
                visibleMap.nodes.fold(0f) { total, node ->
                    val chars = node.title.length + (node.description?.length ?: 0)
                    total + 108f + (chars / 30f) * 14f
                }
            }
            // El motor de mapas usa unidades visuales equivalentes a dp. Antes los valores
            // de ancho/alto se trataban como px físicos y en pantallas densas los nodos quedaban
            // demasiado estrechos. Escalamos a px solo en la fase final de dibujo.
            val densityScale = density.density.coerceAtLeast(1f)
            val nodeCount = visibleMap.nodes.size.coerceAtLeast(1)
            val virtualWidthDpValue = maxOf(
                maxWidth.value,
                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> maxOf(1800f, nodeCount * 150f)
                    StudyMapLayoutStyle.TREE -> maxOf(1900f, nodeCount * 250f)
                    StudyMapLayoutStyle.RADIAL, StudyMapLayoutStyle.RADIAL_CARDS,
                    StudyMapLayoutStyle.IDEA_BOARD, StudyMapLayoutStyle.CONSTELLATION -> maxOf(1900f, nodeCount * 112f)
                }
            )
            val virtualHeightDpValue = maxOf(
                maxHeight.value,
                textDemand * 0.86f,
                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> maxOf(1500f, nodeCount * 190f)
                    StudyMapLayoutStyle.TREE -> maxOf(1400f, nodeCount * 120f)
                    else -> maxOf(1900f, nodeCount * 128f)
                }
            )
            val virtualWidthDp = virtualWidthDpValue.dp
            val virtualHeightDp = virtualHeightDpValue.dp
            val virtualWidthPx = with(density) { virtualWidthDp.toPx() }
            val virtualHeightPx = with(density) { virtualHeightDp.toPx() }
            val nodes = remember(visibleMap, layoutStyle, virtualWidthDpValue, virtualHeightDpValue, densityScale) {
                StudyMapLayoutEngine.layout(visibleMap, layoutStyle, virtualWidthDpValue, virtualHeightDpValue)
                    .map { positioned ->
                        positioned.copy(
                            x = positioned.x * densityScale,
                            y = positioned.y * densityScale,
                            width = positioned.width * densityScale,
                            height = positioned.height * densityScale
                        )
                    }
            }
            val positionedById = remember(nodes) { nodes.associateBy { it.node.id } }

            LaunchedEffect(nodes, widthPx, heightPx, fitRequest) {
                if (nodes.isEmpty() || widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
                val minX = nodes.minOf { it.x }
                val minY = nodes.minOf { it.y }
                val maxX = nodes.maxOf { it.x + it.width }
                val maxY = nodes.maxOf { it.y + it.height }
                val contentWidth = (maxX - minX).coerceAtLeast(1f)
                val contentHeight = (maxY - minY).coerceAtLeast(1f)
                val padding = 76f
                val fitted = minOf(
                    ((widthPx - padding * 2f) / contentWidth),
                    ((heightPx - padding * 2f) / contentHeight)
                ).coerceIn(0.38f, 1.25f)
                // Open every layout already framed inside the viewport. The user can immediately
                // zoom in for reading instead of discovering half the graph outside the screen.
                val targetZoom = if (fitRequest == 0) minOf(fitted, 0.92f) else fitted
                zoom = targetZoom
                panX = (widthPx - contentWidth * targetZoom) / 2f - minX * targetZoom
                panY = (heightPx - contentHeight * targetZoom) / 2f - minY * targetZoom
            }

            LaunchedEffect(centerRequest) {
                if (centerRequest == 0 || nodes.isEmpty() || widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
                val minX = nodes.minOf { it.x }
                val minY = nodes.minOf { it.y }
                val maxX = nodes.maxOf { it.x + it.width }
                val maxY = nodes.maxOf { it.y + it.height }
                val centerX = (minX + maxX) / 2f
                val centerY = (minY + maxY) / 2f
                panX = widthPx / 2f - centerX * zoom
                panY = heightPx / 2f - centerY * zoom
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(map.id, layoutStyle) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val oldZoom = zoom
                            val newZoom = (oldZoom * gestureZoom).coerceIn(0.35f, 3.5f)
                            val ratio = if (oldZoom == 0f) 1f else newZoom / oldZoom
                            panX = centroid.x - (centroid.x - panX) * ratio + pan.x
                            panY = centroid.y - (centroid.y - panY) * ratio + pan.y
                            zoom = newZoom
                        }
                    }
            ) {
                Box(
                    Modifier
                        .size(virtualWidthDp, virtualHeightDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
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
                            val start = when (layoutStyle) {
                                StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> Offset(from.x + from.width, from.y + from.height / 2f)
                                StudyMapLayoutStyle.TREE -> Offset(from.x + from.width / 2f, from.y + from.height)
                                else -> Offset(from.x + from.width / 2f, from.y + from.height / 2f)
                            }
                            val end = when (layoutStyle) {
                                StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> Offset(to.x, to.y + to.height / 2f)
                                StudyMapLayoutStyle.TREE -> Offset(to.x + to.width / 2f, to.y)
                                else -> Offset(to.x + to.width / 2f, to.y + to.height / 2f)
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
                                color = branchColor.copy(alpha = if (layoutStyle == StudyMapLayoutStyle.IDEA_BOARD) 0.76f else 0.68f),
                                style = Stroke(
                                    width = when (layoutStyle) {
                                        StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> 4.2f
                                        StudyMapLayoutStyle.IDEA_BOARD -> 3.2f
                                        else -> 3.4f
                                    },
                                    pathEffect = if (layoutStyle == StudyMapLayoutStyle.IDEA_BOARD) {
                                        PathEffect.dashPathEffect(floatArrayOf(13f, 9f))
                                    } else null
                                )
                            )
                        }
                    }

                    visibleMap.edges.forEach { edge ->
                        val label = edge.label?.trim().orEmpty()
                        if (label.isBlank()) return@forEach
                        val from = positionedById[edge.from] ?: return@forEach
                        val to = positionedById[edge.to] ?: return@forEach
                        val startX = when (layoutStyle) {
                            StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> from.x + from.width
                            else -> from.x + from.width / 2f
                        }
                        val startY = when (layoutStyle) {
                            StudyMapLayoutStyle.TREE -> from.y + from.height
                            else -> from.y + from.height / 2f
                        }
                        val endX = when (layoutStyle) {
                            StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> to.x
                            else -> to.x + to.width / 2f
                        }
                        val endY = when (layoutStyle) {
                            StudyMapLayoutStyle.TREE -> to.y
                            else -> to.y + to.height / 2f
                        }
                        val dx = endX - startX
                        val dy = endY - startY
                        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        val perpendicularX = -dy / length
                        val perpendicularY = dx / length
                        val labelWidthPx = ((label.length * 7.6f + 30f).coerceIn(92f, 220f)) * densityScale
                        val labelHeightPx = (if (label.length > 18) 48f else 36f) * densityScale
                        val marginPx = 18f * densityScale

                        fun collidesWithNode(x: Float, y: Float): Boolean = nodes.any { node ->
                            val separated = x + labelWidthPx + marginPx < node.x ||
                                x - marginPx > node.x + node.width ||
                                y + labelHeightPx + marginPx < node.y ||
                                y - marginPx > node.y + node.height
                            !separated
                        }

                        var labelX = startX + dx * 0.5f - labelWidthPx / 2f
                        var labelY = startY + dy * 0.5f - labelHeightPx / 2f
                        val fractions = floatArrayOf(0.50f, 0.38f, 0.62f, 0.27f, 0.73f)
                        val perpendicularOffsetsDp = floatArrayOf(0f, 34f, -34f, 62f, -62f, 92f, -92f)
                        var foundFreeSpot = false
                        for (fraction in fractions) {
                            if (foundFreeSpot) break
                            for (offsetDp in perpendicularOffsetsDp) {
                                val offsetPx = offsetDp * densityScale
                                val candidateX = startX + dx * fraction + perpendicularX * offsetPx - labelWidthPx / 2f
                                val candidateY = startY + dy * fraction + perpendicularY * offsetPx - labelHeightPx / 2f
                                if (!collidesWithNode(candidateX, candidateY)) {
                                    labelX = candidateX
                                    labelY = candidateY
                                    foundFreeSpot = true
                                    break
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .offset { IntOffset(labelX.roundToInt(), labelY.roundToInt()) }
                                .widthIn(min = 54.dp, max = 220.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                label,
                                color = branchColorFor(edge.to, visibleMap),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                softWrap = true,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
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
                            layoutStyle = layoutStyle,
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
    zoom: Float,
    onLayoutChange: (StudyMapLayoutStyle) -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onCenter: () -> Unit,
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
                    DropdownMenuItem(text = { Text("Compartir PNG · mapa completo") }, onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PNG, StudyMapExportScope.FULL_MAP) })
                    DropdownMenuItem(text = { Text("Compartir PNG · vista actual") }, onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PNG, StudyMapExportScope.CURRENT_VIEW) })
                    DropdownMenuItem(text = { Text("Compartir PDF · mapa completo") }, onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PDF, StudyMapExportScope.FULL_MAP) })
                    DropdownMenuItem(text = { Text("Compartir PDF · vista actual") }, onClick = { menuExpanded = false; onExport(StudyMapExportFormat.PDF, StudyMapExportScope.CURRENT_VIEW) })
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
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
            MapStyleButton("Tarjetas", style == StudyMapLayoutStyle.RADIAL_CARDS) { onLayoutChange(StudyMapLayoutStyle.RADIAL_CARDS) }
            MapStyleButton("Ideas", style == StudyMapLayoutStyle.IDEA_BOARD) { onLayoutChange(StudyMapLayoutStyle.IDEA_BOARD) }
            MapStyleButton("Árbol", style == StudyMapLayoutStyle.TREE) { onLayoutChange(StudyMapLayoutStyle.TREE) }
            MapStyleButton("Constelación", style == StudyMapLayoutStyle.CONSTELLATION) { onLayoutChange(StudyMapLayoutStyle.CONSTELLATION) }
            MapControlButton("−", onZoomOut)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Text(
                    "${(zoom * 100f).roundToInt()}%",
                    color = NotCanGray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            MapControlButton("+", onZoomIn)
            MapControlButton("Ajustar", onFit)
            MapControlButton("Centrar", onCenter)
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
private fun MapControlButton(label: String, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        TextButton(onClick = onClick) { Text(label, color = NotCanOffWhite) }
    }
}

@Composable
private fun StudyMapNodeCard(
    node: StudyMapNode,
    branchColor: Color,
    childCount: Int,
    collapsed: Boolean,
    layoutStyle: StudyMapLayoutStyle,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visualCard = layoutStyle == StudyMapLayoutStyle.RADIAL_CARDS || layoutStyle == StudyMapLayoutStyle.IDEA_BOARD
    val container = when {
        node.level == 0 -> branchColor.copy(alpha = if (visualCard) 0.30f else 0.22f)
        visualCard -> branchColor.copy(alpha = 0.16f)
        else -> NotCanSurface
    }

    Card(
        modifier = modifier.clickable(enabled = childCount > 0, onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = if (visualCard) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = if (visualCard) 2.dp else 0.dp)
    ) {
        Column(
            Modifier.padding(horizontal = if (visualCard) 15.dp else 12.dp, vertical = if (visualCard) 13.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (visualCard) 7.dp else 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = branchColor,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(width = if (visualCard) 7.dp else 5.dp, height = if (visualCard) 34.dp else 28.dp)
                ) { }
                Spacer(Modifier.width(if (visualCard) 10.dp else 8.dp))
                Text(
                    node.title,
                    color = NotCanOffWhite,
                    style = when {
                        node.level == 0 && visualCard -> MaterialTheme.typography.titleMedium
                        node.level == 0 -> MaterialTheme.typography.titleSmall
                        visualCard && node.level == 1 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (node.level <= 1) FontWeight.SemiBold else FontWeight.Medium,
                    softWrap = true,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (childCount > 0) {
                    Surface(color = branchColor.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small) {
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
                    color = if (visualCard) NotCanOffWhite.copy(alpha = 0.78f) else NotCanGray,
                    style = if (visualCard) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    softWrap = true,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (node.sourceRefs.isNotEmpty()) {
                Text(
                    node.sourceRefs.joinToString(" · "),
                    color = branchColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    softWrap = true,
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
