package com.notcan.app.sources

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * File-backed library used by TuNot.
 *
 * Sources are intentionally independent from editable notes. PDF, DOCX and EPUB sources may be
 * copied into app-private storage or referenced through a persisted URI. TuNot stores extracted text
 * sidecars for search/RAG while the original document remains the canonical source.
 */
class ClassSourceStore(private val context: Context) {

    data class SourceItem(
        val id: String,
        val scopeKey: String,
        val displayName: String,
        val type: String,
        val mimeType: String,
        val localPath: String,
        val createdAtEpochMs: Long,
        val indexed: Boolean,
        val enabled: Boolean = true,
        val indexChars: Int = 0,
        val indexPages: Int = 0,
        val sourceUrl: String? = null,
        val sourceUri: String? = null
    )

    data class SearchHit(
        val sourceId: String,
        val sourceName: String,
        val sourceType: String,
        val excerpt: String,
        val offset: Int,
        val pageNumber: Int? = null
    )

    private data class ContextCandidate(
        val item: SourceItem,
        val chunk: SourceRetrieval.Chunk,
        val score: Double
    )

    fun scopeKey(subjectName: String?, classTitle: String?): String {
        val raw = listOfNotNull(subjectName?.trim(), classTitle?.trim()).joinToString("|")
            .ifBlank { "global" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun list(scopeKey: String): List<SourceItem> {
        val manifest = readManifest(scopeKey)
        return (0 until manifest.length()).mapNotNull { index ->
            manifest.optJSONObject(index)?.toSourceItem()
        }.sortedByDescending { it.createdAtEpochMs }
    }

    fun import(scopeKey: String, uri: Uri): SourceItem {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: "fuente_${System.currentTimeMillis()}"
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { guessMime(displayName) }
        val type = resolveType(displayName, mimeType)
        require(type in SUPPORTED_TYPES) { "Solo se admiten PDF, DOCX y EPUB como fuentes" }

        val id = UUID.randomUUID().toString()
        val dir = scopeDir(scopeKey).apply { mkdirs() }
        val file = File(dir, "${id.take(8)}_${sanitize(displayName)}")
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        } ?: error("No se pudo leer el archivo")

        val indexFile = runCatching { SourceTextIndexer.index(context, file, type) }.getOrNull()
        val item = SourceItem(
            id = id,
            scopeKey = scopeKey,
            displayName = displayName,
            type = type,
            mimeType = mimeType,
            localPath = file.absolutePath,
            createdAtEpochMs = System.currentTimeMillis(),
            indexed = indexFile?.exists() == true,
            indexChars = indexFile?.length()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            indexPages = SourceTextIndexer.indexedPageCount(indexFile)
        )
        saveItem(item)
        return item
    }

    fun importReference(
        scopeKey: String,
        uri: Uri,
        displayNameOverride: String? = null,
        mimeTypeOverride: String? = null
    ): SourceItem {
        val resolver = context.contentResolver
        val rawUri = uri.toString()
        list(scopeKey).firstOrNull { it.sourceUri == rawUri }?.let { existing ->
            return reindex(existing)
        }

        val displayName = displayNameOverride?.takeIf { it.isNotBlank() } ?:
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.takeIf { it.isNotBlank() } ?: "fuente_${System.currentTimeMillis()}"
        val mimeType = mimeTypeOverride?.takeIf { it.isNotBlank() }
            ?: resolver.getType(uri).orEmpty().ifBlank { guessMime(displayName) }
        val type = resolveType(displayName, mimeType)
        require(type in SUPPORTED_TYPES) { "Solo se admiten PDF, DOCX y EPUB como fuentes" }

        val id = UUID.randomUUID().toString()
        val dir = scopeDir(scopeKey).apply { mkdirs() }
        val descriptor = File(dir, "${id.take(8)}_${sanitize(displayName)}.ref")
        descriptor.writeText(rawUri, Charsets.UTF_8)
        val item = SourceItem(
            id = id,
            scopeKey = scopeKey,
            displayName = displayName,
            type = type,
            mimeType = mimeType,
            localPath = descriptor.absolutePath,
            createdAtEpochMs = System.currentTimeMillis(),
            indexed = false,
            sourceUri = rawUri
        )
        val indexed = reindexReference(item)
        val finalItem = item.copy(
            indexed = indexed?.exists() == true,
            indexChars = indexed?.length()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            indexPages = SourceTextIndexer.indexedPageCount(indexed)
        )
        saveItem(finalItem)
        return finalItem
    }

    fun importWeb(scopeKey: String, title: String, url: String, content: String): SourceItem {
        require(url.startsWith("https://") || url.startsWith("http://")) { "URL web no válida" }
        val cleanTitle = title.trim().ifBlank { url }.take(180)
        val cleanText = content
            .replace("\u0000", "")
            .replace(Regex("[\t ]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(180_000)
        require(cleanText.isNotBlank()) { "La página no contiene texto legible" }

        val id = UUID.randomUUID().toString()
        val dir = scopeDir(scopeKey).apply { mkdirs() }
        val file = File(dir, "${id.take(8)}_${sanitize(cleanTitle)}.web.txt")
        val indexedText = buildString {
            appendLine("TÍTULO: $cleanTitle")
            appendLine("URL: $url")
            appendLine("FECHA DE CONSULTA: ${java.time.Instant.now()}")
            appendLine()
            append(cleanText)
        }
        file.writeText(indexedText, Charsets.UTF_8)
        SourceTextIndexer.indexFileFor(file).writeText(indexedText, Charsets.UTF_8)
        val item = SourceItem(
            id = id,
            scopeKey = scopeKey,
            displayName = cleanTitle,
            type = "WEB",
            mimeType = "text/plain",
            localPath = file.absolutePath,
            createdAtEpochMs = System.currentTimeMillis(),
            indexed = true,
            enabled = true,
            indexChars = indexedText.length,
            indexPages = 0,
            sourceUrl = url
        )
        saveItem(item)
        return item
    }

    fun reindex(item: SourceItem): SourceItem {
        val file = File(item.localPath)
        val index = when {
            !item.sourceUri.isNullOrBlank() -> reindexReference(item)
            item.type == "WEB" && file.exists() -> {
                SourceTextIndexer.indexFileFor(file).also { it.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8) }
            }
            else -> SourceTextIndexer.index(context, file, item.type)
        }
        val updated = item.copy(
            indexed = index?.exists() == true,
            indexChars = index?.length()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            indexPages = SourceTextIndexer.indexedPageCount(index)
        )
        saveItem(updated)
        return updated
    }

    fun deleteByUri(scopeKey: String, rawUri: String) {
        list(scopeKey).filter { it.sourceUri == rawUri }.forEach { delete(scopeKey, it.id) }
    }

    private fun reindexReference(item: SourceItem): File? {
        val rawUri = item.sourceUri?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        val descriptor = File(item.localPath)
        descriptor.parentFile?.mkdirs()
        if (!descriptor.exists()) descriptor.writeText(rawUri, Charsets.UTF_8)
        val temp = File.createTempFile("tunot_source_", ".bin", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            val tempIndex = SourceTextIndexer.index(context, temp, item.type) ?: return null
            val target = SourceTextIndexer.indexFileFor(descriptor)
            tempIndex.copyTo(target, overwrite = true)
            target
        } finally {
            SourceTextIndexer.indexFileFor(temp).delete()
            temp.delete()
        }
    }

    fun setEnabled(scopeKey: String, sourceId: String, enabled: Boolean) {
        val items = list(scopeKey).map { if (it.id == sourceId) it.copy(enabled = enabled) else it }
        writeManifest(scopeKey, items)
    }

    fun delete(scopeKey: String, sourceId: String) {
        val current = list(scopeKey)
        val target = current.firstOrNull { it.id == sourceId } ?: return
        val source = File(target.localPath)
        SourceTextIndexer.indexFileFor(source).delete()
        source.delete()
        writeManifest(scopeKey, current.filterNot { it.id == sourceId })
    }

    /** Borra el archivo original copiado, su índice y el manifiesto de una clase. */
    fun deleteScope(scopeKey: String): Boolean {
        val dir = scopeDir(scopeKey)
        if (!dir.exists()) return true
        return dir.deleteRecursively()
    }

    /**
     * Search is local and page-aware. It ranks chunks using the phrase and meaningful query terms
     * instead of scanning only for the exact literal string.
     */
    fun search(scopeKey: String, query: String, maxHits: Int = 24): List<SearchHit> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2 || maxHits <= 0) return emptyList()

        val ranked = mutableListOf<Pair<SearchHit, Double>>()
        for (item in list(scopeKey).filter { it.enabled && it.indexed }) {
            val text = SourceTextIndexer.readIndex(File(item.localPath), MAX_INDEX_READ_CHARS)
            if (text.isBlank()) continue
            SourceRetrieval.retrieve(
                indexedText = text,
                query = cleanQuery,
                sourceName = item.displayName,
                maxHits = minOf(maxHits, 8),
                chunkChars = 900
            ).forEach { hit ->
                ranked += SearchHit(
                    sourceId = item.id,
                    sourceName = item.displayName,
                    sourceType = item.type,
                    excerpt = hit.chunk.text,
                    offset = hit.chunk.offset,
                    pageNumber = hit.chunk.pageNumber
                ) to hit.score
            }
        }
        return ranked
            .sortedWith(compareByDescending<Pair<SearchHit, Double>> { it.second }.thenBy { it.first.offset })
            .take(maxHits)
            .map { it.first }
    }

    /**
     * Query-aware context for TuNot. Only the most useful chunks are sent to the model; broad
     * requests receive a spread sample across each document. This substantially reduces prefill,
     * RAM and hallucination pressure compared with concatenating the beginning of every source.
     */
    fun contextForQuery(
        scopeKey: String,
        query: String,
        totalChars: Int = 20_000,
        maxChunks: Int = 12
    ): String {
        if (totalChars <= 0 || maxChunks <= 0) return ""
        val items = list(scopeKey).filter { it.enabled && it.indexed }
        if (items.isEmpty()) return ""

        val broad = SourceRetrieval.isBroadSourceRequest(query)
        val candidates = mutableListOf<ContextCandidate>()
        items.forEach { item ->
            val text = SourceTextIndexer.readIndex(File(item.localPath), MAX_INDEX_READ_CHARS)
            if (text.isBlank()) return@forEach
            if (broad) {
                SourceRetrieval.sample(text, maxChunks = 2).forEachIndexed { index, chunk ->
                    candidates += ContextCandidate(item, chunk, 1.0 - index * 0.01)
                }
            } else {
                SourceRetrieval.retrieve(
                    indexedText = text,
                    query = query,
                    sourceName = item.displayName,
                    maxHits = 5
                ).forEach { ranked ->
                    candidates += ContextCandidate(item, ranked.chunk, ranked.score)
                }
            }
        }

        // If the wording is too generic to score, still provide one bounded sample per source.
        if (candidates.isEmpty()) {
            items.forEach { item ->
                val text = SourceTextIndexer.readIndex(File(item.localPath), MAX_INDEX_READ_CHARS)
                SourceRetrieval.sample(text, maxChunks = 1).firstOrNull()?.let { chunk ->
                    candidates += ContextCandidate(item, chunk, 0.1)
                }
            }
        }

        val ordered = if (broad) candidates else candidates.sortedByDescending { it.score }
        val perSourceCount = mutableMapOf<String, Int>()
        val selected = mutableListOf<ContextCandidate>()
        for (candidate in ordered) {
            if (selected.size >= maxChunks) break
            val used = perSourceCount[candidate.item.id] ?: 0
            if (used >= 3) continue
            selected += candidate
            perSourceCount[candidate.item.id] = used + 1
        }

        val out = StringBuilder()
        for (candidate in selected) {
            if (out.length >= totalChars) break
            val item = candidate.item
            val pageSuffix = candidate.chunk.pageNumber?.let { " | PÁGINA: $it" }.orEmpty()
            val header = "[FUENTE EXTERNA: ${item.displayName} | TIPO: ${item.type}$pageSuffix]"
            val urlLine = item.sourceUrl?.takeIf { it.isNotBlank() }?.let { "URL: $it\n" }.orEmpty()
            val block = "$header\n$urlLine${candidate.chunk.text}\n\n"
            val remaining = totalChars - out.length
            if (remaining <= 0) break
            out.append(block.take(remaining))
        }
        return out.toString().trim()
    }

    /** Context fallback retained for compatibility and explicit full-source operations. */
    fun combinedContext(scopeKey: String, perSourceChars: Int = 32_000, totalChars: Int = 96_000): String {
        val out = StringBuilder()
        for (item in list(scopeKey).filter { it.enabled && it.indexed }) {
            if (out.length >= totalChars) break
            val remaining = totalChars - out.length
            val text = SourceTextIndexer.readIndex(File(item.localPath), minOf(perSourceChars, remaining))
            if (text.isBlank()) continue
            out.appendLine("\n=== FUENTE EXTERNA: ${item.displayName} (${item.type}) ===")
            item.sourceUrl?.takeIf { it.isNotBlank() }?.let { out.appendLine("URL: $it") }
            out.appendLine(text)
        }
        return out.toString().take(totalChars)
    }

    private fun saveItem(item: SourceItem) {
        val current = list(item.scopeKey).toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) current[index] = item else current += item
        writeManifest(item.scopeKey, current)
    }

    private fun readManifest(scopeKey: String): JSONArray = runCatching {
        val file = manifestFile(scopeKey)
        if (!file.exists()) JSONArray() else JSONArray(file.readText(Charsets.UTF_8))
    }.getOrDefault(JSONArray())

    private fun writeManifest(scopeKey: String, items: List<SourceItem>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val file = manifestFile(scopeKey)
        file.parentFile?.mkdirs()
        file.writeText(array.toString(), Charsets.UTF_8)
    }

    private fun manifestFile(scopeKey: String) = File(scopeDir(scopeKey), "manifest.json")
    private fun scopeDir(scopeKey: String) = File(context.filesDir, "tunot_sources/$scopeKey")

    private fun SourceItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("scopeKey", scopeKey)
        .put("displayName", displayName)
        .put("type", type)
        .put("mimeType", mimeType)
        .put("localPath", localPath)
        .put("createdAtEpochMs", createdAtEpochMs)
        .put("indexed", indexed)
        .put("enabled", enabled)
        .put("indexChars", indexChars)
        .put("indexPages", indexPages)
        .put("sourceUrl", sourceUrl)
        .put("sourceUri", sourceUri)

    private fun JSONObject.toSourceItem(): SourceItem? = runCatching {
        SourceItem(
            id = getString("id"),
            scopeKey = getString("scopeKey"),
            displayName = getString("displayName"),
            type = getString("type"),
            mimeType = optString("mimeType"),
            localPath = getString("localPath"),
            createdAtEpochMs = getLong("createdAtEpochMs"),
            indexed = optBoolean("indexed", false),
            enabled = optBoolean("enabled", true),
            indexChars = optInt("indexChars", 0),
            indexPages = optInt("indexPages", 0),
            sourceUrl = optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" },
            sourceUri = optString("sourceUri").takeIf { it.isNotBlank() && it != "null" }
        )
    }.getOrNull()

    private fun resolveType(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> "PDF"
            ext == "docx" || mime.contains("wordprocessingml") -> "DOCX"
            ext == "epub" || mime == "application/epub+zip" -> "EPUB"
            else -> "OTHER"
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "epub" -> "application/epub+zip"
        else -> "application/octet-stream"
    }

    private fun sanitize(name: String): String = name
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(120)

    companion object {
        val SUPPORTED_MIME_TYPES = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/epub+zip"
        )
        private val SUPPORTED_TYPES = setOf("PDF", "DOCX", "EPUB")
        private const val MAX_INDEX_READ_CHARS = 600_000
    }
}
