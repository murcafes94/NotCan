package com.notcan.app.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.notcan.app.MainActivity
import com.notcan.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight foreground player for class recordings.
 * Playback survives navigation between class views and continues when NotCan is backgrounded.
 */
class AudioPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private var currentId: String? = null
    private var currentPath: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val id = intent.getStringExtra(EXTRA_AUDIO_ID) ?: return START_NOT_STICKY
                val path = intent.getStringExtra(EXTRA_AUDIO_PATH) ?: return START_NOT_STICKY
                toggle(id, path)
            }
            ACTION_SEEK -> {
                val offset = intent.getLongExtra(EXTRA_OFFSET_MS, 0L).coerceAtLeast(0L)
                runCatching { player?.seekTo(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
                publishState()
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun toggle(id: String, path: String) {
        if (currentId != id || currentPath != path || player == null) {
            startNew(id, path)
            return
        }
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            publishState()
            startForeground(NOTIFICATION_ID, notification(false))
        } else {
            p.start()
            publishState()
            startForeground(NOTIFICATION_ID, notification(true))
        }
    }

    private fun startNew(id: String, path: String) {
        runCatching { player?.release() }
        player = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            setOnCompletionListener {
                publishState(forcePlaying = false, forcePosition = duration.toLong())
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            setOnErrorListener { _, _, _ ->
                stopPlayback()
                true
            }
            start()
        }
        currentId = id
        currentPath = path
        publishState()
        startForeground(NOTIFICATION_ID, notification(true))
    }

    private fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        currentId = null
        currentPath = null
        _state.value = AudioPlaybackState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishState(forcePlaying: Boolean? = null, forcePosition: Long? = null) {
        val p = player
        _state.value = AudioPlaybackState(
            audioId = currentId,
            isPlaying = forcePlaying ?: runCatching { p?.isPlaying == true }.getOrDefault(false),
            positionMs = forcePosition ?: runCatching { p?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L),
            durationMs = runCatching { p?.duration?.toLong() ?: 0L }.getOrDefault(0L)
        )
    }

    private fun notification(playing: Boolean): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this,
            21,
            Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(EXTRA_AUDIO_ID, currentId)
                putExtra(EXTRA_AUDIO_PATH, currentPath)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            22,
            Intent(this, AudioPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notcan_mic)
            .setContentTitle("Audio de clase")
            .setContentText(if (playing) "Reproduciendo en segundo plano" else "Reproducción pausada")
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(0, if (playing) "Pausar" else "Reanudar", toggleIntent)
            .addAction(0, "Detener", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reproducción de clases",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Mantiene el audio de clase activo mientras estudias en NotCan."
                    setSound(null, null)
                }
            )
        }
    }

    override fun onDestroy() {
        runCatching { player?.release() }
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TOGGLE = "com.notcan.app.audio.TOGGLE"
        const val ACTION_SEEK = "com.notcan.app.audio.SEEK"
        const val ACTION_STOP = "com.notcan.app.audio.STOP"
        const val EXTRA_AUDIO_ID = "audio_id"
        const val EXTRA_AUDIO_PATH = "audio_path"
        const val EXTRA_OFFSET_MS = "offset_ms"

        private const val CHANNEL_ID = "notcan_class_audio"
        private const val NOTIFICATION_ID = 2406

        private val _state = MutableStateFlow(AudioPlaybackState())
        val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()
    }
}

data class AudioPlaybackState(
    val audioId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)
