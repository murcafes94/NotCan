package com.notcan.app.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.notcan.app.MainActivity
import com.notcan.app.R
import com.notcan.app.ai.GeminiLiveTranscriber
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.TranscriptEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RecordingService : Service() {

    private var recorder: AacM4aRecorder? = null
    private var outputFile: File? = null
    private var startedAtEpochMs: Long = 0L
    private var currentClassSessionId: String? = null
    private var currentAudioId: String? = null
    private var liveTranscriber: GeminiLiveTranscriber? = null
    private var pcmChannel: Channel<ByteArray>? = null
    private var liveSenderJob: Job? = null
    private var scheduleEndJob: Job? = null
    private var plannedEndEpochMs: Long? = null
    private var autoStopMode: String = AUTO_STOP_ASK
    private var autoStopGraceMinutes: Int = 5
    private val stopping = AtomicBoolean(false)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { NotCanDatabase.getInstance(this).dao() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startRecording(intent)
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_MARK -> markMoment()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduleEndJob?.cancel()
        liveSenderJob?.cancel()
        pcmChannel?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording(intent: Intent?) {
        if (recorder != null) return
        val classSessionId = intent?.getStringExtra(EXTRA_CLASS_SESSION_ID)
        if (classSessionId.isNullOrBlank()) {
            _state.value = RecordingState.Error("Selecciona una clase antes de comenzar a grabar")
            stopSelf()
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = RecordingState.Error("NotCan necesita permiso de micrófono para grabar")
            stopSelf()
            return
        }

        try {
            val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val audioId = UUID.randomUUID().toString()
            val file = File(recordingsDir, "notcan_$stamp.m4a")
            outputFile = file
            startedAtEpochMs = System.currentTimeMillis()
            currentClassSessionId = classSessionId
            currentAudioId = audioId
            plannedEndEpochMs = intent.getLongExtra(EXTRA_PLANNED_END_EPOCH_MS, -1L).takeIf { it > 0L }
            autoStopMode = intent.getStringExtra(EXTRA_AUTO_STOP_MODE) ?: AUTO_STOP_ASK
            autoStopGraceMinutes = intent.getIntExtra(EXTRA_AUTO_STOP_GRACE_MINUTES, 5).coerceIn(0, 60)
            stopping.set(false)
            _liveTranscript.value = ""
            _aiStatus.value = if (intent.getBooleanExtra(EXTRA_ENABLE_LIVE_TRANSCRIPTION, true)) {
                "Conectando transcripción en vivo…"
            } else {
                "Transcripción en vivo desactivada"
            }

            val liveEnabled = intent.getBooleanExtra(EXTRA_ENABLE_LIVE_TRANSCRIPTION, true)
            val channel = if (liveEnabled) Channel<ByteArray>(capacity = 64) else null
            pcmChannel = channel

            if (liveEnabled && channel != null) {
                val transcriber = GeminiLiveTranscriber(
                    context = this,
                    scope = serviceScope,
                    onTranscriptChunk = { chunk ->
                        _liveTranscript.update { current ->
                            if (current.isBlank()) chunk.trim() else "$current ${chunk.trim()}"
                        }
                    },
                    onStatus = { _aiStatus.value = it }
                )
                liveTranscriber = transcriber
                serviceScope.launch {
                    val active = transcriber.start()
                    if (!active) {
                        channel.close()
                        return@launch
                    }
                    liveSenderJob = launch {
                        for (pcm in channel) {
                            if (!isActive) break
                            transcriber.sendPcm16Khz(pcm)
                        }
                    }
                }
            }

            val engine = AacM4aRecorder(
                context = this,
                outputFile = file,
                scope = serviceScope,
                onPcmChunk = { pcm -> pcmChannel?.trySend(pcm) }
            )
            recorder = engine
            engine.start()

            _state.value = RecordingState.Recording(
                startedAtEpochMs = startedAtEpochMs,
                outputPath = file.absolutePath,
                classSessionId = classSessionId,
                audioId = audioId
            )
            startForeground(NOTIFICATION_ID, buildNotification(isPaused = false))
            schedulePlannedEndBehavior()
        } catch (t: Throwable) {
            _state.value = RecordingState.Error(t.message ?: "No se pudo iniciar la grabación")
            outputFile?.delete()
            clearSessionState()
            stopSelf()
        }
    }

    private fun pauseRecording() {
        val current = _state.value
        if (current !is RecordingState.Recording) return
        try {
            recorder?.pause()
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
        if (!stopping.compareAndSet(false, true)) return
        val path = outputFile?.absolutePath
        val classSessionId = currentClassSessionId
        val audioId = currentAudioId
        val createdAt = startedAtEpochMs
        scheduleEndJob?.cancel()

        if (path == null || classSessionId == null || audioId == null) {
            finishWithoutFile()
            return
        }

        serviceScope.launch {
            val durationMs = try {
                recorder?.stop() ?: 0L
            } catch (_: Throwable) {
                recorder?.elapsedMs() ?: 0L
            }
            recorder = null
            pcmChannel?.close()
            try { liveSenderJob?.join() } catch (_: Throwable) { }
            try { liveTranscriber?.close() } catch (_: Throwable) { }
            liveTranscriber = null

            val file = File(path)
            if (file.exists() && file.length() > 0L) {
                dao.insertAudioRecording(
                    AudioRecordingEntity(
                        id = audioId,
                        classSessionId = classSessionId,
                        localPath = path,
                        durationMs = durationMs,
                        createdAtEpochMs = createdAt
                    )
                )
                val liveText = _liveTranscript.value.trim()
                if (liveText.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    dao.insertTranscript(
                        TranscriptEntity(
                            id = UUID.randomUUID().toString(),
                            classSessionId = classSessionId,
                            audioId = audioId,
                            body = liveText,
                            status = "LIVE_CAPTURE",
                            modelName = "gemini-live",
                            createdAtEpochMs = createdAt,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }

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

    private fun schedulePlannedEndBehavior() {
        val end = plannedEndEpochMs ?: return
        if (autoStopMode == AUTO_STOP_CONTINUE) return
        val target = if (autoStopMode == AUTO_STOP_AUTO) {
            end + autoStopGraceMinutes * 60_000L
        } else {
            end
        }
        val delayMs = (target - System.currentTimeMillis()).coerceAtLeast(0L)
        scheduleEndJob = serviceScope.launch {
            delay(delayMs)
            if (autoStopMode == AUTO_STOP_AUTO) {
                stopRecording()
            } else if (autoStopMode == AUTO_STOP_ASK) {
                notifyScheduleEnded()
            }
        }
    }

    private fun recordedElapsedMs(): Long = recorder?.elapsedMs() ?: 0L

    private fun finishWithoutFile() {
        serviceScope.launch {
            try { recorder?.stop() } catch (_: Throwable) { }
            try { liveTranscriber?.close() } catch (_: Throwable) { }
            withContext(Dispatchers.Main) {
                _state.value = RecordingState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                clearSessionState()
                stopSelf()
            }
        }
    }

    private fun clearSessionState() {
        recorder = null
        outputFile = null
        startedAtEpochMs = 0L
        currentClassSessionId = null
        currentAudioId = null
        plannedEndEpochMs = null
        scheduleEndJob = null
        liveSenderJob = null
        pcmChannel = null
        liveTranscriber = null
        autoStopMode = AUTO_STOP_ASK
        autoStopGraceMinutes = 5
        stopping.set(false)
    }

    private fun buildNotification(isPaused: Boolean, scheduleEnded: Boolean = false): Notification {
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseOrResumeAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val pauseOrResumeLabel = if (isPaused) "Reanudar" else "Pausar"
        val pauseOrResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val text = when {
            scheduleEnded -> "Terminó el horario previsto · detén cuando termine la clase"
            isPaused -> "Grabación pausada"
            else -> "Grabación en curso"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(text)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseOrResumeIcon, pauseOrResumeLabel, servicePendingIntent(20, pauseOrResumeAction))
            .addAction(android.R.drawable.btn_star, "Marcar", servicePendingIntent(21, ACTION_MARK))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", servicePendingIntent(22, ACTION_STOP))
            .build()
    }

    private fun notifyState(isPaused: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun notifyScheduleEnded() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isPaused = recorder?.isPaused() == true, scheduleEnded = true))
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
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

    companion object {
        const val ACTION_START = "com.notcan.app.recording.START"
        const val ACTION_PAUSE = "com.notcan.app.recording.PAUSE"
        const val ACTION_RESUME = "com.notcan.app.recording.RESUME"
        const val ACTION_STOP = "com.notcan.app.recording.STOP"
        const val ACTION_MARK = "com.notcan.app.recording.MARK"
        const val EXTRA_CLASS_SESSION_ID = "class_session_id"
        const val EXTRA_PLANNED_END_EPOCH_MS = "planned_end_epoch_ms"
        const val EXTRA_AUTO_STOP_MODE = "auto_stop_mode"
        const val EXTRA_AUTO_STOP_GRACE_MINUTES = "auto_stop_grace_minutes"
        const val EXTRA_ENABLE_LIVE_TRANSCRIPTION = "enable_live_transcription"

        const val AUTO_STOP_ASK = "ASK"
        const val AUTO_STOP_AUTO = "AUTO"
        const val AUTO_STOP_CONTINUE = "CONTINUE"

        private const val CHANNEL_ID = "notcan_recording"
        private const val NOTIFICATION_ID = 2201

        private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val state: StateFlow<RecordingState> = _state.asStateFlow()
        private val _liveTranscript = MutableStateFlow("")
        val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()
        private val _aiStatus = MutableStateFlow("IA inactiva")
        val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()
    }
}
