from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path): return (ROOT / path).read_text(encoding='utf-8')
def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')
def one(s, old, new, label):
    if old not in s: raise RuntimeError(f'missing {label}')
    return s.replace(old, new, 1)
def rx(s, pattern, repl, label, flags=0):
    out,n = re.subn(pattern,repl,s,count=1,flags=flags)
    if n != 1: raise RuntimeError(f'{label}: {n} matches')
    return out

# DAO: reference counting for persisted cloud URIs.
p='app/src/main/java/com/notcan/app/data/local/NotCanDao.kt'; s=read(p)
s=one(s,
'''    @Query("SELECT localPath FROM document_resources WHERE classSessionId = :classId")
    suspend fun getDocumentPathsForClass(classId: String): List<String>
''',
'''    @Query("SELECT localPath FROM document_resources WHERE classSessionId = :classId")
    suspend fun getDocumentPathsForClass(classId: String): List<String>
    @Query("SELECT COUNT(*) FROM document_resources WHERE localPath = :path")
    suspend fun countDocumentsByPath(path: String): Int
''','dao document refcount')
write(p,s)

# Repository wrapper + explicit document delete.
p='app/src/main/java/com/notcan/app/data/StudyRepository.kt'; s=read(p)
s=one(s,
'''    suspend fun classFilePaths(classId: String): List<String> =
        (dao.getAudioPathsForClass(classId) + dao.getDocumentPathsForClass(classId)).distinct()
''',
'''    suspend fun classFilePaths(classId: String): List<String> =
        (dao.getAudioPathsForClass(classId) + dao.getDocumentPathsForClass(classId)).distinct()
    suspend fun documentReferenceCount(path: String): Int = dao.countDocumentsByPath(path)
''','repository refcount')
s=one(s,
'''    suspend fun saveDocument(document: DocumentResourceEntity) = dao.insertDocument(document)
    suspend fun saveAudioRecording(audioRecording: AudioRecordingEntity) = dao.insertAudioRecording(audioRecording)
''',
'''    suspend fun saveDocument(document: DocumentResourceEntity) = dao.insertDocument(document)
    suspend fun deleteDocument(documentId: String) = dao.deleteDocument(documentId)
    suspend fun saveAudioRecording(audioRecording: AudioRecordingEntity) = dao.insertAudioRecording(audioRecording)
''','repository delete document')
write(p,s)

# ViewModel: safe delete of cloud refs, explicit document deletion.
p='app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt'; s=read(p)
old='''        viewModelScope.launch(Dispatchers.IO) {
            repository.classFilePaths(classId).forEach { path -> runCatching { File(path).delete() } }
            repository.deleteClassData(classId)
        }
'''
new='''        viewModelScope.launch(Dispatchers.IO) {
            val paths = repository.classFilePaths(classId)
            paths.filterNot { it.startsWith("content://") }.forEach { path -> runCatching { File(path).delete() } }
            repository.deleteClassData(classId)
            paths.filter { it.startsWith("content://") }.forEach { path ->
                if (repository.documentReferenceCount(path) == 0) releasePersistedDocumentUri(path)
            }
        }
'''
s=one(s,old,new,'safe class cloud cleanup')
# Add deleteDocument after importDocument.
marker='''    fun askAi(question: String) {
'''
func='''    fun deleteDocument(documentId: String) {
        val document = documents.value.firstOrNull { it.id == documentId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDocument(documentId)
            if (document.localPath.startsWith("content://")) {
                if (repository.documentReferenceCount(document.localPath) == 0) releasePersistedDocumentUri(document.localPath)
            } else {
                runCatching { File(document.localPath).delete() }
            }
        }
    }

    private fun releasePersistedDocumentUri(raw: String) {
        val resolver = getApplication<Application>().contentResolver
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(uri, read or write) }
            .recoverCatching { resolver.releasePersistableUriPermission(uri, read) }
    }

'''
s=one(s,marker,func+marker,'delete document/release uri')
write(p,s)

# Cycle lifecycle: count/delete only true local files, release cloud grant after Room cascade.
p='app/src/main/java/com/notcan/app/data/CycleLifecycleManager.kt'; s=read(p)
s=s.replace('import android.content.Context\n','import android.content.Context\nimport android.content.Intent\nimport android.net.Uri\n')
s=one(s,
'''        val paths = repository.cycleFilePaths(cycleId)
        val bytes = paths.sumOf { path -> File(path).takeIf { it.exists() }?.length() ?: 0L }
''',
'''        val paths = repository.cycleFilePaths(cycleId)
        val localPaths = paths.filterNot { it.startsWith("content://") }
        val bytes = localPaths.sumOf { path -> File(path).takeIf { it.exists() }?.length() ?: 0L }
''','cycle preview local paths')
s=one(s,'            physicalFiles = paths.size,','            physicalFiles = localPaths.size,','cycle preview count')
s=one(s,
'''        val paths = repository.cycleFilePaths(cycleId)
        val eventIds = repository.cycleCalendarEventIds(cycleId)

        var deletedFiles = 0
        paths.forEach { path ->
            val file = File(path)
            if (!file.exists() || file.delete()) deletedFiles++
            pruneEmptyParents(file.parentFile)
        }
''',
'''        val paths = repository.cycleFilePaths(cycleId)
        val localPaths = paths.filterNot { it.startsWith("content://") }
        val cloudUris = paths.filter { it.startsWith("content://") }
        val eventIds = repository.cycleCalendarEventIds(cycleId)

        var deletedFiles = 0
        localPaths.forEach { path ->
            val file = File(path)
            if (!file.exists() || file.delete()) deletedFiles++
            pruneEmptyParents(file.parentFile)
        }
''','cycle deletion local only')
s=one(s,
'''        repository.deleteCycleData(cycleId)
        if (deletingActiveCycle) {
''',
'''        repository.deleteCycleData(cycleId)
        cloudUris.distinct().forEach { raw ->
            if (repository.documentReferenceCount(raw) == 0) releasePersistedUri(raw)
        }
        if (deletingActiveCycle) {
''','cycle release cloud grants')
s=s.replace('            filesFound = paths.size,','            filesFound = localPaths.size,')
s=one(s,
'''    private fun pruneEmptyParents(start: File?) {
''',
'''    private fun releasePersistedUri(raw: String) {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.releasePersistableUriPermission(uri, read or write) }
            .recoverCatching { app.contentResolver.releasePersistableUriPermission(uri, read) }
    }

    private fun pruneEmptyParents(start: File?) {
''','cycle release helper')
write(p,s)

# Settings: use already-loaded calendar list to choose automatic chip, no provider query per chip.
p='app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt'; s=read(p)
s=one(s,
'''    var selectedCalendarId by remember { mutableStateOf(preferences.calendarId) }
''',
'''    var selectedCalendarId by remember { mutableStateOf(preferences.calendarId) }
    val automaticCalendarId = remember(calendarTargets) {
        calendarTargets.firstOrNull { it.isGoogle && it.isPrimary }?.id
            ?: calendarTargets.firstOrNull { it.isGoogle }?.id
            ?: calendarTargets.firstOrNull { it.isPrimary }?.id
            ?: calendarTargets.firstOrNull()?.id
    }
''','automatic calendar id')
s=s.replace('selected = selectedCalendarId == target.id || (selectedCalendarId <= 0L && target == CalendarSync.preferredTarget(context)),','selected = selectedCalendarId == target.id || (selectedCalendarId <= 0L && target.id == automaticCalendarId),')
write(p,s)

# V5 workspace: restore Documents as a real class tab.
p='app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt'; s=read(p)
s=s.replace('import androidx.compose.material.icons.filled.Delete\n','import androidx.compose.material.icons.filled.Delete\nimport androidx.compose.material.icons.filled.Description\n')
s=s.replace('import com.notcan.app.data.local.DetectedCueEntity\n','import com.notcan.app.data.local.DetectedCueEntity\nimport com.notcan.app.data.local.DocumentResourceEntity\n')
s=one(s,
'''    selectedNoteId: String?,
    transcripts: List<TranscriptEntity>,
''',
'''    selectedNoteId: String?,
    documents: List<DocumentResourceEntity>,
    transcripts: List<TranscriptEntity>,
''','V5 docs arg')
s=one(s,
'''    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
''',
'''    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit,
    onDeleteDocument: (String) -> Unit,
''','V5 document callbacks')
s=one(s,
'''                        selectedNoteId = selectedNoteId,
                        transcripts = transcripts,
''',
'''                        selectedNoteId = selectedNoteId,
                        documents = documents,
                        transcripts = transcripts,
''','V5 normal tabs docs')
s=one(s,
'''                        onDeleteTranscript = onDeleteTranscript,
                        onTranscribeLocal = onTranscribeLocal,
                        modifier = Modifier.weight(1f)
''',
'''                        onDeleteTranscript = onDeleteTranscript,
                        onTranscribeLocal = onTranscribeLocal,
                        onImportDocument = onImportDocument,
                        onOpenDocument = onOpenDocument,
                        onDeleteDocument = onDeleteDocument,
                        modifier = Modifier.weight(1f)
''','V5 normal tabs callbacks')
# NormalClassTabs signature and routing.
s=one(s,
'''    selectedNoteId: String?,
    transcripts: List<TranscriptEntity>,
''',
'''    selectedNoteId: String?,
    documents: List<DocumentResourceEntity>,
    transcripts: List<TranscriptEntity>,
''','tabs docs arg')
s=one(s,
'''    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    modifier: Modifier = Modifier
''',
'''    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit,
    onDeleteDocument: (String) -> Unit,
    modifier: Modifier = Modifier
''','tabs docs callbacks')
s=s.replace('val views = listOf("Apuntes", "Audio", "Transcripción", "Estudio")','val views = listOf("Apuntes", "Audio", "Transcripción", "Documentos", "Estudio")')
s=one(s,
'''                2 -> TranscriptContentV5(audioRecordings, transcripts, detectedCues, whisperModelState, localWhisperBusy, localWhisperError, onTranscribeLocal, onDeleteTranscript)
                else -> StudyContentV5(subjectName, classTitle, transcripts, notePages, detectedCues)
''',
'''                2 -> TranscriptContentV5(audioRecordings, transcripts, detectedCues, whisperModelState, localWhisperBusy, localWhisperError, onTranscribeLocal, onDeleteTranscript)
                3 -> DocumentsContentV5(classSessionId, documents, onImportDocument, onOpenDocument, onDeleteDocument)
                else -> StudyContentV5(subjectName, classTitle, transcripts, notePages, detectedCues)
''','route docs tab')
# Add documents UI before TranscriptContent.
insert='''@Composable
private fun TranscriptContentV5(
'''
doc_ui='''@Composable
private fun DocumentsContentV5(
    classSessionId: String,
    documents: List<DocumentResourceEntity>,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit,
    onDeleteDocument: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = NotCanBlue)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Documentos de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text("Drive, nube o dispositivo · sin copia permanente cuando el proveedor lo permite", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { onImportDocument(classSessionId) }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir documento")
                    }
                }
            }
        }
        if (documents.isEmpty()) {
            item { Text("Todavía no hay documentos vinculados a esta clase.", color = NotCanGray) }
        } else {
            items(documents, key = { it.id }) { document ->
                var confirmDelete by remember(document.id) { mutableStateOf(false) }
                val cloud = document.localPath.startsWith("content://")
                Card(
                    colors = CardDefaults.cardColors(containerColor = NotCanGraphite),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDocument(document) }
                ) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.FileOpen, null, tint = NotCanBlue)
                        Column(Modifier.weight(1f)) {
                            Text(document.displayName, color = NotCanOffWhite, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (cloud) "Nube/Drive · abrir o editar en el proveedor" else "Archivo local · ${document.documentType}",
                                color = if (cloud) NotCanBlue else NotCanGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { onOpenDocument(document) }) { Text(if (cloud) "Abrir/editar" else "Abrir") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Quitar documento", tint = NotCanRed) }
                    }
                }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("Quitar documento") },
                        text = { Text(if (cloud) "NotCan quitará el vínculo de esta clase. El archivo original seguirá en Drive o en su proveedor de nube." else "Se eliminará la copia local guardada por NotCan.") },
                        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteDocument(document.id) }) { Text("Quitar") } },
                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
                    )
                }
            }
        }
    }
}

'''
s=one(s,insert,doc_ui+insert,'document UI')
write(p,s)

# Home: call V5 directly, pass documents and document callbacks; keep old PDF data wired at higher layer for DB compatibility.
p='app/src/main/java/com/notcan/app/ui/home/NotCanHomeScreen.kt'; s=read(p)
s=one(s,'    onOpenDocument: (DocumentResourceEntity) -> Unit,\n','    onOpenDocument: (DocumentResourceEntity) -> Unit,\n    onDeleteDocument: (String) -> Unit,\n','home delete document callback')
s=s.replace('                    NotCanClassWorkspaceV4(','                    NotCanClassWorkspaceV5(')
s=one(s,
'''                        selectedNoteId = selectedNoteId,
                        documents = documents,
                        pdfInkStrokes = pdfInkStrokes,
                        transcripts = transcripts,
''',
'''                        selectedNoteId = selectedNoteId,
                        documents = documents,
                        transcripts = transcripts,
''','home V5 document args')
s=one(s,
'''                        onImportDocument = onImportDocument,
                        onOpenDocument = onOpenDocument,
                        onSavePdfInkStroke = onSavePdfInkStroke,
                        onDeletePdfInkStroke = onDeletePdfInkStroke,
                        onClearPdfInkPage = onClearPdfInkPage,
                        onStartRecording = onStartRecording,
''',
'''                        onImportDocument = onImportDocument,
                        onOpenDocument = onOpenDocument,
                        onDeleteDocument = onDeleteDocument,
                        onStartRecording = onStartRecording,
''','home V5 callbacks')
write(p,s)

# Main passes delete callback.
p='app/src/main/java/com/notcan/app/MainActivity.kt'; s=read(p)
s=one(s,
'''                            onImportDocument = ::requestDocumentImport,
                            onOpenDocument = ::openDocument,
                            onSavePdfInkStroke = studyViewModel::savePdfInkStroke,
''',
'''                            onImportDocument = ::requestDocumentImport,
                            onOpenDocument = ::openDocument,
                            onDeleteDocument = studyViewModel::deleteDocument,
                            onSavePdfInkStroke = studyViewModel::savePdfInkStroke,
''','main delete doc callback')
write(p,s)

# Remove two superseded workspace implementations from the debug APK/source graph.
for legacy in [
    'app/src/main/java/com/notcan/app/ui/home/ClassWorkspace.kt',
    'app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV4.kt'
]:
    f=ROOT/legacy
    if f.exists(): f.unlink()

print('v0.8.25 runtime refinement applied')
