package com.notcan.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGraphite
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

internal enum class InkTool { PEN, HIGHLIGHTER, ERASER }
internal data class InkPoint(val x: Float, val y: Float, val pressure: Float)
private data class RenderedPdfPage(val bitmap: Bitmap, val pageCount: Int)

@Composable
internal fun PdfAnnotationWorkspace(
    documents: List<DocumentResourceEntity>,
    inkStrokes: List<PdfInkStrokeEntity>,
    onImportDocument: () -> Unit,
    onOpenExternally: (DocumentResourceEntity) -> Unit,
    onSaveStroke: (documentId: String, pageIndex: Int, tool: String, colorArgb: Long, baseWidth: Float, pointsData: String) -> Unit,
    onDeleteStroke: (String) -> Unit,
    onClearPage: (documentId: String, pageIndex: Int) -> Unit
) {
    val pdfs = documents.filter { it.documentType == "PDF" }
    var selectedId by remember(pdfs) { mutableStateOf(pdfs.firstOrNull()?.id) }
    val selectedPdf = pdfs.firstOrNull { it.id == selectedId } ?: pdfs.firstOrNull()
    var pageIndex by remember(selectedPdf?.id) { mutableIntStateOf(0) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            color = NotCanGraphite,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PDF", color = NotCanOffWhite, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onImportDocument) { Text("+ Importar") }
                }
                if (pdfs.isEmpty()) {
                    Text("Importa un PDF para leerlo y escribir encima con el lápiz.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pdfs, key = { it.id }) { document ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedId = document.id },
                                color = if (document.id == selectedPdf?.id) NotCanBlue.copy(alpha = 0.18f) else androidColorTransparent,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    document.displayName,
                                    color = if (document.id == selectedPdf?.id) NotCanOffWhite else NotCanGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedPdf == null) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Visor PDF + Pencil", color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Las anotaciones se guardarán por página y sin modificar el PDF original.", color = NotCanGray)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onImportDocument) { Text("Importar PDF") }
                }
            }
        } else {
            PdfReaderAndInk(
                document = selectedPdf,
                pageIndex = pageIndex,
                onPageIndexChange = { pageIndex = it },
                pageStrokes = inkStrokes.filter { it.documentId == selectedPdf.id && it.pageIndex == pageIndex },
                onOpenExternally = { onOpenExternally(selectedPdf) },
                onSaveStroke = { tool, color, width, points ->
                    onSaveStroke(selectedPdf.id, pageIndex, tool.name, color, width, encodePoints(points))
                },
                onDeleteStroke = onDeleteStroke,
                onClearPage = { onClearPage(selectedPdf.id, pageIndex) }
            )
        }
    }
}

@Composable
private fun PdfReaderAndInk(
    document: DocumentResourceEntity,
    pageIndex: Int,
    onPageIndexChange: (Int) -> Unit,
    pageStrokes: List<PdfInkStrokeEntity>,
    onOpenExternally: () -> Unit,
    onSaveStroke: (InkTool, Long, Float, List<InkPoint>) -> Unit,
    onDeleteStroke: (String) -> Unit,
    onClearPage: () -> Unit
) {
    var rendered by remember(document.id, pageIndex) { mutableStateOf<RenderedPdfPage?>(null) }
    var renderError by remember(document.id, pageIndex) { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(InkTool.PEN) }
    var penColor by remember { mutableStateOf(0xFF1565C0L) }
    var stylusOnly by remember { mutableStateOf(true) }

    LaunchedEffect(document.localPath, pageIndex) {
        rendered = null
        renderError = null
        try {
            rendered = withContext(Dispatchers.IO) { renderPdfPage(File(document.localPath), pageIndex) }
        } catch (t: Throwable) {
            renderError = t.message ?: "No se pudo mostrar esta página"
        }
    }

    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ToolChip("Pluma", tool == InkTool.PEN) { tool = InkTool.PEN }
                ToolChip("Resaltador", tool == InkTool.HIGHLIGHTER) { tool = InkTool.HIGHLIGHTER }
                ToolChip("Borrador", tool == InkTool.ERASER) { tool = InkTool.ERASER }
                Spacer(Modifier.width(4.dp))
                ColorDot(0xFF15181DL, penColor == 0xFF15181DL) { penColor = 0xFF15181DL }
                ColorDot(0xFF1565C0L, penColor == 0xFF1565C0L) { penColor = 0xFF1565C0L }
                ColorDot(0xFFC62828L, penColor == 0xFFC62828L) { penColor = 0xFFC62828L }
                Spacer(Modifier.weight(1f))
                Text("Solo lápiz", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Switch(checked = stylusOnly, onCheckedChange = { stylusOnly = it })
                TextButton(onClick = onClearPage, enabled = pageStrokes.isNotEmpty()) { Text("Limpiar página") }
                TextButton(onClick = onOpenExternally) { Text("Abrir externo") }
            }

            Spacer(Modifier.height(8.dp))

            when {
                renderError != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(renderError ?: "Error", color = NotCanRed)
                }
                rendered == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Cargando página…", color = NotCanGray)
                }
                else -> {
                    val page = rendered!!
                    PdfPageCanvas(
                        bitmap = page.bitmap,
                        strokes = pageStrokes,
                        tool = tool,
                        colorArgb = if (tool == InkTool.HIGHLIGHTER) 0x6678AFFF else penColor,
                        baseWidth = if (tool == InkTool.HIGHLIGHTER) 12f else 4.2f,
                        stylusOnly = stylusOnly,
                        onStroke = onSaveStroke,
                        onErase = onDeleteStroke,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { onPageIndexChange(max(0, pageIndex - 1)) }, enabled = pageIndex > 0) { Text("Anterior") }
                        Text("  ${pageIndex + 1} / ${page.pageCount}  ", color = NotCanOffWhite)
                        OutlinedButton(onClick = { onPageIndexChange(pageIndex + 1) }, enabled = pageIndex + 1 < page.pageCount) { Text("Siguiente") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageCanvas(
    bitmap: Bitmap,
    strokes: List<PdfInkStrokeEntity>,
    tool: InkTool,
    colorArgb: Long,
    baseWidth: Float,
    stylusOnly: Boolean,
    onStroke: (InkTool, Long, Float, List<InkPoint>) -> Unit,
    onErase: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val pageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val containerAspect = if (maxHeight.value == 0f) pageAspect else maxWidth.value / maxHeight.value
        val pageWidth = if (pageAspect >= containerAspect) maxWidth else maxHeight * pageAspect
        val pageHeight = if (pageAspect >= containerAspect) maxWidth / pageAspect else maxHeight

        Box(
            modifier = Modifier
                .size(pageWidth, pageHeight)
                .background(androidx.compose.ui.graphics.Color.White)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Página PDF",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    StylusInkView(context).apply {
                        onStrokeFinished = onStroke
                        onEraseStroke = onErase
                    }
                },
                update = { view ->
                    view.configure(strokes, tool, colorArgb, baseWidth, stylusOnly)
                    view.onStrokeFinished = onStroke
                    view.onEraseStroke = onErase
                }
            )
        }
    }
}

private class StylusInkView(context: android.content.Context) : View(context) {
    var onStrokeFinished: (InkTool, Long, Float, List<InkPoint>) -> Unit = { _, _, _, _ -> }
    var onEraseStroke: (String) -> Unit = {}

    private var strokes: List<PdfInkStrokeEntity> = emptyList()
    private var tool = InkTool.PEN
    private var colorArgb: Long = 0xFF1565C0L
    private var baseWidth = 4.2f
    private var stylusOnly = true
    private val current = mutableListOf<InkPoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    fun configure(strokes: List<PdfInkStrokeEntity>, tool: InkTool, colorArgb: Long, baseWidth: Float, stylusOnly: Boolean) {
        this.strokes = strokes
        this.tool = tool
        this.colorArgb = colorArgb
        this.baseWidth = baseWidth
        this.stylusOnly = stylusOnly
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { drawStroke(canvas, decodePoints(it.pointsData), it.colorArgb, it.baseWidth) }
        if (current.size > 1 && tool != InkTool.ERASER) drawStroke(canvas, current, colorArgb, baseWidth)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointer = event.actionIndex.coerceAtLeast(0)
        val toolType = event.getToolType(pointer)
        val fromStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (stylusOnly && !fromStylus) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        val effectiveTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER) InkTool.ERASER else tool

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (effectiveTool == InkTool.ERASER) {
                    eraseNearest(event.x / widthSafe(), event.y / heightSafe())
                } else {
                    current.clear()
                    current += pointFrom(event, pointer)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (effectiveTool == InkTool.ERASER) {
                    eraseNearest(event.x / widthSafe(), event.y / heightSafe())
                } else {
                    for (i in 0 until event.historySize) {
                        current += InkPoint(
                            x = (event.getHistoricalX(pointer, i) / widthSafe()).coerceIn(0f, 1f),
                            y = (event.getHistoricalY(pointer, i) / heightSafe()).coerceIn(0f, 1f),
                            pressure = event.getHistoricalPressure(pointer, i).coerceIn(0.05f, 1.5f)
                        )
                    }
                    current += pointFrom(event, pointer)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (effectiveTool != InkTool.ERASER) {
                    current += pointFrom(event, pointer)
                    if (current.size >= 2) onStrokeFinished(effectiveTool, colorArgb, baseWidth, current.toList())
                    current.clear()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                current.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pointFrom(event: MotionEvent, pointer: Int) = InkPoint(
        x = (event.getX(pointer) / widthSafe()).coerceIn(0f, 1f),
        y = (event.getY(pointer) / heightSafe()).coerceIn(0f, 1f),
        pressure = event.getPressure(pointer).coerceIn(0.05f, 1.5f)
    )

    private fun drawStroke(canvas: Canvas, points: List<InkPoint>, color: Long, widthValue: Float) {
        if (points.size < 2) return
        paint.color = color.toInt()
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            val pressure = ((a.pressure + b.pressure) / 2f).coerceIn(0.1f, 1.5f)
            paint.strokeWidth = widthValue * resources.displayMetrics.density * (0.55f + pressure * 0.75f)
            canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, paint)
        }
    }

    private fun eraseNearest(x: Float, y: Float) {
        var best: PdfInkStrokeEntity? = null
        var distance = Float.MAX_VALUE
        strokes.forEach { stroke ->
            decodePoints(stroke.pointsData).forEach { p ->
                val d = abs(p.x - x) + abs(p.y - y)
                if (d < distance) { distance = d; best = stroke }
            }
        }
        val threshold = 0.035f
        if (best != null && distance < threshold) onEraseStroke(best!!.id)
    }

    private fun widthSafe() = width.coerceAtLeast(1).toFloat()
    private fun heightSafe() = height.coerceAtLeast(1).toFloat()
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) NotCanBlue.copy(alpha = 0.25f) else NotCanGraphite,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(label, color = if (selected) NotCanOffWhite else NotCanGray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun ColorDot(argb: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (selected) 30.dp else 25.dp)
            .background(androidx.compose.ui.graphics.Color(argb), CircleShape)
            .clickable(onClick = onClick)
    )
}

private fun renderPdfPage(file: File, pageIndex: Int): RenderedPdfPage {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use { fd ->
        PdfRenderer(fd).use { renderer ->
            require(renderer.pageCount > 0) { "PDF sin páginas" }
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val scale = 2f
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return RenderedPdfPage(bitmap, renderer.pageCount)
            }
        }
    }
}

private fun encodePoints(points: List<InkPoint>): String = points.joinToString(";") { "${it.x},${it.y},${it.pressure}" }
private fun decodePoints(data: String): List<InkPoint> = data.split(';').mapNotNull { item ->
    val parts = item.split(',')
    if (parts.size != 3) null else {
        val x = parts[0].toFloatOrNull()
        val y = parts[1].toFloatOrNull()
        val p = parts[2].toFloatOrNull()
        if (x == null || y == null || p == null) null else InkPoint(x, y, p)
    }
}

private val androidColorTransparent = androidx.compose.ui.graphics.Color.Transparent
