package com.notcan.app.ui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlin.math.roundToInt

@Composable
fun StudyMapScreen(
    map: StudyMap,
    modifier: Modifier = Modifier
) {
    var layoutStyle by remember(map.id) { mutableStateOf(StudyMapLayoutStyle.RADIAL) }
    var zoom by remember(map.id) { mutableFloatStateOf(1f) }
    var panX by remember(map.id) { mutableFloatStateOf(0f) }
    var panY by remember(map.id) { mutableFloatStateOf(0f) }

    Column(modifier.fillMaxSize()) {
        StudyMapToolbar(map, layoutStyle, onLayoutChange = {
            layoutStyle = it
            zoom = 1f
            panX = 0f
            panY = 0f
        })

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val nodes = remember(map, layoutStyle, widthPx, heightPx) {
                StudyMapLayoutEngine.layout(map, layoutStyle, widthPx, heightPx)
            }
            val positionedById = remember(nodes) { nodes.associateBy { it.node.id } }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(map.id, layoutStyle) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.55f, 2.5f)
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
                        map.edges.forEach { edge ->
                            val from = positionedById[edge.from] ?: return@forEach
                            val to = positionedById[edge.to] ?: return@forEach
                            val start = Offset(from.x + from.width / 2f, from.y + from.height / 2f)
                            val end = Offset(to.x + to.width / 2f, to.y + to.height / 2f)
                            val dx = end.x - start.x
                            val path = Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(
                                    start.x + dx * 0.32f,
                                    start.y,
                                    start.x + dx * 0.68f,
                                    end.y,
                                    end.x,
                                    end.y
                                )
                            }
                            drawPath(
                                path = path,
                                color = NotCanBlue.copy(alpha = 0.58f),
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }

                    nodes.forEach { positioned ->
                        val widthDp = with(density) { positioned.width.toDp() }
                        val minHeightDp = with(density) { positioned.height.toDp() }
                        StudyMapNodeCard(
                            node = positioned.node,
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
    onLayoutChange: (StudyMapLayoutStyle) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            map.title,
            color = NotCanOffWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
private fun StudyMapNodeCard(node: StudyMapNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (node.level == 0) NotCanBlue.copy(alpha = 0.22f) else NotCanSurface
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                node.title,
                color = NotCanOffWhite,
                style = if (node.level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (node.level <= 1) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
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
