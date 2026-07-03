package com.flx_apps.digitaldetox.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Best-effort force-stopping of another app, e.g. so that a doom-scrolling app disappears from
 * the recent-apps list after the user chose to leave it. Everything here is strictly
 * fire-and-forget: no method throws, and if no mechanism is available the call is a no-op.
 *
 * Mechanisms, in order:
 * 1. Shizuku shell command `am force-stop` — actually kills the app and removes its task from
 *    recents. Only attempted when Shizuku permission is already granted (no dialog).
 * 2. [ActivityManager.killBackgroundProcesses] — only kills background processes and does not
 *    touch recents; a no-op for other packages since Android 14, but harmless.
 */
object ForceStopUtil {
    /** Valid Android package name — also guards the value interpolated into the shell command. */
    private val PACKAGE_NAME_REGEX =
        Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Tries to force-stop [packageName] in the background. Safe to call from any thread;
     * never throws and never stops DetoxDroid itself.
     */
    fun tryForceStop(context: Context, packageName: String) {
        val appContext = context.applicationContext
        if (!PACKAGE_NAME_REGEX.matches(packageName) || packageName == appContext.packageName) {
            Timber.d("Skipping force-stop for %s", packageName)
            return
        }
        scope.launch {
            runCatching {
                if (ShizukuUtils.canExecuteCommandsSilently()) {
                    ShizukuUtils.executeCommand("am force-stop $packageName") { success, output ->
                        Timber.i("Shizuku force-stop of %s: success=%b %s", packageName, success, output)
                        if (!success) killBackgroundProcesses(appContext, packageName)
                    }
                } else {
                    killBackgroundProcesses(appContext, packageName)
                }
            }.onFailure { Timber.w(it, "Force-stop of %s failed", packageName) }
        }
    }

    private fun killBackgroundProcesses(context: Context, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // silently ignored for other packages since Android 14 — don't bother
            return
        }
        runCatching {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            Timber.i("Killed background processes of %s", packageName)
        }.onFailure { Timber.w(it, "killBackgroundProcesses(%s) failed", packageName) }
    }
}
