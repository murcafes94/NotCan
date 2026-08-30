package com.notcan.app.sources

import android.content.Context
import android.text.Html
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.zip.ZipFile

/**
 * Local text extraction for TuNot sources.
 *
 * The original file remains the canonical source. Extracted text is written to a sidecar
 * `<source>.index.txt` and is never shown as an editable note.
 */
object SourceTextIndexer {

    fun index(context: Context, source: File, documentType: String): File? {
        if (!source.exists()) return null
        val text = when (documentType.uppercase()) {
            "PDF" -> extractPdf(context, source)
            "DOCX" -> extractDocx(source)
            "EPUB" -> extractEpub(source)
            else -> ""
        }.normalizeForSearch()

        if (text.isBlank()) return null
        val indexFile = indexFileFor(source)
        indexFile.writeText(text, Charsets.UTF_8)
        return indexFile
    }

    fun indexFileFor(source: File): File = File(source.absolutePath + ".index.txt")

    fun readIndex(source: File, maxChars: Int = 180_000): String {
        val index = indexFileFor(source)
        if (!index.exists()) return ""
        return index.bufferedReader(Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buffer = CharArray(8_192)
            while (out.length < maxChars) {
                val read = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
                if (read <= 0) break
                out.append(buffer, 0, read)
            }
            out.toString()
        }
    }

    private fun extractPdf(context: Context, file: File): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(file).use { document ->
            PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                paragraphStart = ""
                paragraphEnd = "\n"
            }.getText(document)
        }
    }

    private fun extractDocx(file: File): String = ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return@use ""
        val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        xml
            .replace(Regex("(?i)</w:p>"), "\n")
            .replace(Regex("(?i)<w:tab[^>]*/>"), "\t")
            .replace(Regex("<[^>]+>"), "")
            .decodeHtmlEntities()
    }

    private fun extractEpub(file: File): String = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory }
            .filter {
                val name = it.name.lowercase()
                name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")
            }
            .sortedBy { it.name }
            .toList()

        buildString {
            entries.forEach { entry ->
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                if (plain.isNotBlank()) {
                    appendLine("\n## ${entry.name.substringAfterLast('/').substringBeforeLast('.')}\n")
                    appendLine(plain)
                }
            }
        }
    }

    private fun String.decodeHtmlEntities(): String = Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun String.normalizeForSearch(): String =
        replace("\u0000", "")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
