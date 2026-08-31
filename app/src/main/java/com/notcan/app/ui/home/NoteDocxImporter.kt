package com.notcan.app.ui.home

import android.text.Html
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Convierte DOCX al subconjunto HTML entendido por WriterNoteEditor.
 * Conserva estructura y formato visual frecuente sin convertir el documento en texto plano.
 */
internal object NoteDocxImporter {

    fun toHtml(input: InputStream): String {
        val parts = readDocxParts(input)
        val xml = parts["word/document.xml"] ?: return ""
        val paragraphs = Regex("(?is)<w:p(?:\\s[^>]*)?>(.*?)</w:p>").findAll(xml)
            .map { it.groupValues[1] }
            .toList()

        return buildString {
            paragraphs.forEachIndexed { index, paragraphXml ->
                val properties = Regex("(?is)<w:pPr(?:\\s[^>]*)?>(.*?)</w:pPr>")
                    .find(paragraphXml)?.groupValues?.get(1).orEmpty()
                val style = Regex("(?is)<w:pStyle[^>]*w:val=\"([^\"]+)\"[^>]*/?>")
                    .find(properties)?.groupValues?.get(1).orEmpty()
                val numbered = Regex("(?is)<w:numPr[\\s\\S]*?</w:numPr>").containsMatchIn(properties)
                val numId = Regex("(?is)<w:numId[^>]*w:val=\"([^\"]+)\"[^>]*/?>")
                    .find(properties)?.groupValues?.get(1)
                val level = Regex("(?is)<w:ilvl[^>]*w:val=\"(\\d+)\"[^>]*/?>")
                    .find(properties)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val runs = Regex("(?is)<w:r(?:\\s[^>]*)?>(.*?)</w:r>").findAll(paragraphXml)
                    .joinToString("") { runToHtml(it.groupValues[1]) }
                val hyperlinks = Regex("(?is)<w:hyperlink[^>]*>(.*?)</w:hyperlink>").findAll(paragraphXml)
                    .joinToString("") { link ->
                        Regex("(?is)<w:r(?:\\s[^>]*)?>(.*?)</w:r>").findAll(link.groupValues[1])
                            .joinToString("") { runToHtml(it.groupValues[1]) }
                    }
                val contentRaw = if (runs.isNotBlank()) runs else hyperlinks

                if (contentRaw.isBlank()) {
                    append("<p><br></p>")
                    return@forEachIndexed
                }

                val tag = when {
                    style.contains("Heading1", true) || style.contains("Título1", true) || style == "1" -> "h1"
                    style.contains("Heading2", true) || style.contains("Título2", true) || style == "2" -> "h2"
                    else -> "p"
                }

                val paragraphStyle = paragraphCss(properties)
                val listPrefix = if (numbered) {
                    val ordered = isProbablyOrderedList(parts["word/numbering.xml"], numId)
                    val indent = "&nbsp;&nbsp;".repeat(level.coerceIn(0, 6))
                    if (ordered) "$indent${index + 1}. " else "$indent• "
                } else ""

                append("<$tag")
                if (paragraphStyle.isNotEmpty()) append(" style=\"${paragraphStyle.joinToString(";")}\"")
                append(">")
                append(listPrefix)
                append(contentRaw)
                append("</$tag>")
            }
        }.ifBlank { "<p></p>" }
    }

    private fun readDocxParts(input: InputStream): Map<String, String> {
        val parts = mutableMapOf<String, String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in setOf(
                        "word/document.xml",
                        "word/styles.xml",
                        "word/numbering.xml"
                    )
                ) {
                    parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return parts
    }

    private fun paragraphCss(properties: String): List<String> = buildList {
        val align = Regex("(?is)<w:jc[^>]*w:val=\"([^\"]+)\"[^>]*/?>")
            .find(properties)?.groupValues?.get(1)?.lowercase()
        when (align) {
            "center" -> add("text-align:center")
            "right", "end" -> add("text-align:right")
            "both", "distribute" -> add("text-align:justify")
            "left", "start" -> add("text-align:left")
        }

        val left = twipsToPt(attribute(properties, "w:ind", "w:left") ?: attribute(properties, "w:ind", "w:start"))
        val right = twipsToPt(attribute(properties, "w:ind", "w:right") ?: attribute(properties, "w:ind", "w:end"))
        val first = twipsToPt(attribute(properties, "w:ind", "w:firstLine"))
        val hanging = twipsToPt(attribute(properties, "w:ind", "w:hanging"))
        left?.let { add("margin-left:${formatPt(it)}pt") }
        right?.let { add("margin-right:${formatPt(it)}pt") }
        when {
            first != null -> add("text-indent:${formatPt(first)}pt")
            hanging != null -> add("text-indent:-${formatPt(hanging)}pt")
        }

        twipsToPt(attribute(properties, "w:spacing", "w:before"))?.let { add("margin-top:${formatPt(it)}pt") }
        twipsToPt(attribute(properties, "w:spacing", "w:after"))?.let { add("margin-bottom:${formatPt(it)}pt") }
        val line = attribute(properties, "w:spacing", "w:line")?.toDoubleOrNull()
        if (line != null && line > 0) add("line-height:${"%.2f".format(java.util.Locale.US, line / 240.0)}")
    }

    private fun runToHtml(runXml: String): String {
        val properties = Regex("(?is)<w:rPr(?:\\s[^>]*)?>(.*?)</w:rPr>")
            .find(runXml)?.groupValues?.get(1).orEmpty()
        val text = buildString {
            Regex("(?is)<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab\\s*/>|<w:br(?:\\s[^>]*)?/>|<w:cr\\s*/>")
                .findAll(runXml)
                .forEach { match ->
                    when {
                        match.value.startsWith("<w:tab", true) -> append("&emsp;")
                        match.value.startsWith("<w:br", true) || match.value.startsWith("<w:cr", true) -> append("<br>")
                        else -> append(escapeXmlText(match.groupValues.getOrNull(1).orEmpty()))
                    }
                }
        }
        if (text.isBlank()) return ""

        var result = text
        if (hasOnProperty(properties, "b")) result = "<strong>$result</strong>"
        if (hasOnProperty(properties, "i")) result = "<em>$result</em>"
        if (Regex("(?is)<w:u[^>]*w:val=\"(?!none)[^\"]+\"[^>]*/?>").containsMatchIn(properties)) result = "<u>$result</u>"
        if (hasOnProperty(properties, "strike")) result = "<s>$result</s>"
        if (hasOnProperty(properties, "vertAlign") && properties.contains("superscript", true)) result = "<sup>$result</sup>"
        if (hasOnProperty(properties, "vertAlign") && properties.contains("subscript", true)) result = "<sub>$result</sub>"

        val color = Regex("(?is)<w:color[^>]*w:val=\"([0-9A-Fa-f]{6})\"[^>]*/?>")
            .find(properties)?.groupValues?.get(1)
        val highlight = Regex("(?is)<w:(?:highlight[^>]*w:val=\"([^\"]+)\"|shd[^>]*w:fill=\"([0-9A-Fa-f]{6})\")[^>]*/?>")
            .find(properties)
        val highlightValue = highlight?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        val sizeHalfPoints = Regex("(?is)<w:sz[^>]*w:val=\"(\\d+)\"[^>]*/?>")
            .find(properties)?.groupValues?.get(1)?.toDoubleOrNull()
        val font = Regex("(?is)<w:rFonts[^>]*(?:w:ascii|w:hAnsi)=\"([^\"]+)\"[^>]*/?>")
            .find(properties)?.groupValues?.get(1)

        val styles = buildList {
            color?.let { hex ->
                val rgb = hex.toIntOrNull(16)
                if (rgb != null) {
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
                    // Word usually stores ordinary body text as #000000. Keeping it would make the
                    // imported note look empty on NotCan's dark editor, so dark/default colours inherit
                    // the editor foreground. Bright intentional colours are preserved.
                    if (luminance >= 105.0) add("color:#${hex.uppercase()}")
                }
            }
            highlightValue?.let { value ->
                val css = when (value.lowercase()) {
                    "yellow" -> "#FFE066"
                    "green" -> "#8EE39A"
                    "cyan", "blue" -> "#7EC8FF"
                    "magenta", "pink" -> "#FF9BB8"
                    "darkblue" -> "#3159A7"
                    "darkgreen" -> "#397A4A"
                    "gray", "lightgray" -> "#B8BEC8"
                    else -> if (value.matches(Regex("[0-9A-Fa-f]{6}"))) "#$value" else null
                }
                css?.let { add("background-color:$it") }
            }
            sizeHalfPoints?.let { add("font-size:${formatPt(it / 2.0)}pt") }
            font?.takeIf { it.length <= 60 }?.let { add("font-family:'${escapeCss(it)}',sans-serif") }
        }
        if (styles.isNotEmpty()) result = "<span style=\"${styles.joinToString(";")}\">$result</span>"
        return result
    }

    private fun isProbablyOrderedList(numberingXml: String?, numId: String?): Boolean {
        if (numberingXml.isNullOrBlank() || numId.isNullOrBlank()) return false
        val decimalFormats = listOf("decimal", "lowerLetter", "upperLetter", "lowerRoman", "upperRoman")
        return decimalFormats.any { numberingXml.contains("w:val=\"$it\"", ignoreCase = true) }
    }

    private fun hasOnProperty(xml: String, tag: String): Boolean {
        val match = Regex("(?is)<w:$tag(?:\\s[^>]*)?/?>(?:</w:$tag>)?").find(xml) ?: return false
        return !match.value.contains("w:val=\"0\"", true) && !match.value.contains("w:val=\"false\"", true)
    }

    private fun attribute(xml: String, element: String, attr: String): String? =
        Regex("(?is)<$element[^>]*$attr=\"([^\"]+)\"[^>]*/?>").find(xml)?.groupValues?.get(1)

    private fun twipsToPt(raw: String?): Double? = raw?.toDoubleOrNull()?.div(20.0)
    private fun formatPt(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
    private fun escapeCss(value: String): String = value.replace("'", "").replace("\"", "").replace(";", "")

    private fun escapeXmlText(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        return decoded
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
