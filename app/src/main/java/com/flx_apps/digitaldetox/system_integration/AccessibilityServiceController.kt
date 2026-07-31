package com.flx_apps.digitaldetox.system_integration

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.flx_apps.digitaldetox.DetoxDroidApplication

/**
 * Programmatically enables/disables the [DetoxDroidAccessibilityService] by editing the secure
 * setting that lists enabled accessibility services. This requires the WRITE_SECURE_SETTINGS
 * permission — without it, [Settings.Secure.putString] throws and callers must fall back to
 * sending the user to the accessibility system settings.
 */
object AccessibilityServiceController {
    /**
     * The component name of the accessibility service, as used in the secure settings list.
     */
    val AccessibilityServiceComponent =
        DetoxDroidApplication::class.java.`package`!!.name + "/" + DetoxDroidAccessibilityService::class.java.name

    /**
     * The currently enabled accessibility services as a clean component list (the OS stores them
     * as a `:`-separated string).
     */
    private fun enabledAccessibilityServices(context: Context): List<String> {
        return Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').filter { it.isNotBlank() }
    }

    /**
     * Whether the accessibility service is currently listed as enabled in the system's secure
     * settings. This doubles as the persisted "the user wants DetoxDroid running" signal: it is set
     * by [activate], cleared by [deactivate], survives process death and reboot, and an OEM killing
     * the process does not clear it. Reading it needs no permission.
     */
    fun isEnabledInSettings(context: Context): Boolean =
        enabledAccessibilityServices(context).contains(AccessibilityServiceComponent)

    /**
     * Activates the accessibility service. This is done by adding the service to the list of
     * enabled accessibility services and starting the service. The service is then triggered
     * manually once to make sure it is running.
     * @see DetoxDroidAccessibilityService
     */
    fun activate(context: Context): Boolean {
        val services = enabledAccessibilityServices(context)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            (services + AccessibilityServiceComponent).distinct().joinToString(":")
        )
        Settings.Secure.putString(
            context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1"
        )
        return context.startService(
            Intent(context, DetoxDroidAccessibilityService::class.java)
        ) != null
    }

    /**
     * Disables the accessibility service.
     * @see DetoxDroidAccessibilityService
     */
    fun deactivate(context: Context): Boolean {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            enabledAccessibilityServices(context).filterNot { it == AccessibilityServiceComponent }
                .joinToString(":")
        )
        return context.stopService(
            Intent(context, DetoxDroidAccessibilityService::class.java)
        )
    }
}
