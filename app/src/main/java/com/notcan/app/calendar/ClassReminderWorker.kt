package com.notcan.app.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.notcan.app.R

class ClassReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val subject = inputData.getString(KEY_SUBJECT) ?: return Result.failure()
        val time = inputData.getString(KEY_TIME) ?: ""
        createChannel()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            inputData.getInt(KEY_NOTIFICATION_ID, subject.hashCode()),
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Mañana tienes $subject")
                .setContentText(if (time.isBlank()) "Revisa tu horario en NotCan" else "Clase a las $time")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
        return Result.success()
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de clases",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avisos de NotCan antes de las clases programadas" }
        )
    }

    companion object {
        const val KEY_SUBJECT = "subject"
        const val KEY_TIME = "time"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val CHANNEL_ID = "notcan_class_reminders"
    }
}
