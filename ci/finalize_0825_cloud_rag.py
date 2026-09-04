from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Missing target: {label}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# ClassSourceStore: index persisted cloud/local document references without
# duplicating the full document into TuNot storage. Only a tiny descriptor and
# extracted text sidecar are kept; reindexing re-reads the canonical document.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/sources/ClassSourceStore.kt"
s = read(p)
s = s.replace(
    " * Sources are intentionally independent from editable notes. PDF, DOCX and EPUB files are copied\n * into app-private storage, indexed locally and exposed to the AI/search layer through their\n * extracted text. The original file always remains the canonical source.\n",
    " * Sources are intentionally independent from editable notes. PDF, DOCX and EPUB sources may be\n * copied into app-private storage or referenced through a persisted URI. TuNot stores extracted text\n * sidecars for search/RAG while the original document remains the canonical source.\n"
)
s = replace_once(
    s,
    "        val sourceUrl: String? = null\n",
    "        val sourceUrl: String? = null,\n        val sourceUri: String? = null\n",
    "SourceItem sourceUri"
)
anchor = '''    fun importWeb(scopeKey: String, title: String, url: String, content: String): SourceItem {\n'''
method = '''    fun importReference(\n        scopeKey: String,\n        uri: Uri,\n        displayNameOverride: String? = null,\n        mimeTypeOverride: String? = null\n    ): SourceItem {\n        val resolver = context.contentResolver\n        val rawUri = uri.toString()\n        list(scopeKey).firstOrNull { it.sourceUri == rawUri }?.let { existing ->\n            return reindex(existing)\n        }\n\n        val displayName = displayNameOverride?.takeIf { it.isNotBlank() } ?:\n            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->\n                if (cursor.moveToFirst()) cursor.getString(0) else null\n            }?.takeIf { it.isNotBlank() } ?: "fuente_${System.currentTimeMillis()}"\n        val mimeType = mimeTypeOverride?.takeIf { it.isNotBlank() }\n            ?: resolver.getType(uri).orEmpty().ifBlank { guessMime(displayName) }\n        val type = resolveType(displayName, mimeType)\n        require(type in SUPPORTED_TYPES) { "Solo se admiten PDF, DOCX y EPUB como fuentes" }\n\n        val id = UUID.randomUUID().toString()\n        val dir = scopeDir(scopeKey).apply { mkdirs() }\n        val descriptor = File(dir, "${id.take(8)}_${sanitize(displayName)}.ref")\n        descriptor.writeText(rawUri, Charsets.UTF_8)\n        val item = SourceItem(\n            id = id,\n            scopeKey = scopeKey,\n            displayName = displayName,\n            type = type,\n            mimeType = mimeType,\n            localPath = descriptor.absolutePath,\n            createdAtEpochMs = System.currentTimeMillis(),\n            indexed = false,\n            sourceUri = rawUri\n        )\n        val indexed = reindexReference(item)\n        val finalItem = item.copy(\n            indexed = indexed?.exists() == true,\n            indexChars = indexed?.length()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0\n        )\n        saveItem(finalItem)\n        return finalItem\n    }\n\n'''
s = replace_once(s, anchor, method + anchor, "importReference")
old_reindex = '''    fun reindex(item: SourceItem): SourceItem {\n        val file = File(item.localPath)\n        val index = if (item.type == "WEB" && file.exists()) {\n            SourceTextIndexer.indexFileFor(file).also { it.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8) }\n        } else SourceTextIndexer.index(context, file, item.type)\n        val updated = item.copy(\n            indexed = index?.exists() == true,\n            indexChars = index?.length()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0\n        )\n        saveItem(updated)\n        return updated\n    }\n'''
new_reindex = '''    fun reindex(item: SourceItem): SourceItem {\n        val file = File(item.localPath)\n        val index = when {\n            !item.sourceUri.isNullOrBlank() -> reindexReference(item)\n            item.type == "WEB" && file.exists() -> {\n                SourceTextIndexer.indexFileFor(file).also { it.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8) }\n            }\n            else -> SourceTextIndexer.index(context, file, item.type)\n        }\n        val updated = item.copy(\n            indexed = index?.exists() == true,\n            indexChars = index?.length()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0\n        )\n        saveItem(updated)\n        return updated\n    }\n\n    fun deleteByUri(scopeKey: String, rawUri: String) {\n        list(scopeKey).filter { it.sourceUri == rawUri }.forEach { delete(scopeKey, it.id) }\n    }\n\n    private fun reindexReference(item: SourceItem): File? {\n        val rawUri = item.sourceUri?.takeIf { it.isNotBlank() } ?: return null\n        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null\n        val descriptor = File(item.localPath)\n        descriptor.parentFile?.mkdirs()\n        if (!descriptor.exists()) descriptor.writeText(rawUri, Charsets.UTF_8)\n        val temp = File.createTempFile("tunot_source_", ".bin", context.cacheDir)\n        return try {\n            context.contentResolver.openInputStream(uri)?.use { input ->\n                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }\n            } ?: return null\n            val tempIndex = SourceTextIndexer.index(context, temp, item.type) ?: return null\n            val target = SourceTextIndexer.indexFileFor(descriptor)\n            tempIndex.copyTo(target, overwrite = true)\n            target\n        } finally {\n            SourceTextIndexer.indexFileFor(temp).delete()\n            temp.delete()\n        }\n    }\n'''
s = replace_once(s, old_reindex, new_reindex, "reference reindex")
s = replace_once(
    s,
    '.put("sourceUrl", sourceUrl)\n',
    '.put("sourceUrl", sourceUrl)\n        .put("sourceUri", sourceUri)\n',
    "sourceUri json"
)
s = replace_once(
    s,
    '            sourceUrl = optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" }\n',
    '            sourceUrl = optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" },\n            sourceUri = optString("sourceUri").takeIf { it.isNotBlank() && it != "null" }\n',
    "sourceUri manifest parse"
)
write(p, s)

# ---------------------------------------------------------------------------
# ViewModel: every supported document added to a class is indexed for TuNot
# without a second permanent binary copy; class/document deletion cleans URI
# grants and the matching source scope.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
s = read(p)
old_delete_class = '''    fun deleteClass(classId: String) {\n        if (_selectedClassId.value == classId) {\n            _selectedClassId.value = null\n            _selectedNoteId.value = null\n        }\n        viewModelScope.launch(Dispatchers.IO) {\n            repository.classFilePaths(classId).forEach { path -> runCatching { File(path).delete() } }\n            repository.deleteClassData(classId)\n        }\n    }\n'''
new_delete_class = '''    fun deleteClass(classId: String) {\n        val targetClass = classes.value.firstOrNull { it.id == classId }\n        val targetSubject = targetClass?.let { cls -> subjects.value.firstOrNull { it.id == cls.subjectId } }\n        val scopeKey = sourceStore.scopeKey(targetSubject?.name, targetClass?.title)\n        if (_selectedClassId.value == classId) {\n            _selectedClassId.value = null\n            _selectedNoteId.value = null\n        }\n        viewModelScope.launch(Dispatchers.IO) {\n            val paths = repository.classFilePaths(classId)\n            val cloudUris = paths.filter { it.startsWith("content://") }\n            paths.filterNot { it.startsWith("content://") }.forEach { path -> runCatching { File(path).delete() } }\n            sourceStore.deleteScope(scopeKey)\n            repository.deleteClassData(classId)\n            cloudUris.distinct().forEach { raw ->\n                if (repository.documentReferenceCount(raw) == 0) releasePersistedDocumentUri(raw)\n            }\n        }\n    }\n'''
s = replace_once(s, old_delete_class, new_delete_class, "deleteClass cloud cleanup")
old_persistent = '''            if (persistent) {\n                repository.saveDocument(\n                    DocumentResourceEntity(id, classSessionId, displayName, uri.toString(), mimeType, type, System.currentTimeMillis())\n                )\n                return@launch\n            }\n'''
new_persistent = '''            if (persistent) {\n                repository.saveDocument(\n                    DocumentResourceEntity(id, classSessionId, displayName, uri.toString(), mimeType, type, System.currentTimeMillis())\n                )\n                if (type in setOf("PDF", "DOCX", "EPUB")) {\n                    val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name\n                    val classTitle = classes.value.firstOrNull { it.id == classSessionId }?.title\n                    val scopeKey = sourceStore.scopeKey(subjectName, classTitle)\n                    runCatching { sourceStore.importReference(scopeKey, uri, displayName, mimeType) }\n                }\n                return@launch\n            }\n'''
s = replace_once(s, old_persistent, new_persistent, "persistent document RAG")
old_fallback_save = '''            repository.saveDocument(\n                DocumentResourceEntity(id, classSessionId, displayName, destination.absolutePath, mimeType, type, System.currentTimeMillis())\n            )\n'''
new_fallback_save = '''            repository.saveDocument(\n                DocumentResourceEntity(id, classSessionId, displayName, destination.absolutePath, mimeType, type, System.currentTimeMillis())\n            )\n            if (type in setOf("PDF", "DOCX", "EPUB")) {\n                val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name\n                val classTitle = classes.value.firstOrNull { it.id == classSessionId }?.title\n                val scopeKey = sourceStore.scopeKey(subjectName, classTitle)\n                runCatching { sourceStore.importReference(scopeKey, Uri.fromFile(destination), displayName, mimeType) }\n            }\n'''
s = replace_once(s, old_fallback_save, new_fallback_save, "fallback document RAG")
old_delete_doc = '''    fun deleteDocument(documentId: String) {\n        val document = documents.value.firstOrNull { it.id == documentId } ?: return\n        viewModelScope.launch(Dispatchers.IO) {\n            repository.deleteDocument(documentId)\n            if (document.localPath.startsWith("content://")) {\n                if (repository.documentReferenceCount(document.localPath) == 0) releasePersistedDocumentUri(document.localPath)\n            } else {\n                runCatching { File(document.localPath).delete() }\n            }\n        }\n    }\n'''
new_delete_doc = '''    fun deleteDocument(documentId: String) {\n        val document = documents.value.firstOrNull { it.id == documentId } ?: return\n        val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name\n        val classTitle = classes.value.firstOrNull { it.id == document.classSessionId }?.title\n        val scopeKey = sourceStore.scopeKey(subjectName, classTitle)\n        viewModelScope.launch(Dispatchers.IO) {\n            val sourceUri = if (document.localPath.startsWith("content://")) document.localPath else Uri.fromFile(File(document.localPath)).toString()\n            sourceStore.deleteByUri(scopeKey, sourceUri)\n            repository.deleteDocument(documentId)\n            if (document.localPath.startsWith("content://")) {\n                if (repository.documentReferenceCount(document.localPath) == 0) releasePersistedDocumentUri(document.localPath)\n            } else {\n                runCatching { File(document.localPath).delete() }\n            }\n        }\n    }\n'''
s = replace_once(s, old_delete_doc, new_delete_doc, "delete document source cleanup")
write(p, s)

print("v0.8.25 cloud RAG finalization applied")
