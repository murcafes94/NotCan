package com.notcan.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.PdfInkStrokeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NotCanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao())

    private val _selectedCycleId = MutableStateFlow<String?>(null)
    val selectedCycleId: StateFlow<String?> = _selectedCycleId.asStateFlow()
    private val _selectedSubjectId = MutableStateFlow<String?>(null)
    val selectedSubjectId: StateFlow<String?> = _selectedSubjectId.asStateFlow()
    private val _selectedClassId = MutableStateFlow<String?>(null)
    val selectedClassId: StateFlow<String?> = _selectedClassId.asStateFlow()
    private val _selectedNoteId = MutableStateFlow<String?>(null)
    val selectedNoteId: StateFlow<String?> = _selectedNoteId.asStateFlow()

    val cycles = repository.observeCycles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subjects = _selectedCycleId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSubjects(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val classes = _selectedSubjectId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeClasses(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val audioRecordings = _selectedClassId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeAudioRecordings(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val importantMoments = _selectedClassId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeImportantMoments(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notePages = _selectedClassId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeNotePages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val documents = _selectedClassId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeDocuments(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pdfInkStrokes = _selectedClassId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observePdfInkStrokes(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { cycles.collect { list -> if (_selectedCycleId.value == null || list.none { it.id == _selectedCycleId.value }) _selectedCycleId.value = list.firstOrNull { it.isActive }?.id ?: list.firstOrNull()?.id } }
        viewModelScope.launch { subjects.collect { list -> if (_selectedSubjectId.value == null || list.none { it.id == _selectedSubjectId.value }) _selectedSubjectId.value = list.firstOrNull()?.id } }
        viewModelScope.launch { classes.collect { list -> if (_selectedClassId.value == null || list.none { it.id == _selectedClassId.value }) _selectedClassId.value = list.firstOrNull()?.id } }
        viewModelScope.launch { notePages.collect { list -> if (_selectedNoteId.value == null || list.none { it.id == _selectedNoteId.value }) _selectedNoteId.value = list.firstOrNull()?.id } }
    }

    fun selectCycle(id: String) { _selectedCycleId.value = id; _selectedSubjectId.value = null; _selectedClassId.value = null; _selectedNoteId.value = null; viewModelScope.launch { repository.setActiveCycle(id) } }
    fun selectSubject(id: String) { _selectedSubjectId.value = id; _selectedClassId.value = null; _selectedNoteId.value = null }
    fun selectClass(id: String) { _selectedClassId.value = id; _selectedNoteId.value = null }
    fun selectNote(id: String) { _selectedNoteId.value = id }

    fun createCycle(name: String) { if (name.isBlank()) return; viewModelScope.launch { val item = repository.createCycle(name); _selectedCycleId.value = item.id; _selectedSubjectId.value = null; _selectedClassId.value = null } }
    fun createSubject(name: String) { val parent = _selectedCycleId.value ?: return; if (name.isBlank()) return; viewModelScope.launch { val item = repository.createSubject(parent, name); _selectedSubjectId.value = item.id; _selectedClassId.value = null } }
    fun createClass(title: String) { val parent = _selectedSubjectId.value ?: return; if (title.isBlank()) return; viewModelScope.launch { _selectedClassId.value = repository.createClassSession(parent, title).id } }
    fun createNotePage(title: String = "Apuntes") { val classId = _selectedClassId.value ?: return; viewModelScope.launch { _selectedNoteId.value = repository.createNotePage(classId, title).id } }

    fun updateNotePage(noteId: String, title: String, body: String) {
        val current = notePages.value.firstOrNull { it.id == noteId } ?: return
        if (current.title == title && current.body == body) return
        viewModelScope.launch(Dispatchers.IO) { repository.saveNotePage(current.copy(title = title.ifBlank { "Apuntes" }, body = body, updatedAtEpochMs = System.currentTimeMillis())) }
    }

    fun savePdfInkStroke(documentId: String, pageIndex: Int, tool: String, colorArgb: Long, baseWidth: Float, pointsData: String) {
        val classId = _selectedClassId.value ?: return
        if (pointsData.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePdfInkStroke(PdfInkStrokeEntity(UUID.randomUUID().toString(), classId, documentId, pageIndex, tool, colorArgb, baseWidth, pointsData, System.currentTimeMillis()))
        }
    }

    fun deletePdfInkStroke(strokeId: String) { viewModelScope.launch(Dispatchers.IO) { repository.deletePdfInkStroke(strokeId) } }
    fun clearPdfInkPage(documentId: String, pageIndex: Int) { viewModelScope.launch(Dispatchers.IO) { repository.clearPdfInkPage(documentId, pageIndex) } }

    fun importDocument(classSessionId: String, uri: Uri) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = application.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "documento_${System.currentTimeMillis()}"
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val type = resolveDocumentType(displayName, mimeType)
            val id = UUID.randomUUID().toString()
            val destinationDir = File(application.filesDir, "documents/$classSessionId").apply { mkdirs() }
            val destination = File(destinationDir, "${id.take(8)}_${sanitizeFileName(displayName)}")
            resolver.openInputStream(uri)?.use { source -> destination.outputStream().use { target -> source.copyTo(target, 64 * 1024) } } ?: return@launch
            repository.saveDocument(DocumentResourceEntity(id, classSessionId, displayName, destination.absolutePath, mimeType, type, System.currentTimeMillis()))
        }
    }

    private fun sanitizeFileName(name: String) = name.replace('/', '_').replace('\\', '_').replace(':', '_')
    private fun resolveDocumentType(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when { ext == "pdf" || mime == "application/pdf" -> "PDF"; ext == "epub" || mime == "application/epub+zip" -> "EPUB"; ext == "docx" || mime.contains("wordprocessingml") -> "DOCX"; ext == "doc" || mime == "application/msword" -> "DOC"; else -> "OTHER" }
    }
}
