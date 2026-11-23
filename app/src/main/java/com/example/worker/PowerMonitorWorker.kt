package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.PowerScopeApp
import java.util.concurrent.TimeUnit

class PowerMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as PowerScopeApp
            app.powerRepository.captureAndPersistSnapshot()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "powerscope_periodic_monitor"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PowerMonitorWorker>(
                15L, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateSample(context: Context) {
            val request = OneTimeWorkRequestBuilder<PowerMonitorWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
