package com.notcan.app.calendar

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val TAG = "notcan-semester-reminders"

    fun reschedule(
        context: Context,
        cycle: StudyCycleEntity?,
        subjects: List<SubjectEntity>,
        schedules: List<SubjectScheduleEntity>
    ) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(TAG)
        if (cycle == null) return
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
            val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .addTag(TAG)
                .build()
            workManager.enqueue(request)
        }
    }
}
