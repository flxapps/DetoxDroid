package com.flx_apps.digitaldetox

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityManager


/**
 * Creation Date: 11/9/20
 * @author felix
 */
object AccessibilityUtil {
    private val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"

    @JvmStatic
    fun isAccessibilityServiceEnabled(
        context: Context,
        service: Class<out AccessibilityService?>
    ): Boolean {
        val am =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name
            ) return true
        }
        return false
    }

    @JvmStatic
    fun setGrayscale(
        context: Context,
        grayscale: Boolean
    ): Boolean {
        val contentResolver = context.contentResolver
        val result1 = Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, if (grayscale) 1 else 0)
        val result2 = Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER, if (grayscale) 0 else -1)
        return result1 && result2;
    }
}