package com.notcan.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.recording.RecordingService
import com.notcan.app.ui.NotCanViewModel
import com.notcan.app.ui.home.NotCanHomeScreen
import com.notcan.app.ui.theme.NotCanTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val studyViewModel: NotCanViewModel by viewModels()
    private var pendingClassSessionId: String? = null
    private var pendingDocumentClassId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        val classSessionId = pendingClassSessionId
        pendingClassSessionId = null

        if (microphoneGranted && classSessionId != null) {
            startRecordingService(classSessionId)
        }
    }

    private val documentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val classSessionId = pendingDocumentClassId
        pendingDocumentClassId = null
        if (uri != null && classSessionId != null) {
            studyViewModel.importDocument(classSessionId, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotCanTheme {
                val recordingState = RecordingService.state.collectAsStateWithLifecycle().value
                val cycles = studyViewModel.cycles.collectAsStateWithLifecycle().value
                val subjects = studyViewModel.subjects.collectAsStateWithLifecycle().value
                val classes = studyViewModel.classes.collectAsStateWithLifecycle().value
                val audioRecordings = studyViewModel.audioRecordings.collectAsStateWithLifecycle().value
                val importantMoments = studyViewModel.importantMoments.collectAsStateWithLifecycle().value
                val notePages = studyViewModel.notePages.collectAsStateWithLifecycle().value
                val documents = studyViewModel.documents.collectAsStateWithLifecycle().value
                val selectedCycleId = studyViewModel.selectedCycleId.collectAsStateWithLifecycle().value
                val selectedSubjectId = studyViewModel.selectedSubjectId.collectAsStateWithLifecycle().value
                val selectedClassId = studyViewModel.selectedClassId.collectAsStateWithLifecycle().value
                val selectedNoteId = studyViewModel.selectedNoteId.collectAsStateWithLifecycle().value

                NotCanHomeScreen(
                    recordingState = recordingState,
                    cycles = cycles,
                    subjects = subjects,
                    classes = classes,
                    audioRecordings = audioRecordings,
                    importantMoments = importantMoments,
                    notePages = notePages,
                    documents = documents,
                    selectedCycleId = selectedCycleId,
                    selectedSubjectId = selectedSubjectId,
                    selectedClassId = selectedClassId,
                    selectedNoteId = selectedNoteId,
                    onSelectCycle = studyViewModel::selectCycle,
                    onSelectSubject = studyViewModel::selectSubject,
                    onSelectClass = studyViewModel::selectClass,
                    onSelectNote = studyViewModel::selectNote,
                    onCreateCycle = studyViewModel::createCycle,
                    onCreateSubject = studyViewModel::createSubject,
                    onCreateClass = studyViewModel::createClass,
                    onCreateNote = studyViewModel::createNotePage,
                    onUpdateNote = studyViewModel::updateNotePage,
                    onImportDocument = ::requestDocumentImport,
                    onOpenDocument = ::openDocument,
                    onStartRecording = ::requestPermissionsAndStart,
                    onPauseRecording = { sendRecordingAction(RecordingService.ACTION_PAUSE) },
                    onResumeRecording = { sendRecordingAction(RecordingService.ACTION_RESUME) },
                    onStopRecording = { sendRecordingAction(RecordingService.ACTION_STOP) },
                    onMarkMoment = { sendRecordingAction(RecordingService.ACTION_MARK) }
                )
            }
        }
    }

    private fun requestPermissionsAndStart(classSessionId: String) {
        pendingClassSessionId = classSessionId

        val microphoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (microphoneGranted) {
            pendingClassSessionId = null
            startRecordingService(classSessionId)
            return
        }

        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        permissionLauncher.launch(permissions)
    }

    private fun startRecordingService(classSessionId: String) {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_CLASS_SESSION_ID, classSessionId)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendRecordingAction(action: String) {
        startService(
            Intent(this, RecordingService::class.java).setAction(action)
        )
    }

    private fun requestDocumentImport(classSessionId: String) {
        pendingDocumentClassId = classSessionId
        documentLauncher.launch(
            arrayOf(
                "application/pdf",
                "application/epub+zip",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    private fun openDocument(document: DocumentResourceEntity) {
        val file = File(document.localPath)
        if (!file.exists()) {
            Toast.makeText(this, "El archivo local ya no existe", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, document.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No hay una aplicación compatible para abrir este archivo", Toast.LENGTH_SHORT).show()
        }
    }
}
