package com.notcan.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notcan.app.recording.RecordingService
import com.notcan.app.ui.NotCanViewModel
import com.notcan.app.ui.home.NotCanHomeScreen
import com.notcan.app.ui.theme.NotCanTheme

class MainActivity : ComponentActivity() {

    private var pendingClassSessionId: String? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotCanTheme {
                val viewModel: NotCanViewModel = viewModel()
                val recordingState = RecordingService.state.collectAsStateWithLifecycle().value
                val cycles = viewModel.cycles.collectAsStateWithLifecycle().value
                val subjects = viewModel.subjects.collectAsStateWithLifecycle().value
                val classes = viewModel.classes.collectAsStateWithLifecycle().value
                val audioRecordings = viewModel.audioRecordings.collectAsStateWithLifecycle().value
                val importantMoments = viewModel.importantMoments.collectAsStateWithLifecycle().value
                val selectedCycleId = viewModel.selectedCycleId.collectAsStateWithLifecycle().value
                val selectedSubjectId = viewModel.selectedSubjectId.collectAsStateWithLifecycle().value
                val selectedClassId = viewModel.selectedClassId.collectAsStateWithLifecycle().value

                NotCanHomeScreen(
                    recordingState = recordingState,
                    cycles = cycles,
                    subjects = subjects,
                    classes = classes,
                    audioRecordings = audioRecordings,
                    importantMoments = importantMoments,
                    selectedCycleId = selectedCycleId,
                    selectedSubjectId = selectedSubjectId,
                    selectedClassId = selectedClassId,
                    onSelectCycle = viewModel::selectCycle,
                    onSelectSubject = viewModel::selectSubject,
                    onSelectClass = viewModel::selectClass,
                    onCreateCycle = viewModel::createCycle,
                    onCreateSubject = viewModel::createSubject,
                    onCreateClass = viewModel::createClass,
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
}
