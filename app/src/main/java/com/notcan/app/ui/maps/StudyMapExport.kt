package com.notcan.app.ui.maps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

internal enum class StudyMapExportFormat { PNG, PDF }
internal enum class StudyMapExportScope { FULL_MAP, CURRENT_VIEW }
internal enum class StudyMapExportTheme { LIGHT, DARK }

internal data class StudyMapViewport(
    val widthPx: Float,
    val heightPx: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
)

internal data class StudyMapExportOptions(
    val format: StudyMapExportFormat,
    val scope: StudyMapExportScope = StudyMapExportScope.FULL_MAP,
    val theme: StudyMapExportTheme = StudyMapExportTheme.LIGHT,
    val includeTitle: Boolean = true,
    val includeSources: Boolean = true,
    val pdfLandscape: Boolean = true
)

internal object StudyMapExportManager {
    private const val FULL_WIDTH = 2600
    private const val FULL_HEIGHT = 1600
    private const val EXPORT_MARGIN = 72f
    private const val HEADER_HEIGHT = 150f

    fun export(
        context: Context,
        map: StudyMap,
        layoutStyle: StudyMapLayoutStyle,
        options: StudyMapExportOptions,
        viewport: StudyMapViewport? = null
    ): File {
        val bitmap = renderBitmap(map, layoutStyle, options, viewport)
        return when (options.format) {
            StudyMapExportFormat.PNG -> writePng(context, map, bitmap)
            StudyMapExportFormat.PDF -> writePdf(context, map, bitmap, options.pdfLandscape)
        }
    }

    fun share(context: Context, file: File, format: StudyMapExportFormat) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = if (format == StudyMapExportFormat.PNG) "image/png" else "application/pdf"
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Compartir mapa"))
    }

    private fun renderBitmap(
        map: StudyMap,
        style: StudyMapLayoutStyle,
        options: StudyMapExportOptions,
        viewport: StudyMapViewport?
    ): Bitmap {
        val width = when (options.scope) {
            StudyMapExportScope.FULL_MAP -> FULL_WIDTH
            StudyMapExportScope.CURRENT_VIEW -> max(720, viewport?.widthPx?.toInt() ?: 1280)
        }
        val height = when (options.scope) {
            StudyMapExportScope.FULL_MAP -> FULL_HEIGHT
            StudyMapExportScope.CURRENT_VIEW -> max(520, viewport?.heightPx?.toInt() ?: 800)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = palette(options.theme)
        canvas.drawColor(palette.background)

        val titleOffset = if (options.includeTitle) HEADER_HEIGHT else EXPORT_MARGIN
        if (options.includeTitle) drawHeader(canvas, map, palette, width.toFloat(), options.includeSources)

        val layoutHeight = (height.toFloat() - titleOffset - EXPORT_MARGIN).coerceAtLeast(420f)
        val nodes = StudyMapLayoutEngine.layout(
            map = map,
            style = style,
            canvasWidth = width.toFloat() - EXPORT_MARGIN * 2f,
            canvasHeight = layoutHeight
        ).map { it.copy(x = it.x + EXPORT_MARGIN, y = it.y + titleOffset) }
        val byId = nodes.associateBy { it.node.id }

        val transformZoom = if (options.scope == StudyMapExportScope.CURRENT_VIEW) viewport?.zoom ?: 1f else 1f
        val transformX = if (options.scope == StudyMapExportScope.CURRENT_VIEW) viewport?.panX ?: 0f else 0f
        val transformY = if (options.scope == StudyMapExportScope.CURRENT_VIEW) viewport?.panY ?: 0f else 0f

        canvas.save()
        canvas.translate(transformX, transformY)
        canvas.scale(transformZoom, transformZoom)
        drawEdges(canvas, map, byId, palette, style)
        nodes.forEach { drawNode(canvas, it, palette, style) }
        canvas.restore()

        if (options.scope == StudyMapExportScope.CURRENT_VIEW || nodes.isEmpty()) return bitmap
        return cropToContent(bitmap, nodes, options.includeTitle)
    }

    private fun cropToContent(bitmap: Bitmap, nodes: List<PositionedStudyMapNode>, includeTitle: Boolean): Bitmap {
        val minX = nodes.minOf { it.x }
        val maxX = nodes.maxOf { it.x + it.width }
        val minY = nodes.minOf { it.y }
        val maxY = nodes.maxOf { it.y + it.height }
        val left = (minX - 90f).coerceAtLeast(0f).toInt()
        val right = (maxX + 90f).coerceAtMost(bitmap.width.toFloat()).toInt()
        val top = if (includeTitle) 0 else (minY - 90f).coerceAtLeast(0f).toInt()
        val bottom = (maxY + 90f).coerceAtMost(bitmap.height.toFloat()).toInt()
        val cropWidth = (right - left).coerceAtLeast(320)
        val cropHeight = (bottom - top).coerceAtLeast(260)
        if (left == 0 && top == 0 && cropWidth >= bitmap.width && cropHeight >= bitmap.height) return bitmap
        val safeWidth = cropWidth.coerceAtMost(bitmap.width - left)
        val safeHeight = cropHeight.coerceAtMost(bitmap.height - top)
        val cropped = Bitmap.createBitmap(bitmap, left, top, safeWidth, safeHeight)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    private fun drawHeader(canvas: Canvas, map: StudyMap, palette: ExportPalette, width: Float, includeSources: Boolean) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(map.title.take(80), EXPORT_MARGIN, 68f, titlePaint)

        val typeLabel = if (map.type == StudyMapType.MIND_MAP) "Mapa mental" else "Mapa conceptual"
        val meta = buildString {
            append(typeLabel)
            append(" · ${map.nodes.size} conceptos")
            if (includeSources) {
                val sources = map.nodes.flatMap { it.sourceRefs }.distinct().size
                if (sources > 0) append(" · $sources fuentes")
            }
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 24f }
        canvas.drawText(meta, EXPORT_MARGIN, 108f, metaPaint)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.border; strokeWidth = 2f }
        canvas.drawLine(EXPORT_MARGIN, 132f, width - EXPORT_MARGIN, 132f, linePaint)
    }

    private fun drawEdges(
        canvas: Canvas,
        map: StudyMap,
        byId: Map<String, PositionedStudyMapNode>,
        palette: ExportPalette,
        style: StudyMapLayoutStyle
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.connector
            this.style = Paint.Style.STROKE
            strokeWidth = if (style == StudyMapLayoutStyle.HORIZONTAL_BRANCHES) 5.5f else 4.5f
            strokeCap = Paint.Cap.ROUND
            pathEffect = if (style == StudyMapLayoutStyle.IDEA_BOARD) DashPathEffect(floatArrayOf(15f, 10f), 0f) else null
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 19f }
        val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background; this.style = Paint.Style.FILL }
        val horizontal = style == StudyMapLayoutStyle.HORIZONTAL_BRANCHES || style == StudyMapLayoutStyle.TREE

        map.edges.forEach { edge ->
            val from = byId[edge.from] ?: return@forEach
            val to = byId[edge.to] ?: return@forEach
            val startX = if (horizontal) from.x + from.width else from.x + from.width / 2f
            val startY = from.y + from.height / 2f
            val endX = if (horizontal) to.x else to.x + to.width / 2f
            val endY = to.y + to.height / 2f
            val dx = endX - startX
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(startX + dx * 0.38f, startY, startX + dx * 0.62f, endY, endX, endY)
            }
            canvas.drawPath(path, linePaint)

            edge.label?.takeIf { it.isNotBlank() }?.let { label ->
                val visible = label.take(24)
                val x = startX + dx * 0.50f
                val y = (startY + endY) / 2f - 10f
                val textWidth = labelPaint.measureText(visible)
                val rect = RectF(x - 8f, y - 23f, x + textWidth + 8f, y + 7f)
                canvas.drawRoundRect(rect, 8f, 8f, labelBackground)
                canvas.drawText(visible, x, y, labelPaint)
            }
        }
    }

    private fun drawNode(canvas: Canvas, positioned: PositionedStudyMapNode, palette: ExportPalette, style: StudyMapLayoutStyle) {
        val node = positioned.node
        val visual = style == StudyMapLayoutStyle.RADIAL_CARDS || style == StudyMapLayoutStyle.IDEA_BOARD
        val rect = RectF(positioned.x, positioned.y, positioned.x + positioned.width, positioned.y + positioned.height)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                node.level == 0 -> palette.root
                visual -> palette.visualCard
                else -> palette.card
            }
            this.style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (node.level == 0) palette.connector else palette.border
            this.style = Paint.Style.STROKE
            strokeWidth = if (node.level == 0) 3f else 2f
        }
        val radius = if (visual) 36f else 24f
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, border)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text
            textSize = if (node.level == 0) 27f else if (node.level == 1) 23f else 20f
            typeface = Typeface.create(Typeface.DEFAULT, if (node.level <= 1) Typeface.BOLD else Typeface.NORMAL)
        }
        val textWidth = (positioned.width - 30f).toInt().coerceAtLeast(80)
        val title = compact(node.title, if (node.level == 0) 82 else 64)
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(if (visual) 5 else if (node.level == 0) 3 else 4)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(positioned.x + 15f, positioned.y + 14f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun compact(value: String, max: Int): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= max) return clean
        return clean.take(max).substringBeforeLast(' ', clean.take(max)).trimEnd() + "…"
    }

    private fun writePng(context: Context, map: StudyMap, bitmap: Bitmap): File {
        val file = File(exportDirectory(context), "${safeName(map.title)}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun writePdf(context: Context, map: StudyMap, bitmap: Bitmap, landscape: Boolean): File {
        val file = File(exportDirectory(context), "${safeName(map.title)}_${System.currentTimeMillis()}.pdf")
        val pageWidth = if (landscape) 842 else 595
        val pageHeight = if (landscape) 595 else 842
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        page.canvas.drawColor(Color.WHITE)
        val target = fitRect(bitmap.width.toFloat(), bitmap.height.toFloat(), pageWidth.toFloat(), pageHeight.toFloat(), 18f)
        page.canvas.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        bitmap.recycle()
        return file
    }

    private fun fitRect(sourceW: Float, sourceH: Float, targetW: Float, targetH: Float, margin: Float): RectF {
        val usableW = targetW - margin * 2f
        val usableH = targetH - margin * 2f
        val scale = minOf(usableW / sourceW, usableH / sourceH)
        val w = sourceW * scale
        val h = sourceH * scale
        val left = (targetW - w) / 2f
        val top = (targetH - h) / 2f
        return RectF(left, top, left + w, top + h)
    }

    private fun exportDirectory(context: Context): File = File(context.filesDir, "documents/maps").apply { mkdirs() }

    private fun safeName(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ_-]+"), "_")
        .trim('_')
        .take(60)
        .ifBlank { "mapa_notcan" }

    private data class ExportPalette(
        val background: Int,
        val card: Int,
        val visualCard: Int,
        val root: Int,
        val text: Int,
        val muted: Int,
        val border: Int,
        val connector: Int
    )

    private fun palette(theme: StudyMapExportTheme): ExportPalette = when (theme) {
        StudyMapExportTheme.LIGHT -> ExportPalette(
            background = Color.rgb(250, 251, 253), card = Color.WHITE, visualCard = Color.rgb(244, 247, 253),
            root = Color.rgb(232, 240, 255), text = Color.rgb(24, 31, 43), muted = Color.rgb(92, 104, 122),
            border = Color.rgb(214, 221, 232), connector = Color.rgb(52, 120, 246)
        )
        StudyMapExportTheme.DARK -> ExportPalette(
            background = Color.rgb(8, 13, 21), card = Color.rgb(19, 29, 42), visualCard = Color.rgb(24, 36, 52),
            root = Color.rgb(20, 43, 82), text = Color.rgb(244, 247, 251), muted = Color.rgb(167, 178, 195),
            border = Color.rgb(38, 54, 76), connector = Color.rgb(52, 120, 246)
        )
    }
}
