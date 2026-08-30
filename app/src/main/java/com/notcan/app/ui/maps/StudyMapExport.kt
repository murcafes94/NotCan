package com.notcan.app.ui.maps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
    private const val FULL_WIDTH = 2200
    private const val FULL_HEIGHT = 1400
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
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )
        val mime = when (format) {
            StudyMapExportFormat.PNG -> "image/png"
            StudyMapExportFormat.PDF -> "application/pdf"
        }
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

        val layoutHeight = (height.toFloat() - titleOffset - EXPORT_MARGIN).coerceAtLeast(360f)
        val nodes = StudyMapLayoutEngine.layout(
            map = map,
            style = style,
            canvasWidth = width.toFloat() - EXPORT_MARGIN * 2f,
            canvasHeight = layoutHeight
        ).map {
            it.copy(x = it.x + EXPORT_MARGIN, y = it.y + titleOffset)
        }
        val byId = nodes.associateBy { it.node.id }

        val transformZoom = if (options.scope == StudyMapExportScope.CURRENT_VIEW) viewport?.zoom ?: 1f else 1f
        val transformX = if (options.scope == StudyMapExportScope.CURRENT_VIEW) viewport?.panX ?: 0f else 0f
        val transformY = if (options.scope == StudyMapExportScope.CURRENT_VIEW) viewport?.panY ?: 0f else 0f

        canvas.save()
        canvas.translate(transformX, transformY)
        canvas.scale(transformZoom, transformZoom)
        drawEdges(canvas, map, byId, palette)
        nodes.forEach { drawNode(canvas, it, palette) }
        canvas.restore()
        return bitmap
    }

    private fun drawHeader(
        canvas: Canvas,
        map: StudyMap,
        palette: ExportPalette,
        width: Float,
        includeSources: Boolean
    ) {
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
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 24f
        }
        canvas.drawText(meta, EXPORT_MARGIN, 108f, metaPaint)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.border
            strokeWidth = 2f
        }
        canvas.drawLine(EXPORT_MARGIN, 132f, width - EXPORT_MARGIN, 132f, linePaint)
    }

    private fun drawEdges(
        canvas: Canvas,
        map: StudyMap,
        byId: Map<String, PositionedStudyMapNode>,
        palette: ExportPalette
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.connector
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 19f
        }
        map.edges.forEach { edge ->
            val from = byId[edge.from] ?: return@forEach
            val to = byId[edge.to] ?: return@forEach
            val startX = from.x + from.width
            val startY = from.y + from.height / 2f
            val endX = to.x
            val endY = to.y + to.height / 2f
            val dx = endX - startX
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(
                    startX + dx * 0.38f,
                    startY,
                    startX + dx * 0.62f,
                    endY,
                    endX,
                    endY
                )
            }
            canvas.drawPath(path, linePaint)
            edge.label?.takeIf { it.isNotBlank() }?.let { label ->
                canvas.drawText(label.take(28), startX + dx * 0.45f, (startY + endY) / 2f - 8f, labelPaint)
            }
        }
    }

    private fun drawNode(canvas: Canvas, positioned: PositionedStudyMapNode, palette: ExportPalette) {
        val node = positioned.node
        val rect = RectF(positioned.x, positioned.y, positioned.x + positioned.width, positioned.y + positioned.height)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (node.level == 0) palette.root else palette.card
            style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (node.level == 0) palette.connector else palette.border
            style = Paint.Style.STROKE
            strokeWidth = if (node.level == 0) 3f else 2f
        }
        canvas.drawRoundRect(rect, 24f, 24f, fill)
        canvas.drawRoundRect(rect, 24f, 24f, border)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text
            textSize = if (node.level == 0) 27f else if (node.level == 1) 23f else 20f
            typeface = Typeface.create(Typeface.DEFAULT, if (node.level <= 1) Typeface.BOLD else Typeface.NORMAL)
        }
        val textWidth = (positioned.width - 28f).toInt().coerceAtLeast(60)
        val layout = StaticLayout.Builder.obtain(node.title, 0, node.title.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(if (node.level == 0) 3 else 4)
            .build()
        canvas.save()
        canvas.translate(positioned.x + 14f, positioned.y + 13f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun writePng(context: Context, map: StudyMap, bitmap: Bitmap): File {
        val directory = exportDirectory(context)
        val file = File(directory, "${safeName(map.title)}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }

    private fun writePdf(context: Context, map: StudyMap, bitmap: Bitmap, landscape: Boolean): File {
        val directory = exportDirectory(context)
        val file = File(directory, "${safeName(map.title)}_${System.currentTimeMillis()}.pdf")
        val pageWidth = if (landscape) 842 else 595
        val pageHeight = if (landscape) 595 else 842
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val pageCanvas = page.canvas
        pageCanvas.drawColor(Color.WHITE)
        val target = fitRect(bitmap.width.toFloat(), bitmap.height.toFloat(), pageWidth.toFloat(), pageHeight.toFloat(), 26f)
        pageCanvas.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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

    private fun exportDirectory(context: Context): File =
        File(context.filesDir, "documents/maps").apply { mkdirs() }

    private fun safeName(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ_-]+"), "_")
        .trim('_')
        .take(60)
        .ifBlank { "mapa_notcan" }

    private data class ExportPalette(
        val background: Int,
        val card: Int,
        val root: Int,
        val text: Int,
        val muted: Int,
        val border: Int,
        val connector: Int
    )

    private fun palette(theme: StudyMapExportTheme): ExportPalette = when (theme) {
        StudyMapExportTheme.LIGHT -> ExportPalette(
            background = Color.rgb(250, 251, 253),
            card = Color.WHITE,
            root = Color.rgb(232, 240, 255),
            text = Color.rgb(24, 31, 43),
            muted = Color.rgb(92, 104, 122),
            border = Color.rgb(214, 221, 232),
            connector = Color.rgb(52, 120, 246)
        )
        StudyMapExportTheme.DARK -> ExportPalette(
            background = Color.rgb(8, 13, 21),
            card = Color.rgb(19, 29, 42),
            root = Color.rgb(20, 43, 82),
            text = Color.rgb(244, 247, 251),
            muted = Color.rgb(167, 178, 195),
            border = Color.rgb(38, 54, 76),
            connector = Color.rgb(52, 120, 246)
        )
    }
}
