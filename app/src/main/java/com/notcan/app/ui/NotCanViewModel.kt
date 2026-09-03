package com.notcan.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notcan.app.ai.NotCanAiService
import com.notcan.app.calendar.PlannedClassOccurrence
import com.notcan.app.calendar.ReminderScheduler
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.LocalWhisperEngine
import com.notcan.app.localai.StudyModelManager
import com.notcan.app.localai.StudyModelState
import com.notcan.app.localai.WhisperModelManager
import com.notcan.app.localai.WhisperModelSpec
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.sources.ClassSourceStore
import com.notcan.app.ui.home.NoteDocxImporter
import com.notcan.app.sync.SupabaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NotCanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao(), application)
    private val aiService = NotCanAiService(application)
    private val syncManager = SupabaseSyncManager(application)
    private val sourceStore = ClassSourceStore(application)
    private val studyModelManager = StudyModelManager(application)
    private val whisperModelManager = WhisperModelManager(application)
    private val localWhisper = LocalWhisperEngine(application)

    private val _selectedCycleId = MutableStateFlow<String?>(null)
    val selectedCycleId: StateFlow<String?> = _selectedCycleId.asStateFlow()
    private val _selectedSubjectId = MutableStateFlow<String?>(null)
    val selectedSubjectId: StateFlow<String?> = _selectedSubjectId.asStateFlow()
    private val _selectedClassId = MutableStateFlow<String?>(null)
    val selectedClassId: StateFlow<String?> = _selectedClassId.asStateFlow()
    private val _selectedNoteId = MutableStateFlow<String?>(null)
    val selectedNoteId: StateFlow<String?> = _selectedNoteId.asStateFlow()

    private val _aiResult = MutableStateFlow("")
    val aiResult: StateFlow<String> = _aiResult.asStateFlow()
    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()
    private val _aiConfigured = MutableStateFlow(aiService.isConfigured())
    val aiConfigured: StateFlow<Boolean> = _aiConfigured.asStateFlow()

    private val _studyModelState = MutableStateFlow(studyModelManager.state())
    val studyModelState: StateFlow<StudyModelState> = _studyModelState.asStateFlow()
    private val _studyModelProgress = MutableStateFlow(studyModelManager.progressPercent())
    val studyModelProgress: StateFlow<Int?> = _studyModelProgress.asStateFlow()

    private val _whisperModelState = MutableStateFlow(whisperModelManager.state())
    val whisperModelState: StateFlow<WhisperModelState> = _whisperModelState.asStateFlow()
    private val _whisperModelProgress = MutableStateFlow(whisperModelManager.progressPercent())
    val whisperModelProgress: StateFlow<Int?> = _whisperModelProgress.asStateFlow()
    private val _localWhisperBusy = MutableStateFlow(false)
    val localWhisperBusy: StateFlow<Boolean> = _localWhisperBusy.asStateFlow()
    private val _localWhisperError = MutableStateFlow<String?>(null)
    val localWhisperError: StateFlow<String?> = _localWhisperError.asStateFlow()

    val cycles = repository.observeCycles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subjects = _selectedCycleId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSubjects(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val schedules = _selectedCycleId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSchedules(id) }
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
    val transcripts = _selectedClassId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeTranscripts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (syncManager.isSignedIn()) runCatching { syncManager.syncNow() }
        }
        viewModelScope.launch {
            cycles.collect { list ->
                if (_selectedCycleId.value == null || list.none { it.id == _selectedCycleId.value }) {
                    _selectedCycleId.value = list.firstOrNull { it.isActive }?.id ?: list.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            subjects.collect { list ->
                val selected = _selectedSubjectId.value
                if (selected != null && list.none { it.id == selected }) {
                    _selectedSubjectId.value = null
                    _selectedClassId.value = null
                    _selectedNoteId.value = null
                }
            }
        }
        viewModelScope.launch {
            classes.collect { list ->
                val selected = _selectedClassId.value
                if (selected != null && list.none { it.id == selected }) {
                    _selectedClassId.value = null
                    _selectedNoteId.value = null
                }
            }
        }
        viewModelScope.launch {
            notePages.collect { list ->
                if (_selectedNoteId.value == null || list.none { it.id == _selectedNoteId.value }) _selectedNoteId.value = list.firstOrNull()?.id
            }
        }
        viewModelScope.launch {
            combine(cycles, subjects, schedules) { cycleList, subjectList, scheduleList -> Triple(cycleList, subjectList, scheduleList) }
                .collect { (cycleList, subjectList, scheduleList) ->
                    val selected = cycleList.firstOrNull { it.id == _selectedCycleId.value }
                    ReminderScheduler.reschedule(application, selected, subjectList, scheduleList)
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _whisperModelState.value = whisperModelManager.state()
                _whisperModelProgress.value = whisperModelManager.progressPercent()
                _studyModelState.value = studyModelManager.state()
                _studyModelProgress.value = studyModelManager.progressPercent()
                _aiConfigured.value = _studyModelState.value == StudyModelState.INSTALLED
                val downloading = _whisperModelState.value == WhisperModelState.DOWNLOADING ||
                    _studyModelState.value == StudyModelState.DOWNLOADING
                delay(if (downloading) 1_500 else 8_000)
            }
        }
    }

    fun selectCycle(id: String) {
        _selectedCycleId.value = id
        _selectedSubjectId.value = null
        _selectedClassId.value = null
        _selectedNoteId.value = null
        viewModelScope.launch { repository.setActiveCycle(id) }
    }

    fun openSubjects() {
        _selectedSubjectId.value = null
        _selectedClassId.value = null
        _selectedNoteId.value = null
    }

    fun selectSubject(id: String) { _selectedSubjectId.value = id; _selectedClassId.value = null; _selectedNoteId.value = null }
    fun selectClass(id: String) { _selectedClassId.value = id; _selectedNoteId.value = null }
    fun selectNote(id: String) { _selectedNoteId.value = id }

    fun createCycle(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val item = repository.createCycle(name)
            _selectedCycleId.value = item.id
            _selectedSubjectId.value = null
            _selectedClassId.value = null
        }
    }

    fun updateCycleDates(startEpochDay: Long, endEpochDay: Long) {
        val cycleId = _selectedCycleId.value ?: return
        if (startEpochDay <= 0L || endEpochDay < startEpochDay) return
        viewModelScope.launch { repository.updateCycleDates(cycleId, startEpochDay, endEpochDay) }
    }

    fun createSubject(name: String) {
        val parent = _selectedCycleId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch { repository.createSubject(parent, name) }
    }

    fun createClass(title: String) {
        val parent = _selectedSubjectId.value ?: return
        viewModelScope.launch {
            val number = classes.value.size + 1
            val resolvedTitle = title.trim().ifBlank { "Clase $number" }
            repository.createClassSession(parent, resolvedTitle)
        }
    }

    fun deleteClass(classId: String) {
        if (_selectedClassId.value == classId) {
            _selectedClassId.value = null
            _selectedNoteId.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.classFilePaths(classId).forEach { path -> runCatching { File(path).delete() } }
            repository.deleteClassData(classId)
        }
    }

    fun addSchedule(subjectId: String, weekdayIso: Int, startMinuteOfDay: Int, endMinuteOfDay: Int, autoStopMode: String, autoStopGraceMinutes: Int) {
        val cycleId = _selectedCycleId.value ?: return
        viewModelScope.launch {
            repository.addSchedule(cycleId, subjectId, weekdayIso, startMinuteOfDay, endMinuteOfDay, 1440, 10, autoStopMode, autoStopGraceMinutes)
        }
    }

    fun deleteSchedule(scheduleId: String) { viewModelScope.launch { repository.deleteSchedule(scheduleId) } }
    fun setScheduleCalendarEvent(scheduleId: String, eventId: Long?) { viewModelScope.launch { repository.setScheduleCalendarEvent(scheduleId, eventId) } }

    fun materializeOccurrence(occurrence: PlannedClassOccurrence, onReady: (ClassSessionEntity) -> Unit = {}) {
        viewModelScope.launch {
            val session = repository.materializeScheduledSession(occurrence.schedule, occurrence.subject, occurrence.startEpochMs, occurrence.endEpochMs)
            _selectedSubjectId.value = occurrence.subject.id
            _selectedClassId.value = session.id
            _selectedNoteId.value = null
            onReady(session)
        }
    }

    fun createNotePage(title: String = "Apuntes") {
        val classId = _selectedClassId.value ?: return
        viewModelScope.launch { _selectedNoteId.value = repository.createNotePage(classId, title).id }
    }

    fun updateNotePage(noteId: String, title: String, body: String) {
        val current = notePages.value.firstOrNull { it.id == noteId } ?: return
        if (current.title == title && current.body == body) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNotePage(current.copy(title = title.ifBlank { "Apuntes" }, body = body, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun deleteNotePage(noteId: String) {
        if (_selectedNoteId.value == noteId) _selectedNoteId.value = null
        viewModelScope.launch(Dispatchers.IO) { repository.deleteNotePage(noteId) }
    }

    fun importNoteText(classSessionId: String, uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = app.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "Apuntes importados"
            val mimeType = resolver.getType(uri).orEmpty()
            val isDocx = displayName.endsWith(".docx", ignoreCase = true) || mimeType.contains("wordprocessingml", ignoreCase = true)
            val body = resolver.openInputStream(uri)?.use { input ->
                if (isDocx) NoteDocxImporter.toHtml(input)
                else input.bufferedReader().use { it.readText() }
            } ?: return@launch
            if (body.isBlank()) return@launch
            val title = displayName.substringBeforeLast('.').ifBlank { "Apuntes importados" }
            val note = repository.createNotePage(classSessionId, title)
            repository.saveNotePage(note.copy(body = body, updatedAtEpochMs = System.currentTimeMillis()))
            _selectedNoteId.value = note.id
        }
    }

    fun deleteAudio(audioId: String) {
        val audio = audioRecordings.value.firstOrNull { it.id == audioId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            File(audio.localPath).delete()
            repository.deleteAudio(audio.id)
        }
    }

    fun downloadWhisperModel() {
        _localWhisperError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                whisperModelManager.enqueueDownload()
                _whisperModelState.value = whisperModelManager.state()
            } catch (t: Throwable) {
                _localWhisperError.value = t.message ?: "No se pudo iniciar la descarga del modelo"
            }
        }
    }

    fun removeWhisperModel() {
        viewModelScope.launch(Dispatchers.IO) {
            whisperModelManager.removeModel()
            _whisperModelState.value = whisperModelManager.state()
            _whisperModelProgress.value = null
        }
    }

    fun downloadStudyModel() {
        _aiError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                studyModelManager.enqueueDownload()
                _studyModelState.value = studyModelManager.state()
            } catch (t: Throwable) {
                _aiError.value = t.message ?: "No se pudo iniciar la descarga de NotCan AI"
            }
        }
    }

    fun removeStudyModel() {
        if (_aiBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            studyModelManager.removeModel()
            _studyModelState.value = studyModelManager.state()
            _studyModelProgress.value = null
            _aiConfigured.value = false
            _aiResult.value = ""
        }
    }

    fun transcribeAudioLocal(audioId: String) {
        val audio = audioRecordings.value.firstOrNull { it.id == audioId } ?: return
        if (_localWhisperBusy.value) return
        _localWhisperBusy.value = true
        _localWhisperError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = localWhisper.transcribeM4a(File(audio.localPath))
                val now = System.currentTimeMillis()
                repository.saveTranscript(
                    TranscriptEntity(
                        id = UUID.randomUUID().toString(),
                        classSessionId = audio.classSessionId,
                        audioId = audio.id,
                        body = text,
                        status = "FINAL_LOCAL",
                        modelName = WhisperModelSpec.DISPLAY_NAME,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                )
            } catch (t: Throwable) {
                _localWhisperError.value = t.message ?: "No se pudo transcribir localmente"
            } finally {
                _localWhisperBusy.value = false
            }
        }
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

    fun askAi(question: String) {
        if (question.isBlank() || _aiBusy.value) return
        _aiResult.value = ""
        _aiBusy.value = true
        _aiError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notesText = notePages.value.joinToString("\n\n") { "${it.title}\n${it.body}" }
                val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name
                val classTitle = classes.value.firstOrNull { it.id == _selectedClassId.value }?.title
                val scopeKey = sourceStore.scopeKey(subjectName, classTitle)
                val externalSources = sourceStore.combinedContext(scopeKey)
                val transcriptText = buildString {
                    append(transcripts.value.joinToString("\n\n") { it.body })
                    if (externalSources.isNotBlank()) {
                        appendLine("\n\nARCHIVOS EXTERNOS INDEXADOS PARA ESTA CLASE:")
                        append(externalSources)
                    }
                }
                val finalResult = aiService.studyAssistant(
                    subjectName = subjectName,
                    notes = notesText,
                    transcript = transcriptText,
                    question = question,
                    onPartial = { partial -> _aiResult.value = partial }
                )
                _aiResult.value = finalResult
                _aiConfigured.value = true
            } catch (t: Throwable) {
                _aiConfigured.value = aiService.isConfigured()
                _aiError.value = t.message ?: "No se pudo ejecutar la IA"
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun transcribeAudio(audioId: String) = transcribeAudioLocal(audioId)

    fun clearAiMessage() { _aiError.value = null; _aiResult.value = "" }

    private fun sanitizeFileName(name: String) = name.replace('/', '_').replace('\\', '_').replace(':', '_')
    private fun resolveDocumentType(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> "PDF"
            ext == "epub" || mime == "application/epub+zip" -> "EPUB"
            ext == "docx" || mime.contains("wordprocessingml") -> "DOCX"
            ext == "doc" || mime == "application/msword" -> "DOC"
            else -> "OTHER"
        }
    }
}
