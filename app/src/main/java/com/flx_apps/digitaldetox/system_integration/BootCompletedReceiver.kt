package com.flx_apps.digitaldetox.system_integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flx_apps.digitaldetox.workers.ServiceReliabilityScheduler
import timber.log.Timber

/**
 * Re-arms the reliability watchdog after a reboot or an app update. The accessibility service
 * itself is re-bound by the OS automatically as long as it stays enabled in secure settings, but
 * the WorkManager watchdog schedule and an immediate health check are (re-)established here so a
 * service that fails to come back is noticed and the user nudged to re-enable it.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.i("BootCompletedReceiver: ${intent.action} -> re-arming watchdog")
                ServiceReliabilityScheduler.schedule(context)
                ServiceReliabilityScheduler.runCheckNow(context)
            }
        }
    }
}
