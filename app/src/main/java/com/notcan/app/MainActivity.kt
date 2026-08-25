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
import com.notcan.app.recording.RecordingService
import com.notcan.app.ui.home.NotCanHomeScreen
import com.notcan.app.ui.theme.NotCanTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (microphoneGranted) {
            startRecordingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotCanTheme {
                val recordingState = RecordingService.state.collectAsStateWithLifecycle().value

                NotCanHomeScreen(
                    recordingState = recordingState,
                    onStartRecording = ::requestPermissionsAndStart,
                    onPauseRecording = { sendRecordingAction(RecordingService.ACTION_PAUSE) },
                    onResumeRecording = { sendRecordingAction(RecordingService.ACTION_RESUME) },
                    onStopRecording = { sendRecordingAction(RecordingService.ACTION_STOP) },
                    onMarkMoment = { sendRecordingAction(RecordingService.ACTION_MARK) }
                )
            }
        }
    }

    private fun requestPermissionsAndStart() {
        val microphoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (microphoneGranted) {
            startRecordingService()
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

    private fun startRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendRecordingAction(action: String) {
        startService(
            Intent(this, RecordingService::class.java).setAction(action)
        )
    }
}
