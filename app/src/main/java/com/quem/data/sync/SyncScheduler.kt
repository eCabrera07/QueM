package com.quem.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

object SyncScheduler {
    val periodicInterval: Duration = Duration.ofMinutes(15)

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            periodicInterval.toMinutes(),
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_SYNC_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private const val PERIODIC_SYNC_NAME = "quem-periodic-sync"
    private const val ONE_TIME_SYNC_NAME  = "quem-manual-sync"
}
