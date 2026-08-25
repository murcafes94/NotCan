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
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import com.notcan.app.data.local.TranscriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NotCanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao())
    private val aiService = NotCanAiService(application)

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

    val cycles = repository.observeCycles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subjects = _selectedCycleId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSubjects(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val schedules = _selectedCycleId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSchedules(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val classes = _selectedSubjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeClasses(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val audioRecordings = _selectedClassId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeAudioRecordings(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importantMoments = _selectedClassId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeImportantMoments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notePages = _selectedClassId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeNotePages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documents = _selectedClassId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeDocuments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pdfInkStrokes = _selectedClassId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observePdfInkStrokes(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transcripts = _selectedClassId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeTranscripts(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            cycles.collect { list ->
                if (_selectedCycleId.value == null || list.none { it.id == _selectedCycleId.value }) {
                    _selectedCycleId.value = list.firstOrNull { it.isActive }?.id ?: list.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            subjects.collect { list ->
                if (_selectedSubjectId.value == null || list.none { it.id == _selectedSubjectId.value }) {
                    _selectedSubjectId.value = list.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            classes.collect { list ->
                if (_selectedClassId.value == null || list.none { it.id == _selectedClassId.value }) {
                    _selectedClassId.value = list.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            notePages.collect { list ->
                if (_selectedNoteId.value == null || list.none { it.id == _selectedNoteId.value }) {
                    _selectedNoteId.value = list.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            combine(cycles, subjects, schedules) { cycleList, subjectList, scheduleList ->
                Triple(cycleList, subjectList, scheduleList)
            }.collect { (cycleList, subjectList, scheduleList) ->
                val selected = cycleList.firstOrNull { it.id == _selectedCycleId.value }
                ReminderScheduler.reschedule(application, selected, subjectList, scheduleList)
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

    fun selectSubject(id: String) {
        _selectedSubjectId.value = id
        _selectedClassId.value = null
        _selectedNoteId.value = null
    }

    fun selectClass(id: String) {
        _selectedClassId.value = id
        _selectedNoteId.value = null
    }

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
        viewModelScope.launch {
            val item = repository.createSubject(parent, name)
            _selectedSubjectId.value = item.id
            _selectedClassId.value = null
        }
    }

    fun createClass(title: String) {
        val parent = _selectedSubjectId.value ?: return
        if (title.isBlank()) return
        viewModelScope.launch { _selectedClassId.value = repository.createClassSession(parent, title).id }
    }

    fun addSchedule(
        subjectId: String,
        weekdayIso: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        autoStopMode: String,
        autoStopGraceMinutes: Int
    ) {
        val cycleId = _selectedCycleId.value ?: return
        viewModelScope.launch {
            repository.addSchedule(
                cycleId = cycleId,
                subjectId = subjectId,
                weekdayIso = weekdayIso,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                reminderMinutesBefore = 1440,
                previewMinutesBefore = 10,
                autoStopMode = autoStopMode,
                autoStopGraceMinutes = autoStopGraceMinutes
            )
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch { repository.deleteSchedule(scheduleId) }
    }

    fun setScheduleCalendarEvent(scheduleId: String, eventId: Long?) {
        viewModelScope.launch { repository.setScheduleCalendarEvent(scheduleId, eventId) }
    }

    fun materializeOccurrence(
        occurrence: PlannedClassOccurrence,
        onReady: (ClassSessionEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val session = repository.materializeScheduledSession(
                schedule = occurrence.schedule,
                subject = occurrence.subject,
                occurrenceStartEpochMs = occurrence.startEpochMs,
                occurrenceEndEpochMs = occurrence.endEpochMs
            )
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
            repository.saveNotePage(
                current.copy(
                    title = title.ifBlank { "Apuntes" },
                    body = body,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun savePdfInkStroke(
        documentId: String,
        pageIndex: Int,
        tool: String,
        colorArgb: Long,
        baseWidth: Float,
        pointsData: String
    ) {
        val classId = _selectedClassId.value ?: return
        if (pointsData.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePdfInkStroke(
                PdfInkStrokeEntity(
                    UUID.randomUUID().toString(),
                    classId,
                    documentId,
                    pageIndex,
                    tool,
                    colorArgb,
                    baseWidth,
                    pointsData,
                    System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePdfInkStroke(strokeId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deletePdfInkStroke(strokeId) }
    }

    fun clearPdfInkPage(documentId: String, pageIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) { repository.clearPdfInkPage(documentId, pageIndex) }
    }

    fun importDocument(classSessionId: String, uri: Uri) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = application.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "documento_${System.currentTimeMillis()}"
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val type = resolveDocumentType(displayName, mimeType)
            val id = UUID.randomUUID().toString()
            val destinationDir = File(application.filesDir, "documents/$classSessionId").apply { mkdirs() }
            val destination = File(destinationDir, "${id.take(8)}_${sanitizeFileName(displayName)}")
            resolver.openInputStream(uri)?.use { source ->
                destination.outputStream().use { target -> source.copyTo(target, 64 * 1024) }
            } ?: return@launch
            repository.saveDocument(
                DocumentResourceEntity(id, classSessionId, displayName, destination.absolutePath, mimeType, type, System.currentTimeMillis())
            )
        }
    }

    fun askAi(question: String) {
        if (question.isBlank()) return
        _aiBusy.value = true
        _aiError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notesText = notePages.value.joinToString("\n\n") { "${it.title}\n${it.body}" }
                val transcriptText = transcripts.value.joinToString("\n\n") { it.body }
                val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name
                _aiResult.value = aiService.studyAssistant(subjectName, notesText, transcriptText, question)
                _aiConfigured.value = true
            } catch (t: Throwable) {
                _aiConfigured.value = aiService.isConfigured()
                _aiError.value = t.message ?: "No se pudo consultar Gemini"
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun transcribeAudio(audioId: String) {
        val audio: AudioRecordingEntity = audioRecordings.value.firstOrNull { it.id == audioId } ?: return
        _aiBusy.value = true
        _aiError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = aiService.transcribeAudio(File(audio.localPath))
                val now = System.currentTimeMillis()
                repository.saveTranscript(
                    TranscriptEntity(
                        id = UUID.randomUUID().toString(),
                        classSessionId = audio.classSessionId,
                        audioId = audio.id,
                        body = text,
                        status = "FINAL",
                        modelName = NotCanAiService.TEXT_MODEL,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                )
                _aiResult.value = text
                _aiConfigured.value = true
            } catch (t: Throwable) {
                _aiConfigured.value = aiService.isConfigured()
                _aiError.value = t.message ?: "No se pudo transcribir el audio"
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun clearAiMessage() {
        _aiError.value = null
        _aiResult.value = ""
    }

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
