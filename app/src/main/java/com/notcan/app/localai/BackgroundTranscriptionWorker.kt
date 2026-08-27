package com.notcan.app.localai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.notcan.app.R
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.settings.NotCanPreferences
import java.io.File
import java.util.UUID

class BackgroundTranscriptionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val audioId = inputData.getString(KEY_AUDIO_ID) ?: return Result.failure()
        val classSessionId = inputData.getString(KEY_CLASS_ID) ?: return Result.failure()
        val path = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: "clase"
        val audio = File(path)
        if (!audio.exists()) return Result.failure(workDataOf(KEY_ERROR to "El audio local ya no existe"))

        createChannel()
        setForeground(foregroundInfo("Preparando $displayName…"))

        return try {
            val text = LocalWhisperEngine(applicationContext).transcribeM4a(audio)
            val now = System.currentTimeMillis()
            val transcriptId = UUID.randomUUID().toString()
            val dao = NotCanDatabase.getInstance(applicationContext).dao()
            dao.insertTranscript(
                TranscriptEntity(
                    id = transcriptId,
                    classSessionId = classSessionId,
                    audioId = audioId,
                    body = text,
                    status = "FINAL_LOCAL",
                    modelName = WhisperModelSpec.DISPLAY_NAME,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )

            if (NotCanPreferences(applicationContext).autoDetectAcademicCues) {
                dao.deleteDetectedCuesForTranscript(transcriptId)
                AcademicCueDetector.detect(text, classSessionId, transcriptId, audioId)
                    .forEach { dao.insertDetectedCue(it) }
            }

            notifyFinished(displayName)
            Result.success(workDataOf(KEY_TRANSCRIPT_ID to transcriptId))
        } catch (t: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "No se pudo transcribir el audio")))
        }
    }

    private fun foregroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NotCan · Transcripción local")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Whisper large-v3-turbo está transcribiendo en segundo plano. Puedes cerrar NotCan."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transcripción y procesos de estudio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Procesos locales largos que continúan aunque NotCan no esté abierta"
            }
        )
    }

    private fun notifyFinished(displayName: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            COMPLETED_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Transcripción terminada")
                .setContentText(displayName)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val TAG = "notcan-background-transcription"
        private const val CHANNEL_ID = "notcan_background_processing"
        private const val NOTIFICATION_ID = 2401
        private const val COMPLETED_NOTIFICATION_ID = 2402
        const val KEY_AUDIO_ID = "audio_id"
        const val KEY_CLASS_ID = "class_id"
        const val KEY_AUDIO_PATH = "audio_path"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TRANSCRIPT_ID = "transcript_id"
        const val KEY_ERROR = "error"
    }
}

object BackgroundTranscriptionManager {
    fun enqueue(
        context: Context,
        audioId: String,
        classSessionId: String,
        audioPath: String,
        displayName: String
    ): UUID {
        val request = OneTimeWorkRequestBuilder<BackgroundTranscriptionWorker>()
            .setInputData(
                Data.Builder()
                    .putString(BackgroundTranscriptionWorker.KEY_AUDIO_ID, audioId)
                    .putString(BackgroundTranscriptionWorker.KEY_CLASS_ID, classSessionId)
                    .putString(BackgroundTranscriptionWorker.KEY_AUDIO_PATH, audioPath)
                    .putString(BackgroundTranscriptionWorker.KEY_DISPLAY_NAME, displayName)
                    .build()
            )
            .addTag(BackgroundTranscriptionWorker.TAG)
            .addTag("notcan-audio-$audioId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "notcan-transcribe-$audioId",
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    fun isAnyActive(context: Context): Boolean = runCatching {
        WorkManager.getInstance(context).getWorkInfosByTag(BackgroundTranscriptionWorker.TAG).get()
            .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    }.getOrDefault(false)
}
