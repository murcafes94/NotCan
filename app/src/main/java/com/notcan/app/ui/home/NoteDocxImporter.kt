package com.notcan.app.ui.home

import android.text.Html
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Converts a DOCX into the HTML subset understood by WriterNoteEditor. */
internal object NoteDocxImporter {

    fun toHtml(input: InputStream): String {
        val xml = readDocumentXml(input) ?: return ""
        val paragraphs = Regex("(?is)<w:p(?:\\s[^>]*)?>(.*?)</w:p>").findAll(xml).map { it.groupValues[1] }.toList()
        return buildString {
            paragraphs.forEach { paragraphXml ->
                val style = Regex("(?is)<w:pStyle[^>]*w:val=\"([^\"]+)\"[^>]*/?>").find(paragraphXml)?.groupValues?.get(1).orEmpty()
                val numbered = Regex("(?is)<w:numPr[\\s\\S]*?</w:numPr>").containsMatchIn(paragraphXml)
                val runs = Regex("(?is)<w:r(?:\\s[^>]*)?>(.*?)</w:r>").findAll(paragraphXml).map { runMatch ->
                    runToHtml(runMatch.groupValues[1])
                }.joinToString("")
                val content = if (numbered && runs.isNotBlank()) "• $runs" else runs
                if (content.isBlank()) {
                    append("<p><br></p>")
                } else {
                    val tag = when {
                        style.contains("Heading1", true) || style.contains("Título1", true) || style == "1" -> "h1"
                        style.contains("Heading2", true) || style.contains("Título2", true) || style == "2" -> "h2"
                        else -> "p"
                    }
                    append("<$tag>$content</$tag>")
                }
            }
        }.ifBlank { "<p></p>" }
    }

    private fun readDocumentXml(input: InputStream): String? {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    return zip.bufferedReader(Charsets.UTF_8).readText()
                }
                zip.closeEntry()
            }
        }
        return null
    }

    private fun runToHtml(runXml: String): String {
        val properties = Regex("(?is)<w:rPr(?:\\s[^>]*)?>(.*?)</w:rPr>").find(runXml)?.groupValues?.get(1).orEmpty()
        val text = buildString {
            Regex("(?is)<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab\\s*/>|<w:br\\s*/>").findAll(runXml).forEach { match ->
                when {
                    match.value.startsWith("<w:tab", true) -> append("&emsp;")
                    match.value.startsWith("<w:br", true) -> append("<br>")
                    else -> append(escapeXmlText(match.groupValues.getOrNull(1).orEmpty()))
                }
            }
        }
        if (text.isBlank()) return ""

        var result = text
        if (Regex("(?is)<w:b(?:\\s[^>]*)?/?>(?:</w:b>)?").containsMatchIn(properties)) result = "<strong>$result</strong>"
        if (Regex("(?is)<w:i(?:\\s[^>]*)?/?>(?:</w:i>)?").containsMatchIn(properties)) result = "<em>$result</em>"
        if (Regex("(?is)<w:u[^>]*w:val=\"(?!none)[^\"]+\"[^>]*/?>").containsMatchIn(properties)) result = "<u>$result</u>"
        if (Regex("(?is)<w:strike(?:\\s[^>]*)?/?>(?:</w:strike>)?").containsMatchIn(properties)) result = "<s>$result</s>"

        val color = Regex("(?is)<w:color[^>]*w:val=\"([0-9A-Fa-f]{6})\"[^>]*/?>").find(properties)?.groupValues?.get(1)
        val highlight = Regex("(?is)<w:(?:highlight[^>]*w:val=\"([^\"]+)\"|shd[^>]*w:fill=\"([0-9A-Fa-f]{6})\")[^>]*/?>").find(properties)
        val highlightValue = highlight?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        val styles = buildList {
            color?.let { add("color:#${it.uppercase()}") }
            highlightValue?.let { value ->
                val css = when (value.lowercase()) {
                    "yellow" -> "#FFE066"
                    "green" -> "#8EE39A"
                    "cyan", "blue" -> "#7EC8FF"
                    "magenta", "pink" -> "#FF9BB8"
                    else -> if (value.matches(Regex("[0-9A-Fa-f]{6}"))) "#$value" else null
                }
                css?.let { add("background-color:$it") }
            }
        }
        if (styles.isNotEmpty()) result = "<span style=\"${styles.joinToString(";")}\">$result</span>"
        return result
    }

    private fun escapeXmlText(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        return decoded
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
