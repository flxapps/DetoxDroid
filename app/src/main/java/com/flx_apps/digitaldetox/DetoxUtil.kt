package com.flx_apps.digitaldetox

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Build
import android.provider.Settings


/**
 * Creation Date: 11/9/20
 * @author felix
 */
object DetoxUtil {
    const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"

    const val ZEN_MODE = "zen_mode"
    const val ZEN_MODE_OFF = 0
    const val ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1
    const val ZEN_MODE_NO_INTERRUPTIONS = 2

//    @JvmStatic
//    fun isAccessibilityServiceEnabled(
//        context: Context,
//        service: Class<out AccessibilityService?>
//    ): Boolean {
//        val am =
//            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
//        val enabledServices =
//            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
//        for (enabledService in enabledServices) {
//            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
//            if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name
//            ) return true
//        }
//        return false
//    }

    @JvmStatic
    fun setGrayscale(
        context: Context,
        grayscale: Boolean
    ): Boolean {
        val contentResolver = context.contentResolver
        val result1 = Settings.Secure.putInt(
            contentResolver,
            DISPLAY_DALTONIZER_ENABLED,
            if (grayscale) 1 else 0
        )
        val result2 = Settings.Secure.putInt(
            contentResolver,
            DISPLAY_DALTONIZER,
            if (grayscale) 0 else -1
        )
        return result1 && result2;
    }

    @JvmStatic
    fun setZenMode(
        context: Context,
        enabled: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return Settings.Global.putInt(
                context.contentResolver,
                ZEN_MODE,
                if (enabled) ZEN_MODE_IMPORTANT_INTERRUPTIONS else ZEN_MODE_OFF
            )
        }

        val notificationManager: NotificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        }
        return false
    }
}