package com.flx_apps.digitaldetox.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.flx_apps.digitaldetox.R
import com.topjohnwu.superuser.Shell
import timber.log.Timber

/**
 * Whether DetoxDroid holds WRITE_SECURE_SETTINGS. Only adb, root or Shizuku can grant it, see
 * [PrivilegedShell.grantWriteSecureSettings].
 */
fun Context.hasWriteSecureSettings(): Boolean =
    checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

/** The two ways DetoxDroid can run a privileged shell command by itself: root, or Shizuku. */
object PrivilegedShell {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var rootAvailable: Boolean? = null

    /**
     * Whether there is a root shell. The first call spawns `su`, which can block until the root
     * manager's prompt times out, so not on the main thread. The answer is kept: libsu keeps the
     * shell it got for the life of the process anyway, so asking again would not change it.
     */
    fun isRootAvailable(): Boolean = rootAvailable
        ?: runCatching { Shell.getShell().isRoot }.getOrDefault(false).also { rootAvailable = it }

    /** Runs [command] as root. [onDone] learns whether it worked, on the main thread. */
    fun runAsRoot(command: String, onDone: (Boolean) -> Unit) {
        runCatching { Shell.cmd(command).submit { result -> onDone(result.isSuccess) } }
            .onFailure {
                Timber.e(it, "Failed to run root command")
                mainHandler.post { onDone(false) }
            }
    }

    /**
     * Runs [command] through Shizuku, which may first ask the user to let DetoxDroid use it.
     * [onDone] learns whether it worked, on the main thread.
     */
    fun runViaShizuku(command: String, onDone: (Boolean) -> Unit) {
        ShizukuUtils.executeCommand(command) { success, _ -> onDone(success) }
    }

    /**
     * Grants DetoxDroid WRITE_SECURE_SETTINGS as root where there is root, through Shizuku
     * otherwise. Probes for root first: not on the main thread.
     */
    fun grantWriteSecureSettings(context: Context, onDone: (Boolean) -> Unit) {
        val command = context.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission)
        if (isRootAvailable()) runAsRoot(command, onDone) else runViaShizuku(command, onDone)
    }
}
