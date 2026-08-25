package com.notcan.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.NotCanDatabase
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

    val cycles = repository.observeCycles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subjects = _selectedCycleId
        .flatMapLatest { cycleId ->
            if (cycleId == null) flowOf(emptyList()) else repository.observeSubjects(cycleId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val classes = _selectedSubjectId
        .flatMapLatest { subjectId ->
            if (subjectId == null) flowOf(emptyList()) else repository.observeClasses(subjectId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val audioRecordings = _selectedClassId
        .flatMapLatest { classId ->
            if (classId == null) flowOf(emptyList()) else repository.observeAudioRecordings(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importantMoments = _selectedClassId
        .flatMapLatest { classId ->
            if (classId == null) flowOf(emptyList()) else repository.observeImportantMoments(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notePages = _selectedClassId
        .flatMapLatest { classId ->
            if (classId == null) flowOf(emptyList()) else repository.observeNotePages(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documents = _selectedClassId
        .flatMapLatest { classId ->
            if (classId == null) flowOf(emptyList()) else repository.observeDocuments(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            cycles.collect { items ->
                val current = _selectedCycleId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedCycleId.value = items.firstOrNull { it.isActive }?.id ?: items.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            subjects.collect { items ->
                val current = _selectedSubjectId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedSubjectId.value = items.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            classes.collect { items ->
                val current = _selectedClassId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedClassId.value = items.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            notePages.collect { items ->
                val current = _selectedNoteId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedNoteId.value = items.firstOrNull()?.id
                }
            }
        }
    }

    fun selectCycle(cycleId: String) {
        _selectedCycleId.value = cycleId
        _selectedSubjectId.value = null
        _selectedClassId.value = null
        _selectedNoteId.value = null
        viewModelScope.launch { repository.setActiveCycle(cycleId) }
    }

    fun selectSubject(subjectId: String) {
        _selectedSubjectId.value = subjectId
        _selectedClassId.value = null
        _selectedNoteId.value = null
    }

    fun selectClass(classId: String) {
        _selectedClassId.value = classId
        _selectedNoteId.value = null
    }

    fun selectNote(noteId: String) {
        _selectedNoteId.value = noteId
    }

    fun createCycle(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cycle = repository.createCycle(name)
            _selectedCycleId.value = cycle.id
            _selectedSubjectId.value = null
            _selectedClassId.value = null
            _selectedNoteId.value = null
        }
    }

    fun createSubject(name: String) {
        val cycleId = _selectedCycleId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val subject = repository.createSubject(cycleId, name)
            _selectedSubjectId.value = subject.id
            _selectedClassId.value = null
            _selectedNoteId.value = null
        }
    }

    fun createClass(title: String) {
        val subjectId = _selectedSubjectId.value ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            val classSession = repository.createClassSession(subjectId, title)
            _selectedClassId.value = classSession.id
            _selectedNoteId.value = null
        }
    }

    fun createNotePage(title: String = "Apuntes") {
        val classId = _selectedClassId.value ?: return
        viewModelScope.launch {
            val note = repository.createNotePage(classId, title)
            _selectedNoteId.value = note.id
        }
    }

    fun updateNotePage(noteId: String, title: String, body: String) {
        val current = notePages.value.firstOrNull { it.id == noteId } ?: return
        if (current.title == title && current.body == body) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNotePage(
                current.copy(
                    title = title.ifBlank { "Apuntes" },
                    body = body,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun importDocument(classSessionId: String, uri: Uri) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = application.contentResolver
            val displayName = resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "documento_${System.currentTimeMillis()}"

            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val documentType = resolveDocumentType(displayName, mimeType)
            val id = UUID.randomUUID().toString()
            val safeName = sanitizeFileName(displayName)
            val destinationDir = File(application.filesDir, "documents/$classSessionId").apply { mkdirs() }
            val destination = File(destinationDir, "${id.take(8)}_$safeName")

            val input = resolver.openInputStream(uri) ?: return@launch
            input.use { source ->
                destination.outputStream().use { target ->
                    source.copyTo(target, 64 * 1024)
                }
            }

            repository.saveDocument(
                DocumentResourceEntity(
                    id = id,
                    classSessionId = classSessionId,
                    displayName = displayName,
                    localPath = destination.absolutePath,
                    mimeType = mimeType,
                    documentType = documentType,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun sanitizeFileName(name: String): String = name
        .replace('/', '_')
        .replace('\\', '_')
        .replace(':', '_')

    private fun resolveDocumentType(displayName: String, mimeType: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when {
            extension == "pdf" || mimeType == "application/pdf" -> "PDF"
            extension == "epub" || mimeType == "application/epub+zip" -> "EPUB"
            extension == "docx" || mimeType.contains("wordprocessingml") -> "DOCX"
            extension == "doc" || mimeType == "application/msword" -> "DOC"
            else -> "OTHER"
        }
    }
}
