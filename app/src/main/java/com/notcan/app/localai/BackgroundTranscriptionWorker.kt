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
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.settings.NotCanPreferences
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import kotlin.math.abs

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
            val dao = NotCanDatabase.getInstance(applicationContext).dao()
            val classSession = dao.getClassSession(classSessionId)
            val subject = classSession?.let { dao.getSubject(it.subjectId) }
            val storedVocabulary = subject?.let { selectedSubject ->
                dao.observeVocabularyForCycle(selectedSubject.cycleId)
                    .first()
                    .filter { term -> term.subjectId == null || term.subjectId == selectedSubject.id }
            }.orEmpty()
            val noteContext = dao.getNotesForClass(classSessionId)
                .flatMap { note -> listOf(note.title, note.body) }
            val academicTerms = AcademicTranscriptionContext.buildTerms(
                subjectName = subject?.name,
                classTitle = displayName,
                stored = storedVocabulary,
                contextTexts = noteContext
            )

            setForeground(
                foregroundInfo(
                    if (academicTerms.isNotEmpty()) {
                        "Transcribiendo $displayName · vocabulario académico…"
                    } else {
                        "Transcribiendo $displayName…"
                    }
                )
            )

            val rawTranscription = LocalWhisperEngine(applicationContext).transcribeM4aDetailed(audio)
            val transcription = AcademicTranscriptionContext.correct(rawTranscription, academicTerms)
            val plainText = transcription.text.trim()
            val timedText = transcription.segments
                .joinToString(separator = "\n\n") { segment ->
                    "[${formatTimestamp(segment.startMs)}–${formatTimestamp(segment.endMs)}] ${segment.text}"
                }
                .ifBlank { plainText }

            val now = System.currentTimeMillis()
            val transcriptId = "final-$audioId"
            dao.insertTranscript(
                TranscriptEntity(
                    id = transcriptId,
                    classSessionId = classSessionId,
                    audioId = audioId,
                    body = timedText,
                    status = "FINAL_LOCAL_TIMED",
                    modelName = if (academicTerms.isNotEmpty()) {
                        "${WhisperModelSpec.DISPLAY_NAME} · contexto académico"
                    } else {
                        WhisperModelSpec.DISPLAY_NAME
                    },
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )

            // La transcripción definitiva también queda como un apunte normal y editable.
            // El id determinista evita crear copias si WorkManager reintenta el mismo audio.
            val noteId = "transcript-note-$audioId"
            if (dao.getNotePage(noteId) == null && plainText.isNotBlank()) {
                dao.insertNotePage(
                    NotePageEntity(
                        id = noteId,
                        classSessionId = classSessionId,
                        title = "Transcripción — $displayName",
                        body = plainText,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                )
            }

            // Completa los marcadores creados durante la grabación con el texto que se
            // estaba diciendo en ese instante. El tiempo exacto del marcador se conserva.
            if (transcription.segments.isNotEmpty()) {
                dao.observeImportantMoments(classSessionId).first()
                    .asSequence()
                    .filter { it.audioId == audioId && it.note.isNullOrBlank() }
                    .forEach { moment ->
                        val segment = findSegment(transcription.segments, moment.offsetMs)
                        if (segment != null) {
                            dao.insertImportantMoment(
                                moment.copy(
                                    note = segment.text
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                        .take(280)
                                )
                            )
                        }
                    }
            }

            // Los capítulos son navegación de la clase, no una modificación del texto.
            // Se regeneran de forma determinista cada vez que se vuelve a procesar el audio.
            dao.deleteDetectedCuesForTranscript(transcriptId)
            ClassChapterDetector.detect(
                segments = transcription.segments,
                terms = academicTerms,
                classSessionId = classSessionId,
                transcriptId = transcriptId,
                audioId = audioId
            ).forEach { dao.insertDetectedCue(it) }

            if (NotCanPreferences(applicationContext).autoDetectAcademicCues) {
                AcademicCueDetector.detect(plainText, classSessionId, transcriptId, audioId)
                    .forEach { dao.insertDetectedCue(it) }
            }

            notifyFinished(displayName, academicTerms.isNotEmpty())
            Result.success(
                workDataOf(
                    KEY_TRANSCRIPT_ID to transcriptId,
                    KEY_CONTEXT_TERMS to academicTerms.size
                )
            )
        } catch (t: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "No se pudo transcribir el audio")))
        }
    }

    private fun findSegment(segments: List<WhisperSegmentResult>, offsetMs: Long): WhisperSegmentResult? {
        segments.firstOrNull { offsetMs in it.startMs..it.endMs }?.let { return it }
        return segments.minByOrNull { segment ->
            val center = segment.startMs + ((segment.endMs - segment.startMs) / 2L)
            abs(center - offsetMs)
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    private fun foregroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NotCan · Transcripción local")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${WhisperModelSpec.DISPLAY_NAME} está transcribiendo en segundo plano. Puedes cerrar NotCan."
                )
            )
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

    private fun notifyFinished(displayName: String, usedAcademicContext: Boolean) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            COMPLETED_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Transcripción terminada")
                .setContentText(
                    if (usedAcademicContext) {
                        "$displayName · apunte editable y capítulos creados"
                    } else {
                        "$displayName · apunte editable creado"
                    }
                )
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
        const val KEY_CONTEXT_TERMS = "context_terms"
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
