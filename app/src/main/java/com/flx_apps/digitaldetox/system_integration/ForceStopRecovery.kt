package com.flx_apps.digitaldetox.system_integration

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * Tells the system switching the accessibility service off after a force stop apart from the user
 * switching it off, and undoes the former where it can.
 *
 * A force stop takes every accessibility service of the stopped package off the enabled list,
 * cancels its jobs and keeps broadcasts away from it until something launches it again. Several
 * vendors force-stop apps when the recent apps are cleared or their battery manager steps in, so to
 * the user DetoxDroid simply switched itself off. The secure setting reads the same either way, so
 * the service keeps its own record of running: set when it connects, cleared in
 * [DetoxDroidAccessibilityService.onDestroy]. Every deliberate way of switching it off (the in-app
 * stop, the system toggle, uninstalling from the app) ends there. A force stop kills the process
 * before anything is unbound, so onDestroy never runs and the record outlives the setting.
 */
object ForceStopRecovery {
    private const val PREFS = "service_reliability_prefs"
    private const val KEY_SERVICE_RUNNING = "service_running"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Records whether the service is running. Also cleared when the user has read why it is off and
     * chooses to leave it that way.
     */
    fun recordRunning(context: Context, running: Boolean) =
        prefs(context).edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()

    /**
     * Whether the service was running when the process died and has been taken off the enabled
     * list since, which is what a force stop does.
     */
    fun wasForceStopped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_RUNNING, false) &&
            !AccessibilityServiceController.isEnabledInSettings(context)

    /**
     * Runs on every process start, the first code of DetoxDroid's that runs after a force stop.
     * Holding WRITE_SECURE_SETTINGS, it puts the service straight back, unless this is a build that
     * asks first and the user has not agreed yet ([AccessibilityServiceController.activate] holds
     * back then). Otherwise the home screen explains what happened.
     */
    fun onAppStart(context: Context) {
        logPreviousExit(context)
        if (!wasForceStopped(context)) return
        Timber.w("ForceStopRecovery: the accessibility service was switched off by a force stop")
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        // activate() writes the setting before it starts the service, and the system binds an
        // enabled service on its own, so a start refused in the background changes nothing
        runCatching { AccessibilityServiceController.activate(context) }
        Timber.i(
            "ForceStopRecovery: re-enabled=%b",
            AccessibilityServiceController.isEnabledInSettings(context)
        )
    }

    /**
     * Logs how the previous main process ended. The reason tells a force stop (USER REQUESTED)
     * from a kill under memory pressure, and the description often names what asked for it.
     */
    private fun logPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(null, 0, 0)
                .firstOrNull { it.processName == context.packageName }
                ?.let { Timber.i("Previous process: %s", it) }
        }.onFailure { Timber.w(it, "Could not read the previous process exit") }
    }
}
