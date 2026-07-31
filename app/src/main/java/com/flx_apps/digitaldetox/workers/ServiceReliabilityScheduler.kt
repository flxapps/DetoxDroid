package com.flx_apps.digitaldetox.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Central place for (re-)scheduling the [ServiceWatchdogWorker]. Called on app start and again from
 * [com.flx_apps.digitaldetox.system_integration.BootCompletedReceiver] after a reboot / app update.
 */
object ServiceReliabilityScheduler {
    private const val CHECK_NOW_WORK = "service_watchdog_now"

    /**
     * Enqueues the periodic health check. WorkManager persists this across process death and
     * reboots; [ExistingPeriodicWorkPolicy.KEEP] makes repeated calls idempotent.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ServiceWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Runs a one-off health check now (e.g. right after boot). */
    fun runCheckNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ServiceWatchdogWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHECK_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
