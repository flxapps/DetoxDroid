package com.flx_apps.digitaldetox.system_integration

import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.util.ShizukuUtils
import timber.log.Timber

/**
 * Suspends and releases apps through the package manager's shell interface, over Shizuku.
 *
 * A suspended app is greyed out in the launcher and cannot be started: the system shows a dialog
 * instead. Unlike deactivating an app it needs no device owner, which most phones cannot give,
 * and unlike a blocking overlay the user never gets into the app at all.
 *
 * What makes this dangerous is that a suspension outlives everything that created it. It survives
 * our process, a reboot, and the Shizuku session that carried the command, and nothing in the
 * system ever lifts it on its own. So the record of what is suspended is persisted before the
 * command runs, never dropped because a command failed, and every path that ends the enforcement
 * tries to release; the periodic service check tries again, for the case where Shizuku was gone
 * at the time. Releasing an app that is not suspended does nothing, so trying too often is free
 * and trying too rarely is not.
 */
object AppSuspender {
    /**
     * The packages DetoxDroid has suspended and not released again. Persisted, because after a
     * process death this is the only record of what has to be handed back.
     */
    private var suspendedApps: Set<String> by DataStoreProperty(
        stringSetPreferencesKey("AppSuspender_suspendedApps"), emptySet()
    )

    val hasSuspendedApps: Boolean get() = suspendedApps.isNotEmpty()

    /** Whether a command would run right now without putting a permission dialog in front of it. */
    val isAvailable: Boolean get() = ShizukuUtils.canExecuteCommandsSilently()

    /**
     * Suspends [packageNames], minus the ones already suspended and DetoxDroid itself.
     */
    fun suspend(packageNames: Collection<String>) {
        val targets = packageNames
            .filter { ShizukuUtils.isSafePackageName(it) }
            .filterNot { it == DetoxDroidApplication.appContext.packageName }
            .minus(suspendedApps)
        if (targets.isEmpty()) return
        if (!isAvailable) {
            Timber.w("Cannot suspend %s, Shizuku is not available", targets)
            return
        }
        // Recorded before the command runs and kept even if it fails: a suspension we do not know
        // about is one nothing will ever undo, while a release of an app that was never suspended
        // costs nothing.
        suspendedApps = suspendedApps + targets
        run("pm suspend", targets) {}
    }

    /**
     * Releases everything this object suspended. A failure leaves the record in place, so the next
     * attempt covers it.
     */
    fun releaseAll() {
        val targets = suspendedApps.toList()
        if (targets.isEmpty()) return
        if (!isAvailable) {
            Timber.w("Cannot release %s yet, Shizuku is not available", targets)
            return
        }
        run("pm unsuspend", targets) { success ->
            if (success) suspendedApps = suspendedApps - targets.toSet()
        }
    }

    private fun run(command: String, packages: List<String>, onResult: (Boolean) -> Unit) {
        val script = packages.joinToString("\n") { "$command $it" }
        ShizukuUtils.executeCommand(script) { success, output ->
            Timber.i("%s %s: success=%b %s", command, packages, success, output)
            onResult(success)
        }
    }
}
