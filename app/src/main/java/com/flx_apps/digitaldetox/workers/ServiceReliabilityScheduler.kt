package com.flx_apps.digitaldetox.workers

import android.content.Context
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
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
     * Enqueues the periodic health check, or cancels it again on a device where DetoxDroid was
     * never started. There is nothing to watch over until the user switches it on once, and a
     * quarter-hourly wake-up on a phone whose owner has moved on is pure cost.
     *
     * WorkManager persists the schedule across process death and reboots;
     * [ExistingPeriodicWorkPolicy.KEEP] makes repeated calls idempotent.
     */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!AccessibilityServiceController.isEnabledInSettings(context)) {
            workManager.cancelUniqueWork(ServiceWatchdogWorker.WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
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
