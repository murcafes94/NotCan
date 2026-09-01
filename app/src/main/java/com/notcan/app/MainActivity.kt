package com.notcan.app

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.notcan.app.ai.MistralCredentialsStore
import com.notcan.app.calendar.CalendarSync
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.localai.BackgroundTranscriptionManager
import com.notcan.app.recording.RecordingService
import com.notcan.app.recording.RecordingState
import com.notcan.app.settings.NotCanPreferences
import com.notcan.app.ui.AcademicExtrasViewModel
import com.notcan.app.ui.NotCanViewModel
import com.notcan.app.ui.ai.NotCanAiScreen
import com.notcan.app.ui.ai.TuNotOfflineEntry
import com.notcan.app.ui.calendar.AcademicCalendarScreen
import com.notcan.app.ui.grades.GradesScreen
import com.notcan.app.ui.home.NEW_CLASS_RECORDING_SENTINEL
import com.notcan.app.ui.home.NotCanHomeScreen
import com.notcan.app.ui.home.NotCanRootV5
import com.notcan.app.ui.settings.SettingsScreen
import com.notcan.app.ui.tasks.TasksScreen
import com.notcan.app.ui.theme.NotCanTheme
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val studyViewModel: NotCanViewModel by viewModels()
    private val extrasViewModel: AcademicExtrasViewModel by viewModels()
    private val preferences by lazy { NotCanPreferences(this) }
    private val mistralCredentials by lazy { MistralCredentialsStore(this) }
    private val recordingRepository by lazy { StudyRepository(NotCanDatabase.getInstance(this).dao(), this) }
    private var pendingRecording: PendingRecording? = null
    private var pendingDocumentClassId: String? = null
    private var pendingNoteClassId: String? = null
    private var pendingCalendarScheduleId: String? = null
    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var notCanDndEnabled = false

    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val request = pendingRecording
        pendingRecording = null
        if (microphoneGranted && request != null) startRecordingService(request)
    }

    private val calendarPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val scheduleId = pendingCalendarScheduleId
        pendingCalendarScheduleId = null
        val readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted && scheduleId != null) performCalendarSync(scheduleId)
        else if (scheduleId != null) Toast.makeText(this, "NotCan necesita acceso al calendario para sincronizar el horario", Toast.LENGTH_LONG).show()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val documentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val classSessionId = pendingDocumentClassId
        pendingDocumentClassId = null
        if (uri != null && classSessionId != null) studyViewModel.importDocument(classSessionId, uri)
    }

    private val noteLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val classSessionId = pendingNoteClassId
        pendingNoteClassId = null
        if (uri != null && classSessionId != null) studyViewModel.importNoteText(classSessionId, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            var darkTheme by remember { mutableStateOf(preferences.darkTheme) }
            var classNavigationRequest by remember { mutableIntStateOf(0) }
            NotCanTheme(darkTheme = darkTheme) {
                val recordingState = RecordingService.state.collectAsStateWithLifecycle().value
                val cycles = studyViewModel.cycles.collectAsStateWithLifecycle().value
                val subjects = studyViewModel.subjects.collectAsStateWithLifecycle().value
                val schedules = studyViewModel.schedules.collectAsStateWithLifecycle().value
                val classes = studyViewModel.classes.collectAsStateWithLifecycle().value
                val audioRecordings = studyViewModel.audioRecordings.collectAsStateWithLifecycle().value
                val importantMoments = studyViewModel.importantMoments.collectAsStateWithLifecycle().value
                val notePages = studyViewModel.notePages.collectAsStateWithLifecycle().value
                val documents = studyViewModel.documents.collectAsStateWithLifecycle().value
                val pdfInkStrokes = studyViewModel.pdfInkStrokes.collectAsStateWithLifecycle().value
                val transcripts = studyViewModel.transcripts.collectAsStateWithLifecycle().value
                val selectedCycleId = studyViewModel.selectedCycleId.collectAsStateWithLifecycle().value
                val selectedSubjectId = studyViewModel.selectedSubjectId.collectAsStateWithLifecycle().value
                val selectedClassId = studyViewModel.selectedClassId.collectAsStateWithLifecycle().value
                val selectedNoteId = studyViewModel.selectedNoteId.collectAsStateWithLifecycle().value
                val aiConfigured = studyViewModel.aiConfigured.collectAsStateWithLifecycle().value
                val aiBusy = studyViewModel.aiBusy.collectAsStateWithLifecycle().value
                val aiError = studyViewModel.aiError.collectAsStateWithLifecycle().value
                val aiResult = studyViewModel.aiResult.collectAsStateWithLifecycle().value
                val studyModelState = studyViewModel.studyModelState.collectAsStateWithLifecycle().value
                val studyModelProgress = studyViewModel.studyModelProgress.collectAsStateWithLifecycle().value
                val whisperModelState = studyViewModel.whisperModelState.collectAsStateWithLifecycle().value
                val whisperModelProgress = studyViewModel.whisperModelProgress.collectAsStateWithLifecycle().value
                val localWhisperError = studyViewModel.localWhisperError.collectAsStateWithLifecycle().value
                val gradeItems = extrasViewModel.gradeItems.collectAsStateWithLifecycle().value
                val detectedCues = extrasViewModel.detectedCues.collectAsStateWithLifecycle().value
                val taskItems = extrasViewModel.taskItems.collectAsStateWithLifecycle().value

                LaunchedEffect(selectedCycleId, selectedSubjectId, selectedClassId) {
                    extrasViewModel.setContext(selectedCycleId, selectedSubjectId, selectedClassId)
                }

                val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }
                val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
                val selectedClass = classes.firstOrNull { it.id == selectedClassId }
                val recordingActive = recordingState is RecordingState.Recording || recordingState is RecordingState.Paused
                val assistantContextTitle = listOfNotNull(selectedSubject?.name, selectedClass?.title)
                    .joinToString(" · ")
                    .ifBlank { selectedCycle?.name ?: "NotCan" }
                val assistantOfflineEntries = buildList {
                    selectedSubject?.let { subject ->
                        add(TuNotOfflineEntry(subject.name, "Materia", subject.name))
                    }
                    selectedClass?.let { session ->
                        add(TuNotOfflineEntry(session.title, "Clase", session.title))
                    }
                    notePages.forEach { note ->
                        add(
                            TuNotOfflineEntry(
                                title = note.title.ifBlank { "Apuntes" },
                                subtitle = "Apunte guardado",
                                text = markdownToPlainText(note.body)
                            )
                        )
                    }
                    transcripts.forEachIndexed { index, transcript ->
                        add(
                            TuNotOfflineEntry(
                                title = "Transcripción ${index + 1}",
                                subtitle = selectedClass?.title ?: "Transcripción guardada",
                                text = transcript.body
                            )
                        )
                    }
                    documents.forEach { document ->
                        add(
                            TuNotOfflineEntry(
                                title = document.displayName,
                                subtitle = "Documento local · ${document.documentType}",
                                text = document.displayName
                            )
                        )
                    }
                }
                val assistantOnlineConfigured = mistralCredentials.hasApiKey() && preferences.mistralAgentId.isNotBlank()

                NotCanRootV5(
                    cycle = selectedCycle,
                    subjects = subjects,
                    schedules = schedules,
                    tasks = taskItems,
                    recordingActive = recordingActive,
                    subjectContextActive = selectedSubject != null,
                    subjectsTitle = selectedClass?.title ?: selectedSubject?.name ?: "Materias",
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme; preferences.darkTheme = darkTheme },
                    onToggleDoNotDisturb = ::toggleDoNotDisturb,
                    onOpenClasses = { classNavigationRequest++ },
                    onOpenPlannedClass = { occurrence -> studyViewModel.materializeOccurrence(occurrence) },
                    onRecordPlannedClass = { occurrence ->
                        studyViewModel.materializeOccurrence(occurrence) { session ->
                            requestPermissionsAndStart(session.id, occurrence.endEpochMs, occurrence.schedule.autoStopMode, occurrence.schedule.autoStopGraceMinutes)
                        }
                    },
                    assistantContextTitle = assistantContextTitle,
                    assistantOfflineEntries = assistantOfflineEntries,
                    assistantOnlineConfigured = assistantOnlineConfigured,
                    assistantBusy = aiBusy,
                    assistantResult = aiResult,
                    onAssistantAsk = studyViewModel::askAi,
                    subjectsContent = {
                        NotCanHomeScreen(
                            recordingState = recordingState,
                            cycles = cycles,
                            subjects = subjects,
                            classes = classes,
                            audioRecordings = audioRecordings,
                            importantMoments = importantMoments,
                            notePages = notePages,
                            documents = documents,
                            pdfInkStrokes = pdfInkStrokes,
                            transcripts = transcripts,
                            detectedCues = detectedCues,
                            whisperModelState = whisperModelState,
                            localWhisperBusy = false,
                            localWhisperError = localWhisperError,
                            selectedCycleId = selectedCycleId,
                            selectedSubjectId = selectedSubjectId,
                            selectedClassId = selectedClassId,
                            selectedNoteId = selectedNoteId,
                            classNavigationRequest = classNavigationRequest,
                            onSelectCycle = studyViewModel::selectCycle,
                            onSelectSubject = studyViewModel::selectSubject,
                            onSelectClass = studyViewModel::selectClass,
                            onSelectNote = studyViewModel::selectNote,
                            onCreateCycle = studyViewModel::createCycle,
                            onCreateSubject = studyViewModel::createSubject,
                            onCreateClass = studyViewModel::createClass,
                            onDeleteClass = studyViewModel::deleteClass,
                            onCreateNote = studyViewModel::createNotePage,
                            onUpdateNote = studyViewModel::updateNotePage,
                            onDeleteNote = studyViewModel::deleteNotePage,
                            onImportNote = ::requestNoteImport,
                            onShareNote = ::shareNote,
                            onShareAudio = ::shareAudio,
                            onDeleteAudio = studyViewModel::deleteAudio,
                            onDeleteTranscript = { transcriptId ->
                                lifecycleScope.launch { recordingRepository.deleteTranscript(transcriptId) }
                            },
                            onTranscribeLocal = ::enqueueBackgroundTranscription,
                            onImportDocument = ::requestDocumentImport,
                            onOpenDocument = ::openDocument,
                            onSavePdfInkStroke = studyViewModel::savePdfInkStroke,
                            onDeletePdfInkStroke = studyViewModel::deletePdfInkStroke,
                            onClearPdfInkPage = studyViewModel::clearPdfInkPage,
                            onStartRecording = { requestedClassId ->
                                if (!recordingActive) {
                                    if (requestedClassId == NEW_CLASS_RECORDING_SENTINEL) {
                                        val subjectId = selectedSubjectId
                                        if (subjectId != null) {
                                            lifecycleScope.launch {
                                                val session = recordingRepository.createClassSession(subjectId, "")
                                                studyViewModel.selectClass(session.id)
                                                requestPermissionsAndStart(session.id)
                                            }
                                        }
                                    } else if (requestedClassId.isNotBlank()) {
                                        studyViewModel.selectClass(requestedClassId)
                                        requestPermissionsAndStart(requestedClassId)
                                    }
                                }
                            },
                            onPauseRecording = { sendRecordingAction(RecordingService.ACTION_PAUSE) },
                            onResumeRecording = { sendRecordingAction(RecordingService.ACTION_RESUME) },
                            onStopRecording = { sendRecordingAction(RecordingService.ACTION_STOP) },
                            onMarkMoment = { sendRecordingAction(RecordingService.ACTION_MARK) }
                        )
                    },
                    tasksContent = {
                        TasksScreen(
                            subjects = subjects,
                            items = taskItems,
                            onAdd = { subjectId, title, type, dueAt, priority, notes -> extrasViewModel.addTask(subjectId, title, type, dueAt, priority, notes) },
                            onCompleted = extrasViewModel::setTaskCompleted,
                            onDelete = extrasViewModel::deleteTask
                        )
                    },
                    calendarContent = {
                        AcademicCalendarScreen(
                            cycle = selectedCycle,
                            subjects = subjects,
                            schedules = schedules,
                            selectedSubjectId = selectedSubjectId,
                            onSaveCycleDates = studyViewModel::updateCycleDates,
                            onAddSchedule = { subjectId, weekday, startMinute, endMinute, mode, grace ->
                                studyViewModel.addSchedule(subjectId, weekday, startMinute, endMinute, mode, grace)
                                requestNotificationPermissionIfNeeded()
                            },
                            onDeleteSchedule = studyViewModel::deleteSchedule,
                            onSyncScheduleToCalendar = ::requestCalendarSync,
                            onOpenOccurrence = { occurrence -> studyViewModel.materializeOccurrence(occurrence) },
                            onRecordOccurrence = { occurrence ->
                                studyViewModel.materializeOccurrence(occurrence) { session ->
                                    requestPermissionsAndStart(session.id, occurrence.endEpochMs, occurrence.schedule.autoStopMode, occurrence.schedule.autoStopGraceMinutes)
                                }
                            }
                        )
                    },
                    aiContent = {
                        NotCanAiScreen(
                            subjectName = selectedSubject?.name,
                            classTitle = selectedClass?.title,
                            configured = aiConfigured,
                            busy = aiBusy,
                            error = aiError,
                            result = aiResult,
                            transcripts = transcripts,
                            audioRecordings = audioRecordings,
                            detectedCues = detectedCues,
                            studyModelState = studyModelState,
                            studyModelProgress = studyModelProgress,
                            whisperModelState = whisperModelState,
                            whisperModelProgress = whisperModelProgress,
                            localWhisperBusy = false,
                            localWhisperError = localWhisperError,
                            onDownloadStudyModel = studyViewModel::downloadStudyModel,
                            onRemoveStudyModel = studyViewModel::removeStudyModel,
                            onDownloadWhisperModel = studyViewModel::downloadWhisperModel,
                            onRemoveWhisperModel = studyViewModel::removeWhisperModel,
                            onTranscribeLocal = ::enqueueBackgroundTranscription,
                            onAsk = studyViewModel::askAi,
                            onClear = studyViewModel::clearAiMessage
                        )
                    },
                    gradesContent = {
                        GradesScreen(
                            subjectName = selectedSubject?.name,
                            items = gradeItems,
                            onAdd = extrasViewModel::addGrade,
                            onDelete = extrasViewModel::deleteGrade
                        )
                    },
                    settingsContent = { SettingsScreen(preferences) }
                )
            }
        }
    }

    private fun toggleDoNotDisturb() {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            Toast.makeText(this, "Permite a NotCan controlar No molestar una sola vez", Toast.LENGTH_LONG).show()
            return
        }
        if (!notCanDndEnabled) {
            previousInterruptionFilter = manager.currentInterruptionFilter
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            notCanDndEnabled = true
            Toast.makeText(this, "No molestar activado 🤫", Toast.LENGTH_SHORT).show()
        } else {
            manager.setInterruptionFilter(previousInterruptionFilter)
            notCanDndEnabled = false
            Toast.makeText(this, "No molestar desactivado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissionsAndStart(classSessionId: String, plannedEndEpochMs: Long? = null, autoStopMode: String? = null, graceMinutes: Int? = null) {
        val resolved = resolveRecordingPlan(classSessionId, plannedEndEpochMs, autoStopMode, graceMinutes)
        pendingRecording = resolved
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (microphoneGranted) {
            pendingRecording = null
            startRecordingService(resolved)
            return
        }
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        microphonePermissionLauncher.launch(permissions)
    }

    private fun resolveRecordingPlan(classSessionId: String, plannedEndEpochMs: Long?, autoStopMode: String?, graceMinutes: Int?): PendingRecording {
        val session = studyViewModel.classes.value.firstOrNull { it.id == classSessionId }
        val schedule = session?.scheduleId?.let { id -> studyViewModel.schedules.value.firstOrNull { it.id == id } }
        return PendingRecording(
            classSessionId = classSessionId,
            classTitle = session?.title ?: "Clase",
            plannedEndEpochMs = plannedEndEpochMs ?: session?.plannedEndEpochMs,
            autoStopMode = autoStopMode ?: schedule?.autoStopMode ?: RecordingService.AUTO_STOP_ASK,
            graceMinutes = graceMinutes ?: schedule?.autoStopGraceMinutes ?: 5
        )
    }

    private fun startRecordingService(request: PendingRecording) {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_CLASS_SESSION_ID, request.classSessionId)
            .putExtra(RecordingService.EXTRA_CLASS_TITLE, request.classTitle)
            .putExtra(RecordingService.EXTRA_AUTO_STOP_MODE, request.autoStopMode)
            .putExtra(RecordingService.EXTRA_AUTO_STOP_GRACE_MINUTES, request.graceMinutes)
            .putExtra(RecordingService.EXTRA_ENABLE_LIVE_TRANSCRIPTION, true)
        request.plannedEndEpochMs?.let { intent.putExtra(RecordingService.EXTRA_PLANNED_END_EPOCH_MS, it) }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun enqueueBackgroundTranscription(audioId: String) {
        val audio = studyViewModel.audioRecordings.value.firstOrNull { it.id == audioId }
        if (audio == null) {
            Toast.makeText(this, "No encontré el audio", Toast.LENGTH_SHORT).show()
            return
        }
        val title = studyViewModel.classes.value.firstOrNull { it.id == audio.classSessionId }?.title ?: File(audio.localPath).nameWithoutExtension
        requestNotificationPermissionIfNeeded()
        BackgroundTranscriptionManager.enqueue(this, audio.id, audio.classSessionId, audio.localPath, title)
        Toast.makeText(this, "Transcripción en segundo plano iniciada", Toast.LENGTH_SHORT).show()
    }

    private fun sendRecordingAction(action: String) {
        startService(Intent(this, RecordingService::class.java).setAction(action))
    }

    private fun requestNoteImport(classSessionId: String) {
        pendingNoteClassId = classSessionId
        noteLauncher.launch(
            arrayOf(
                "text/plain",
                "text/markdown",
                "text/*",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    private fun shareNote(note: NotePageEntity) {
        val text = buildString {
            appendLine(note.title.ifBlank { "Apuntes" })
            appendLine()
            append(markdownToPlainText(note.body))
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, note.title)
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, "Compartir apuntes"))
    }

    private fun shareAudio(audio: AudioRecordingEntity) {
        val file = File(audio.localPath)
        if (!file.exists()) {
            Toast.makeText(this, "El audio ya no existe", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Compartir audio"))
    }

    private fun requestCalendarSync(scheduleId: String) {
        pendingCalendarScheduleId = scheduleId
        val readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            pendingCalendarScheduleId = null
            performCalendarSync(scheduleId)
        } else calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    private fun performCalendarSync(scheduleId: String) {
        val schedule = studyViewModel.schedules.value.firstOrNull { it.id == scheduleId } ?: return
        val cycle = studyViewModel.cycles.value.firstOrNull { it.id == schedule.cycleId } ?: return
        val subject = studyViewModel.subjects.value.firstOrNull { it.id == schedule.subjectId } ?: return
        try {
            val eventId = CalendarSync.syncSchedule(this, cycle, subject, schedule)
            if (eventId != null) {
                studyViewModel.setScheduleCalendarEvent(schedule.id, eventId)
                Toast.makeText(this, "${subject.name} sincronizada con el calendario", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "No encontré un calendario editable en el dispositivo", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "No se pudo sincronizar: ${t.message ?: "error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestDocumentImport(classSessionId: String) {
        pendingDocumentClassId = classSessionId
        documentLauncher.launch(arrayOf("application/pdf", "application/epub+zip", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    private fun openDocument(document: DocumentResourceEntity) {
        val file = File(document.localPath)
        if (!file.exists()) {
            Toast.makeText(this, "El archivo local ya no existe", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No hay una aplicación compatible para abrir este archivo", Toast.LENGTH_SHORT).show() }
    }

    private fun markdownToPlainText(value: String): String = value
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace("**", "")
        .replace("__", "")
        .replace("*", "")
        .replace("_", "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")

    private data class PendingRecording(
        val classSessionId: String,
        val classTitle: String,
        val plannedEndEpochMs: Long?,
        val autoStopMode: String,
        val graceMinutes: Int
    )
}
