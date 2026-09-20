package com.flx_apps.digitaldetox.system_integration

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.flx_apps.digitaldetox.DetoxDroidApplication

/**
 * Programmatically enables/disables the [DetoxDroidAccessibilityService].
 *
 * Enabling means editing the secure setting that lists enabled accessibility services, which needs
 * the WRITE_SECURE_SETTINGS permission: without it [Settings.Secure.putString] throws and callers
 * must fall back to sending the user to the accessibility system settings. Disabling does not,
 * see [deactivate].
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
     *
     * A connected service can switch itself off through [AccessibilityService.disableSelf], which
     * needs no permission and clears the secure setting on its own. Without that path, stopping
     * DetoxDroid required WRITE_SECURE_SETTINGS just like starting it, so a user who never granted
     * it could turn the app on and then not off again.
     *
     * Editing the setting directly stays as the fallback for the case where the component is
     * listed as enabled but no instance is connected.
     * @see DetoxDroidAccessibilityService
     */
    fun deactivate(context: Context): Boolean {
        DetoxDroidAccessibilityService.instance?.let {
            it.disableSelf()
            return true
        }
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
