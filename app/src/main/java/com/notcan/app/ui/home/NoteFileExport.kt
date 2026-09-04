package com.notcan.app.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

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
            Format.DOCX -> writeDocx(context, file, title, html)
            Format.PDF -> writePdf(context, file, title, html)
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

    private fun writeDocx(context: Context, file: File, title: String, html: String) {
        val titleText = title.ifBlank { "Apuntes" }
        val blocks = htmlBlocks(html)
        val bodyAlreadyStartsWithTitle = blocks.firstOrNull()?.text?.trim()?.equals(titleText.trim(), ignoreCase = true) == true
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putText("[Content_Types].xml", CONTENT_TYPES)
            zip.putText("_rels/.rels", ROOT_RELS)
            zip.putText("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.putText("word/styles.xml", STYLES)
            val body = buildString {
                if (!bodyAlreadyStartsWithTitle) {
                    append(paragraphXml(context, titleText, "Title", emptyList(), ParagraphAlignment.LEFT, null, null))
                }
                blocks.forEach { block ->
                    val style = when (block.tag) {
                        "h1" -> "Heading1"
                        "h2" -> "Heading2"
                        else -> null
                    }
                    append(paragraphXml(context, block.text, style, block.runs, block.alignment, block.lineHeight, block.spacingAfterPt))
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

    private fun paragraphXml(
        context: Context,
        text: String,
        style: String?,
        runs: List<RunStyle>,
        alignment: ParagraphAlignment,
        lineHeight: Float?,
        spacingAfterPt: Float?
    ): String {
        val properties = buildString {
            style?.let { append("<w:pStyle w:val=\"$it\"/>") }
            when (alignment) {
                ParagraphAlignment.CENTER -> append("<w:jc w:val=\"center\"/>")
                ParagraphAlignment.RIGHT -> append("<w:jc w:val=\"right\"/>")
                ParagraphAlignment.JUSTIFY -> append("<w:jc w:val=\"both\"/>")
                ParagraphAlignment.LEFT -> Unit
            }
            if (lineHeight != null || spacingAfterPt != null) {
                append("<w:spacing")
                lineHeight?.let { append(" w:line=\"${(it.coerceIn(0.8f, 3f) * 240f).roundToInt()}\" w:lineRule=\"auto\"") }
                spacingAfterPt?.let { append(" w:after=\"${(it.coerceIn(0f, 72f) * 20f).roundToInt()}\"") }
                append("/>")
            }
        }
        val pPr = if (properties.isNotEmpty()) "<w:pPr>$properties</w:pPr>" else ""
        if (text.isBlank()) return "<w:p>$pPr<w:r><w:t></w:t></w:r></w:p>"
        val resolvedRuns = fillRunGaps(text.length, runs)
        val runXml = resolvedRuns.joinToString("") { run ->
            val start = run.start.coerceIn(0, text.length)
            val end = run.end.coerceIn(start, text.length)
            if (start == end) return@joinToString ""
            val props = buildString {
                if (run.bold || (run.fontWeight ?: 400) >= 600) append("<w:b/>")
                if (run.italic) append("<w:i/>")
                if (run.underline) append("<w:u w:val=\"single\"/>")
                if (run.strike) append("<w:strike/>")
                run.foreground?.let { append("<w:color w:val=\"${rgbHex(it)}\"/>") }
                run.background?.let { append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${rgbHex(it)}\"/>") }
                run.fontSizePt?.let { pt ->
                    val halfPoints = (pt * 2f).roundToInt().coerceIn(12, 144)
                    append("<w:sz w:val=\"$halfPoints\"/><w:szCs w:val=\"$halfPoints\"/>")
                }
                run.fontFamily?.takeIf { it.isNotBlank() }?.let { family ->
                    val safe = xmlEscape(exportFontName(context, family))
                    append("<w:rFonts w:ascii=\"$safe\" w:hAnsi=\"$safe\" w:cs=\"$safe\"/>")
                }
                run.letterSpacingEm?.let { em ->
                    val pt = run.fontSizePt ?: 11f
                    append("<w:spacing w:val=\"${(em.coerceIn(-0.2f, 0.5f) * pt * 20f).roundToInt()}\"/>")
                }
            }
            val body = wordTextXml(text.substring(start, end))
            "<w:r>${if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else ""}$body</w:r>"
        }
        return "<w:p>$pPr$runXml</w:p>"
    }

    private fun wordTextXml(value: String): String {
        val parts = value.split('\n')
        return parts.mapIndexed { index, part ->
            val br = if (index > 0) "<w:br/>" else ""
            "$br<w:t xml:space=\"preserve\">${xmlEscape(part)}</w:t>"
        }.joinToString("")
    }

    private fun writePdf(context: Context, file: File, title: String, html: String) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 46
        val contentWidth = pageWidth - margin * 2
        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
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

        fun drawLayout(layout: StaticLayout) {
            canvas!!.save()
            canvas!!.translate(margin.toFloat(), y)
            layout.draw(canvas)
            canvas!!.restore()
            y += layout.height
        }

        fun drawFlowingText(
            source: CharSequence,
            paint: TextPaint,
            alignment: ParagraphAlignment,
            extraBottom: Float,
            lineHeight: Float = 1.08f
        ) {
            var remaining: CharSequence = source
            if (remaining.isEmpty()) remaining = " "
            while (remaining.isNotEmpty()) {
                if (page == null) newPage()
                var available = (pageHeight - margin).toFloat() - y
                if (available < paint.textSize * 1.8f) {
                    newPage()
                    available = (pageHeight - margin).toFloat() - y
                }
                val layout = staticLayout(remaining, paint, contentWidth, alignment, lineHeight)
                if (layout.height <= available) {
                    drawLayout(layout)
                    y += extraBottom
                    break
                }
                var fittingLines = 0
                while (fittingLines < layout.lineCount && layout.getLineBottom(fittingLines) <= available) fittingLines++
                if (fittingLines <= 0) {
                    newPage()
                    continue
                }
                val end = layout.getLineEnd(fittingLines - 1).coerceAtLeast(1)
                val chunk = remaining.subSequence(0, end)
                drawLayout(staticLayout(chunk, paint, contentWidth, alignment, lineHeight))
                remaining = remaining.subSequence(end, remaining.length)
                newPage()
            }
        }

        val titleText = title.ifBlank { "Apuntes" }
        val blocks = htmlBlocks(html)
        val bodyAlreadyStartsWithTitle = blocks.firstOrNull()?.text?.trim()?.equals(titleText.trim(), ignoreCase = true) == true
        newPage()
        if (!bodyAlreadyStartsWithTitle) {
            drawFlowingText(titleText, titlePaint, ParagraphAlignment.LEFT, 18f)
        }
        blocks.forEach { block ->
            val paint = TextPaint(bodyPaint)
            when (block.tag) {
                "h1" -> {
                    paint.textSize = 17f
                    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                "h2" -> {
                    paint.textSize = 14f
                    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
            }
            val richText = styledText(context, block)
            drawFlowingText(richText, paint, block.alignment, block.spacingAfterPt ?: if (block.tag.startsWith("h")) 10f else 7f, block.lineHeight ?: 1.08f)
        }
        page?.let { document.finishPage(it) }
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun staticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: ParagraphAlignment,
        lineHeight: Float
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(
                when (alignment) {
                    ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                    ParagraphAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setIncludePad(false)
            .setLineSpacing(0f, lineHeight.coerceIn(0.8f, 3f))
        if (alignment == ParagraphAlignment.JUSTIFY) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        return builder.build()
    }

    private fun styledText(context: Context, block: HtmlBlock): CharSequence {
        val text = SpannableString(block.text)
        block.runs.forEach { run ->
            val start = run.start.coerceIn(0, text.length)
            val end = run.end.coerceIn(start, text.length)
            if (start >= end) return@forEach
            val flags = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            val requestedWeight = (run.fontWeight ?: if (run.bold) 700 else 400).coerceIn(100, 900)
            val typeface = resolvePdfTypeface(context, run.fontFamily, requestedWeight, run.italic)
            text.setSpan(FontRunSpan(typeface, run.letterSpacingEm), start, end, flags)
            if (run.underline) text.setSpan(UnderlineSpan(), start, end, flags)
            if (run.strike) text.setSpan(StrikethroughSpan(), start, end, flags)
            run.foreground?.let { text.setSpan(ForegroundColorSpan(it), start, end, flags) }
            run.background?.let { text.setSpan(BackgroundColorSpan(it), start, end, flags) }
            run.fontSizePt?.let { text.setSpan(AbsoluteSizeSpan(it.roundToInt().coerceIn(6, 72), false), start, end, flags) }
        }
        return text
    }

    private enum class ParagraphAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

    private data class HtmlBlock(
        val tag: String,
        val text: String,
        val runs: List<RunStyle>,
        val alignment: ParagraphAlignment,
        val lineHeight: Float? = null,
        val spacingAfterPt: Float? = null
    )

    private data class RunStyle(
        val start: Int,
        val end: Int,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val foreground: Int? = null,
        val background: Int? = null,
        val fontSizePt: Float? = null,
        val fontFamily: String? = null,
        val fontWeight: Int? = null,
        val letterSpacingEm: Float? = null
    )

    private data class InlineStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val foreground: Int? = null,
        val background: Int? = null,
        val fontSizePt: Float? = null,
        val fontFamily: String? = null,
        val fontWeight: Int? = null,
        val letterSpacingEm: Float? = null
    )

    private data class ParsedInline(val text: String, val runs: List<RunStyle>)

    private fun htmlBlocks(html: String): List<HtmlBlock> {
        val normalized = html.replace(Regex("(?is)<br\\s*/?>"), "<br>")
        val regex = Regex("(?is)<(h1|h2|p|div|li)(\\s[^>]*)?>(.*?)</\\1>")
        val found = regex.findAll(normalized).map { match ->
            val originalTag = match.groupValues[1].lowercase()
            val attrs = match.groupValues[2]
            val parsed = parseInline(match.groupValues[3])
            val marker = if (originalTag == "li") listMarker(normalized, match.range.first) else null
            val markerText = marker.orEmpty()
            HtmlBlock(
                tag = when (originalTag) {
                    "div", "li" -> "p"
                    else -> originalTag
                },
                text = markerText + parsed.text,
                runs = parsed.runs.map { it.copy(start = it.start + markerText.length, end = it.end + markerText.length) },
                alignment = parseAlignment(attrs),
                lineHeight = parseLineHeight(attrs),
                spacingAfterPt = parseSpacingAfterPt(attrs)
            )
        }.toList()
        if (found.isNotEmpty()) return found
        val plain = plainText(normalized)
        return plain.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }.map {
            HtmlBlock("p", it, emptyList(), ParagraphAlignment.LEFT)
        }
    }

    private fun listMarker(html: String, position: Int): String {
        val before = html.substring(0, position).lowercase(Locale.ROOT)
        val olOpen = before.lastIndexOf("<ol")
        val ulOpen = before.lastIndexOf("<ul")
        val olClose = before.lastIndexOf("</ol")
        val ulClose = before.lastIndexOf("</ul")
        val ordered = olOpen > olClose && olOpen > ulOpen
        if (!ordered) return "• "
        val start = olOpen.coerceAtLeast(0)
        val previousItems = Regex("(?is)<li(?:\\s[^>]*)?>").findAll(before.substring(start)).count()
        return "${previousItems + 1}. "
    }

    private fun parseAlignment(attrs: String): ParagraphAlignment {
        val style = attributeValue(attrs, "style").orEmpty()
        val value = cssProperties(style)["text-align"]?.lowercase(Locale.ROOT)
        return when (value) {
            "center" -> ParagraphAlignment.CENTER
            "right", "end" -> ParagraphAlignment.RIGHT
            "justify" -> ParagraphAlignment.JUSTIFY
            else -> ParagraphAlignment.LEFT
        }
    }

    private fun parseLineHeight(attrs: String): Float? {
        val raw = cssProperties(attributeValue(attrs, "style").orEmpty())["line-height"]?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when {
            raw.endsWith("%") -> raw.removeSuffix("%").toFloatOrNull()?.div(100f)
            raw.endsWith("pt") || raw.endsWith("px") -> null
            else -> raw.toFloatOrNull()
        }?.coerceIn(0.8f, 3f)
    }

    private fun parseSpacingAfterPt(attrs: String): Float? {
        val raw = cssProperties(attributeValue(attrs, "style").orEmpty())["margin-bottom"] ?: return null
        return parseCssLengthPt(raw)?.coerceIn(0f, 72f)
    }

    private fun parseInline(fragment: String): ParsedInline {
        val text = StringBuilder()
        val runs = mutableListOf<RunStyle>()
        var style = InlineStyle()
        val stack = mutableListOf<Pair<String, InlineStyle>>()
        val tokenRegex = Regex("(?is)<(/?)(strong|b|em|i|u|s|strike|span|font|br|sup|sub|a)([^>]*)>|([^<]+)|<[^>]+>")

        fun addText(raw: String) {
            val decoded = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
            if (decoded.isEmpty()) return
            val start = text.length
            text.append(decoded)
            val end = text.length
            if (style != InlineStyle()) {
                runs += RunStyle(
                    start = start,
                    end = end,
                    bold = style.bold,
                    italic = style.italic,
                    underline = style.underline,
                    strike = style.strike,
                    foreground = style.foreground,
                    background = style.background,
                    fontSizePt = style.fontSizePt,
                    fontFamily = style.fontFamily,
                    fontWeight = style.fontWeight,
                    letterSpacingEm = style.letterSpacingEm
                )
            }
        }

        tokenRegex.findAll(fragment).forEach { match ->
            val rawText = match.groupValues[4]
            if (rawText.isNotEmpty()) {
                addText(rawText)
                return@forEach
            }
            val tag = match.groupValues[2].lowercase(Locale.ROOT)
            if (tag.isEmpty()) return@forEach
            val closing = match.groupValues[1] == "/"
            val attrs = match.groupValues[3]
            if (tag == "br") {
                val start = text.length
                text.append('\n')
                if (style != InlineStyle()) {
                    runs += RunStyle(start, text.length, style.bold, style.italic, style.underline, style.strike, style.foreground, style.background, style.fontSizePt, style.fontFamily, style.fontWeight, style.letterSpacingEm)
                }
                return@forEach
            }
            if (closing) {
                var index = stack.lastIndex
                while (index >= 0 && stack[index].first != tag) index--
                if (index >= 0) {
                    style = stack[index].second
                    while (stack.size > index) stack.removeAt(stack.lastIndex)
                }
                return@forEach
            }

            stack += tag to style
            style = when (tag) {
                "strong", "b" -> style.copy(bold = true)
                "em", "i" -> style.copy(italic = true)
                "u" -> style.copy(underline = true)
                "s", "strike" -> style.copy(strike = true)
                "font" -> {
                    var next = style
                    attributeValue(attrs, "color")?.let { parseCssColor(it)?.let { color -> next = next.copy(foreground = color) } }
                    attributeValue(attrs, "face")?.let { next = next.copy(fontFamily = sanitizeFontFamily(it)) }
                    attributeValue(attrs, "size")?.toIntOrNull()?.let { htmlSize -> next = next.copy(fontSizePt = htmlFontSizePt(htmlSize)) }
                    next
                }
                "span", "a" -> applyCss(style, attributeValue(attrs, "style").orEmpty())
                else -> style
            }
        }
        return ParsedInline(text.toString(), mergeAdjacentRuns(runs))
    }

    private fun applyCss(base: InlineStyle, styleText: String): InlineStyle {
        val css = cssProperties(styleText)
        var result = base
        css["color"]?.let { parseCssColor(it)?.let { color -> result = result.copy(foreground = color) } }
        (css["background-color"] ?: css["background"])?.let { parseCssColor(it)?.let { color -> result = result.copy(background = color) } }
        css["font-size"]?.let { parseFontSizePt(it)?.let { size -> result = result.copy(fontSizePt = size) } }
        css["font-family"]?.let { family -> result = result.copy(fontFamily = sanitizeFontFamily(family)) }
        css["font-weight"]?.lowercase(Locale.ROOT)?.let { weight ->
            val numeric = when (weight) {
                "normal" -> 400
                "bold" -> 700
                "lighter" -> 300
                "bolder" -> 700
                else -> weight.toIntOrNull()
            }?.coerceIn(100, 900)
            if (numeric != null) result = result.copy(fontWeight = numeric, bold = result.bold || numeric >= 600)
        }
        css["letter-spacing"]?.let { parseLetterSpacingEm(it)?.let { spacing -> result = result.copy(letterSpacingEm = spacing) } }
        css["font-style"]?.lowercase(Locale.ROOT)?.let { if (it == "italic" || it == "oblique") result = result.copy(italic = true) }
        css["text-decoration"]?.lowercase(Locale.ROOT)?.let { decoration ->
            if ("underline" in decoration) result = result.copy(underline = true)
            if ("line-through" in decoration) result = result.copy(strike = true)
        }
        return result
    }

    private fun cssProperties(style: String): Map<String, String> = style.split(';')
        .mapNotNull { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0) null else entry.substring(0, separator).trim().lowercase(Locale.ROOT) to entry.substring(separator + 1).trim()
        }
        .toMap()

    private fun attributeValue(attrs: String, name: String): String? {
        val quoted = Regex("(?is)\\b${Regex.escape(name)}\\s*=\\s*(['\"])(.*?)\\1").find(attrs)
        if (quoted != null) return quoted.groupValues[2]
        return Regex("(?is)\\b${Regex.escape(name)}\\s*=\\s*([^\\s>]+)").find(attrs)?.groupValues?.get(1)
    }

    private fun parseFontSizePt(value: String): Float? {
        val raw = value.trim().lowercase(Locale.ROOT)
        val number = Regex("[-+]?[0-9]*\\.?[0-9]+").find(raw)?.value?.toFloatOrNull() ?: return null
        return when {
            raw.endsWith("pt") -> number
            raw.endsWith("px") -> number * 0.75f
            raw.endsWith("em") -> number * 11f
            raw.endsWith("rem") -> number * 11f
            else -> number
        }.coerceIn(6f, 72f)
    }


    private fun parseCssLengthPt(value: String): Float? {
        val raw = value.trim().lowercase(Locale.ROOT)
        val number = Regex("[-+]?[0-9]*\\.?[0-9]+").find(raw)?.value?.toFloatOrNull() ?: return null
        return when {
            raw.endsWith("px") -> number * 0.75f
            raw.endsWith("em") || raw.endsWith("rem") -> number * 11f
            else -> number
        }
    }

    private fun parseLetterSpacingEm(value: String): Float? {
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw == "normal") return 0f
        val number = Regex("[-+]?[0-9]*\\.?[0-9]+").find(raw)?.value?.toFloatOrNull() ?: return null
        return when {
            raw.endsWith("em") -> number
            raw.endsWith("rem") -> number
            raw.endsWith("pt") -> number / 11f
            raw.endsWith("px") -> (number * 0.75f) / 11f
            else -> number
        }.coerceIn(-0.2f, 0.5f)
    }

    private fun htmlFontSizePt(size: Int): Float = when (size.coerceIn(1, 7)) {
        1 -> 8f
        2 -> 10f
        3 -> 12f
        4 -> 14f
        5 -> 18f
        6 -> 24f
        else -> 36f
    }

    private fun parseCssColor(value: String): Int? {
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw == "transparent" || raw == "inherit" || raw == "initial") return null
        Regex("rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").find(raw)?.let { match ->
            val r = match.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return@let
            val g = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return@let
            val b = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return@let
            return Color.rgb(r, g, b)
        }
        return runCatching { Color.parseColor(raw) }.getOrNull()
    }

    private fun sanitizeFontFamily(value: String): String = value
        .substringBefore(',')
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .replace(Regex("[;<>]"), "")
        .take(60)
        .ifBlank { "sans-serif" }

    private fun exportFontName(context: Context, value: String): String = LocalFontStore.exportName(context, value) ?: when {
        value.contains("mono", true) || value.contains("courier", true) -> "Courier New"
        value.contains("georgia", true) -> "Georgia"
        value.contains("times", true) || value.equals("serif", true) -> "Times New Roman"
        value.contains("arial", true) -> "Arial"
        value.contains("noto serif", true) -> "Noto Serif"
        value.contains("noto sans", true) -> "Noto Sans"
        value.contains("roboto", true) || value.contains("sans", true) -> "Roboto"
        else -> value
    }

    private fun pdfSystemFamily(value: String?): String = when {
        value?.contains("mono", true) == true || value?.contains("courier", true) == true -> "monospace"
        value?.contains("georgia", true) == true || value?.contains("times", true) == true || value.equals("serif", true) -> "serif"
        value?.contains("cursive", true) == true -> "cursive"
        else -> "sans-serif"
    }

    private fun resolvePdfTypeface(context: Context, family: String?, weight: Int, italic: Boolean): Typeface {
        val base = LocalFontStore.resolveTypeface(context, family) ?: Typeface.create(pdfSystemFamily(family), Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, weight.coerceIn(100, 900), italic)
        } else {
            val style = when {
                weight >= 600 && italic -> Typeface.BOLD_ITALIC
                weight >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, style)
        }
    }

    private class FontRunSpan(
        private val typeface: Typeface,
        private val letterSpacingEm: Float?
    ) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
        override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
        private fun apply(paint: TextPaint) {
            paint.typeface = typeface
            letterSpacingEm?.let { paint.letterSpacing = it.coerceIn(-0.2f, 0.5f) }
        }
    }

    private fun mergeAdjacentRuns(runs: List<RunStyle>): List<RunStyle> {
        if (runs.isEmpty()) return emptyList()
        val sorted = runs.sortedBy { it.start }
        val out = mutableListOf<RunStyle>()
        sorted.forEach { run ->
            val previous = out.lastOrNull()
            if (previous != null && previous.end == run.start && previous.copy(end = run.end) == run.copy(start = previous.start)) {
                out[out.lastIndex] = previous.copy(end = run.end)
            } else {
                out += run
            }
        }
        return out
    }

    private fun fillRunGaps(length: Int, styledRuns: List<RunStyle>): List<RunStyle> {
        if (length <= 0) return emptyList()
        if (styledRuns.isEmpty()) return listOf(RunStyle(0, length))
        val sorted = styledRuns.sortedBy { it.start }
        val result = mutableListOf<RunStyle>()
        var cursor = 0
        sorted.forEach { run ->
            val start = run.start.coerceIn(0, length)
            val end = run.end.coerceIn(start, length)
            if (start > cursor) result += RunStyle(cursor, start)
            if (end > start) result += run.copy(start = start, end = end)
            cursor = maxOf(cursor, end)
        }
        if (cursor < length) result += RunStyle(cursor, length)
        return result
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
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:b/><w:sz w:val="36"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:b/><w:sz w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:b/><w:sz w:val="26"/></w:rPr></w:style>
</w:styles>"""
}
