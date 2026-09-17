package com.flx_apps.digitaldetox.util

import android.content.Context
import android.os.Build

/**
 * Android 13 puts the switches for special access (usage access, accessibility) behind an extra
 * confirmation for apps that did not come from an app store, and hardened ROMs apply it to more
 * installs than stock does. The switch is simply inert until the user has been through
 * "Allow restricted settings" in the app's own settings page.
 *
 * There is no API that answers "is this app restricted right now", and the switch gives no feedback
 * when it is, so the user is left concluding that the app is broken. Telling them where the
 * confirmation lives is the whole of what can be done about it.
 */
object RestrictedSettingsUtil {
    /**
     * Installers whose apps the system exempts, so that a hint there would only be noise.
     */
    private val TrustedInstallers = setOf("com.android.vending", "com.google.android.feedback")

    /**
     * Whether a greyed-out special-access switch is plausible on this device and this install.
     * Deliberately a guess on the generous side: the cost of being wrong is one sentence of extra
     * explanation, while the cost of staying quiet is a permission the user cannot grant and no
     * indication why.
     */
    fun mayBlockSpecialAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val installer = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
        return installer !in TrustedInstallers
    }
}
