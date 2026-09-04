package com.notcan.app.calendar

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val TAG = "notcan-semester-reminders"
    private const val PREFS = "notcan_reminder_scheduler"
    private const val KEY_SIGNATURE = "signature"

    fun reschedule(
        context: Context,
        cycle: StudyCycleEntity?,
        subjects: List<SubjectEntity>,
        schedules: List<SubjectScheduleEntity>
    ) {
        val workManager = WorkManager.getInstance(context)
        if (cycle == null) {
            workManager.cancelAllWorkByTag(TAG)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            return
        }

        val signature = signature(cycle, schedules)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SIGNATURE, null) == signature) return

        workManager.cancelAllWorkByTag(TAG)
        val now = System.currentTimeMillis()
        AcademicSchedule.allOccurrences(cycle, subjects, schedules).forEach { occurrence ->
            val reminderAt = occurrence.startEpochMs - occurrence.schedule.reminderMinutesBefore * 60_000L
            val delay = reminderAt - now
            if (delay <= 0L) return@forEach
            val input = Data.Builder()
                .putString(ClassReminderWorker.KEY_SUBJECT, occurrence.subject.name)
                .putString(ClassReminderWorker.KEY_TIME, AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay))
                .putInt(ClassReminderWorker.KEY_NOTIFICATION_ID, (occurrence.schedule.id + occurrence.date).hashCode())
                .build()
            val uniqueName = "notcan-reminder-${occurrence.schedule.id}-${occurrence.date}"
            val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
        prefs.edit().putString(KEY_SIGNATURE, signature).apply()
    }

    private fun signature(cycle: StudyCycleEntity, schedules: List<SubjectScheduleEntity>): String {
        val raw = buildString {
            append(cycle.id).append('|').append(cycle.startEpochDay).append('|').append(cycle.endEpochDay)
            schedules.sortedBy { it.id }.forEach { s ->
                append('|').append(s.id)
                    .append(':').append(s.subjectId)
                    .append(':').append(s.weekdayIso)
                    .append(':').append(s.startMinuteOfDay)
                    .append(':').append(s.endMinuteOfDay)
                    .append(':').append(s.reminderMinutesBefore)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
    }
}
