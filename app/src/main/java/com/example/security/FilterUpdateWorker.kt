package com.example.security

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.telemetry.PerformanceMonitor
import java.util.concurrent.TimeUnit

/**
 * Battery-aware background filter list updater.
 * Runs periodically ONLY on unmetered Wi-Fi while charging and battery is not low,
 * validating versions/SHA-256 checksums and compiling rules once into binary cache.
 */
class FilterUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ContentBlockerEngine.initialize(applicationContext)
        val outcome = ContentBlockerEngine.syncEnabledSubscriptions(applicationContext)
        return outcome.fold(
            onSuccess = { changed ->
                PerformanceMonitor.logDiagnostic("FilterUpdateWorker", "completed changed=$changed")
                Result.success()
            },
            onFailure = { err ->
                PerformanceMonitor.logDiagnostic("FilterUpdateWorker", "failed=${err.message}")
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "navula_periodic_filter_update"
        const val TAG_FILTER_SYNC = "navula_filter_sync"

        fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        fun schedulePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(7, TimeUnit.DAYS)
                    .setConstraints(defaultConstraints())
                    .addTag(TAG_FILTER_SYNC)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
            }
        }
    }
}
