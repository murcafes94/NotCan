package com.notcan.app.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object NoteFileExport {

    enum class Format(val extension: String, val mime: String) {
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PDF("pdf", "application/pdf"),
        TEXT("txt", "text/plain")
    }

    fun share(context: Context, title: String, html: String, format: Format) {
        val safeTitle = sanitizeFileName(title.ifBlank { "Apuntes" })
        val dir = File(context.filesDir, "documents/exports").apply { mkdirs() }
        val file = File(dir, "$safeTitle.${format.extension}")
        when (format) {
            Format.DOCX -> writeDocx(file, title, html)
            Format.PDF -> writePdf(file, title, html)
            Format.TEXT -> file.writeText("${title.ifBlank { "Apuntes" }}\n\n${plainText(html)}", Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(format.mime)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Compartir apuntes como ${format.extension.uppercase()}"))
    }

    private fun writeDocx(file: File, title: String, html: String) {
        val blocks = htmlBlocks(html)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putText("[Content_Types].xml", CONTENT_TYPES)
            zip.putText("_rels/.rels", ROOT_RELS)
            zip.putText("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.putText("word/styles.xml", STYLES)
            val body = buildString {
                append(paragraphXml(title.ifBlank { "Apuntes" }, "Title", emptyList()))
                blocks.forEach { block ->
                    val style = when (block.tag) {
                        "h1" -> "Heading1"
                        "h2" -> "Heading2"
                        else -> null
                    }
                    append(paragraphXml(block.text.toString(), style, block.runs))
                }
                append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>")
            }
            zip.putText(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>""".trimIndent()
            )
        }
    }

    private fun paragraphXml(text: String, style: String?, runs: List<RunStyle>): String {
        val pPr = style?.let { "<w:pPr><w:pStyle w:val=\"$it\"/></w:pPr>" }.orEmpty()
        if (text.isBlank()) return "<w:p>$pPr<w:r><w:t></w:t></w:r></w:p>"
        val resolvedRuns = if (runs.isEmpty()) listOf(RunStyle(0, text.length)) else runs
        val runXml = resolvedRuns.joinToString("") { run ->
            val start = run.start.coerceIn(0, text.length)
            val end = run.end.coerceIn(start, text.length)
            if (start == end) return@joinToString ""
            val props = buildString {
                if (run.bold) append("<w:b/>")
                if (run.italic) append("<w:i/>")
                if (run.underline) append("<w:u w:val=\"single\"/>")
                if (run.strike) append("<w:strike/>")
                run.foreground?.let { append("<w:color w:val=\"${rgbHex(it)}\"/>") }
                run.background?.let { append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${rgbHex(it)}\"/>") }
            }
            "<w:r>${if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else ""}<w:t xml:space=\"preserve\">${xmlEscape(text.substring(start, end))}</w:t></w:r>"
        }
        return "<w:p>$pPr$runXml</w:p>"
    }

    private fun writePdf(file: File, title: String, html: String) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 46
        val contentWidth = pageWidth - margin * 2
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 15f
        }
        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        var pageNumber = 1
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = margin.toFloat()

        fun newPage() {
            page?.let { document.finishPage(it) }
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create())
            canvas = page!!.canvas.apply { drawColor(Color.WHITE) }
            y = margin.toFloat()
        }

        fun drawLayout(layout: StaticLayout, extraBottom: Float = 12f) {
            if (page == null) newPage()
            if (y + layout.height > pageHeight - margin) newPage()
            canvas!!.save()
            canvas!!.translate(margin.toFloat(), y)
            layout.draw(canvas)
            canvas!!.restore()
            y += layout.height + extraBottom
        }

        newPage()
        drawLayout(staticLayout(title.ifBlank { "Apuntes" }, titlePaint, contentWidth), 20f)
        htmlBlocks(html).forEach { block ->
            val p = TextPaint(paint)
            if (block.tag == "h1") {
                p.textSize = 20f
                p.typeface = Typeface.DEFAULT_BOLD
            } else if (block.tag == "h2") {
                p.textSize = 17f
                p.typeface = Typeface.DEFAULT_BOLD
            }
            drawLayout(staticLayout(block.text, p, contentWidth), if (block.tag.startsWith("h")) 12f else 8f)
        }
        page?.let { document.finishPage(it) }
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun staticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(3f, 1.08f)
            .build()

    private data class HtmlBlock(val tag: String, val text: Spanned, val runs: List<RunStyle>)
    private data class RunStyle(
        val start: Int,
        val end: Int,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val foreground: Int? = null,
        val background: Int? = null
    )

    private fun htmlBlocks(html: String): List<HtmlBlock> {
        val normalized = html
            .replace(Regex("(?is)<br\\s*/?>"), "<br>")
            .replace(Regex("(?is)</?(div)([^>]*)>"), { m -> if (m.value.startsWith("</")) "</p>" else "<p>" })
        val regex = Regex("(?is)<(h1|h2|p|li)(?:\\s[^>]*)?>(.*?)</\\1>")
        val found = regex.findAll(normalized).map { match ->
            val tag = match.groupValues[1].lowercase()
            val prefix = if (tag == "li") "• " else ""
            val spanned = Html.fromHtml(prefix + match.groupValues[2], Html.FROM_HTML_MODE_LEGACY)
            HtmlBlock(if (tag == "li") "p" else tag, spanned, spansToRuns(spanned))
        }.toList()
        if (found.isNotEmpty()) return found
        val spanned = Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
        return spanned.toString().split('\n').filter { it.isNotBlank() }.map { text ->
            val line = Html.fromHtml(xmlEscape(text), Html.FROM_HTML_MODE_LEGACY)
            HtmlBlock("p", line, spansToRuns(line))
        }
    }

    private fun spansToRuns(spanned: Spanned): List<RunStyle> {
        if (spanned.isEmpty()) return emptyList()
        val boundaries = sortedSetOf(0, spanned.length)
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            boundaries += spanned.getSpanStart(span).coerceAtLeast(0)
            boundaries += spanned.getSpanEnd(span).coerceAtMost(spanned.length)
        }
        return boundaries.zipWithNext().mapNotNull { (start, end) ->
            if (start >= end) return@mapNotNull null
            val spans = spanned.getSpans(start, end, Any::class.java)
            val styles = spans.filterIsInstance<StyleSpan>()
            RunStyle(
                start = start,
                end = end,
                bold = styles.any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC },
                italic = styles.any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC },
                underline = spans.any { it is UnderlineSpan },
                strike = spans.any { it is StrikethroughSpan },
                foreground = spans.filterIsInstance<ForegroundColorSpan>().lastOrNull()?.foregroundColor,
                background = spans.filterIsInstance<BackgroundColorSpan>().lastOrNull()?.backgroundColor
            )
        }
    }

    private fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    private fun rgbHex(color: Int): String = "%02X%02X%02X".format(Color.red(color), Color.green(color), Color.blue(color))
    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun sanitizeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(90).ifBlank { "Apuntes" }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
</w:styles>"""
}
