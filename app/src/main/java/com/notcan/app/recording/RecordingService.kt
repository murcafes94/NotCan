package com.notcan.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.notcan.app.MainActivity
import com.notcan.app.R
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotCanDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtEpochMs: Long = 0L
    private var pausedAtEpochMs: Long? = null
    private var totalPausedMs: Long = 0L
    private var currentClassSessionId: String? = null
    private var currentAudioId: String? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { NotCanDatabase.getInstance(this).dao() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startRecording(intent?.getStringExtra(EXTRA_CLASS_SESSION_ID))
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_MARK -> markMoment()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseRecorder()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording(classSessionId: String?) {
        if (recorder != null) return
        if (classSessionId.isNullOrBlank()) {
            _state.value = RecordingState.Error("Selecciona una clase antes de comenzar a grabar")
            stopSelf()
            return
        }

        try {
            val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val audioId = UUID.randomUUID().toString()
            outputFile = File(recordingsDir, "notcan_$stamp.m4a")
            startedAtEpochMs = System.currentTimeMillis()
            pausedAtEpochMs = null
            totalPausedMs = 0L
            currentClassSessionId = classSessionId
            currentAudioId = audioId

            recorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }

            _state.value = RecordingState.Recording(
                startedAtEpochMs = startedAtEpochMs,
                outputPath = outputFile!!.absolutePath,
                classSessionId = classSessionId,
                audioId = audioId
            )
            startForeground(NOTIFICATION_ID, buildNotification(isPaused = false))
        } catch (t: Throwable) {
            _state.value = RecordingState.Error(t.message ?: "No se pudo iniciar la grabación")
            outputFile?.delete()
            releaseRecorder()
            clearSessionState()
            stopSelf()
        }
    }

    private fun pauseRecording() {
        val current = _state.value
        if (current !is RecordingState.Recording) return

        try {
            recorder?.pause()
            pausedAtEpochMs = System.currentTimeMillis()
            _state.value = RecordingState.Paused(
                current.startedAtEpochMs,
                current.outputPath,
                current.classSessionId,
                current.audioId
            )
            notifyState(isPaused = true)
        } catch (t: Throwable) {
            _state.value = RecordingState.Error(t.message ?: "No se pudo pausar")
        }
    }

    private fun resumeRecording() {
        val current = _state.value
        if (current !is RecordingState.Paused) return

        try {
            recorder?.resume()
            val now = System.currentTimeMillis()
            pausedAtEpochMs?.let { totalPausedMs += (now - it).coerceAtLeast(0L) }
            pausedAtEpochMs = null
            _state.value = RecordingState.Recording(
                current.startedAtEpochMs,
                current.outputPath,
                current.classSessionId,
                current.audioId
            )
            notifyState(isPaused = false)
        } catch (t: Throwable) {
            _state.value = RecordingState.Error(t.message ?: "No se pudo reanudar")
        }
    }

    private fun stopRecording() {
        val path = outputFile?.absolutePath
        val classSessionId = currentClassSessionId
        val audioId = currentAudioId
        val durationMs = recordedElapsedMs()

        try {
            recorder?.stop()
        } catch (_: Throwable) {
            // Android puede rechazar stop si la grabación fue extremadamente corta.
        } finally {
            releaseRecorder()
        }

        if (path == null || classSessionId == null || audioId == null) {
            _state.value = RecordingState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            clearSessionState()
            stopSelf()
            return
        }

        val createdAt = startedAtEpochMs
        serviceScope.launch {
            dao.insertAudioRecording(
                AudioRecordingEntity(
                    id = audioId,
                    classSessionId = classSessionId,
                    localPath = path,
                    durationMs = durationMs,
                    createdAtEpochMs = createdAt
                )
            )

            withContext(Dispatchers.Main) {
                _state.value = RecordingState.Finished(
                    outputPath = path,
                    classSessionId = classSessionId,
                    audioId = audioId,
                    durationMs = durationMs
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                clearSessionState()
                stopSelf()
            }
        }
    }

    private fun markMoment() {
        val current = _state.value
        val classSessionId: String
        val audioId: String
        val outputPath: String

        when (current) {
            is RecordingState.Recording -> {
                classSessionId = current.classSessionId
                audioId = current.audioId
                outputPath = current.outputPath
            }
            is RecordingState.Paused -> {
                classSessionId = current.classSessionId
                audioId = current.audioId
                outputPath = current.outputPath
            }
            else -> return
        }

        val offsetMs = recordedElapsedMs()
        val createdAt = System.currentTimeMillis()
        val momentId = UUID.randomUUID().toString()

        // CSV mínimo como mecanismo de recuperación si la base local se dañara.
        File("$outputPath.markers.csv").appendText("$momentId,$offsetMs,$createdAt\n")

        serviceScope.launch {
            dao.insertImportantMoment(
                ImportantMomentEntity(
                    id = momentId,
                    classSessionId = classSessionId,
                    audioId = audioId,
                    offsetMs = offsetMs,
                    createdAtEpochMs = createdAt
                )
            )
        }
    }

    private fun recordedElapsedMs(now: Long = System.currentTimeMillis()): Long {
        if (startedAtEpochMs == 0L) return 0L
        val effectiveNow = pausedAtEpochMs ?: now
        return (effectiveNow - startedAtEpochMs - totalPausedMs).coerceAtLeast(0L)
    }

    private fun clearSessionState() {
        outputFile = null
        startedAtEpochMs = 0L
        pausedAtEpochMs = null
        totalPausedMs = 0L
        currentClassSessionId = null
        currentAudioId = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.reset()
        } catch (_: Throwable) {
        }
        try {
            recorder?.release()
        } catch (_: Throwable) {
        }
        recorder = null
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else MediaRecorder()

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val pauseOrResumeLabel = if (isPaused) "Reanudar" else "Pausar"
        val pauseOrResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(if (isPaused) "Grabación pausada" else "Grabación en curso")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                pauseOrResumeIcon,
                pauseOrResumeLabel,
                servicePendingIntent(20, pauseOrResumeAction)
            )
            .addAction(
                android.R.drawable.btn_star,
                "Marcar",
                servicePendingIntent(21, ACTION_MARK)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Detener",
                servicePendingIntent(22, ACTION_STOP)
            )
            .build()
    }

    private fun notifyState(isPaused: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles discretos de grabación de NotCan"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.notcan.app.recording.START"
        const val ACTION_PAUSE = "com.notcan.app.recording.PAUSE"
        const val ACTION_RESUME = "com.notcan.app.recording.RESUME"
        const val ACTION_STOP = "com.notcan.app.recording.STOP"
        const val ACTION_MARK = "com.notcan.app.recording.MARK"
        const val EXTRA_CLASS_SESSION_ID = "class_session_id"

        private const val CHANNEL_ID = "notcan_recording"
        private const val NOTIFICATION_ID = 2201

        private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val state: StateFlow<RecordingState> = _state.asStateFlow()
    }
}
