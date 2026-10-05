package com.notcan.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SupabaseSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = SupabaseSyncManager(applicationContext)
        if (!manager.isSignedIn()) return Result.success()

        return runCatching {
            manager.syncNow()
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC = "notcan-account-periodic-sync"
        private const val UNIQUE_IMMEDIATE = "notcan-account-immediate-sync"

        private val connected = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SupabaseSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
                .setConstraints(connected)
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
