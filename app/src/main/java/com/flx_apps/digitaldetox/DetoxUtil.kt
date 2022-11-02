package com.flx_apps.digitaldetox

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.flx_apps.digitaldetox.DetoxUtil.ZEN_MODE
import com.flx_apps.digitaldetox.DetoxUtil.isZenModeEnabled
import com.flx_apps.digitaldetox.prefs.Prefs_
import java.util.concurrent.TimeUnit


/**
 * Creation Date: 11/9/20
 * @author felix
 */
object DetoxUtil {
    const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
    const val EXTRA_DIM = "reduce_bright_colors_activated"

    const val ZEN_MODE = "zen_mode"
    const val ZEN_MODE_OFF = 0
    const val ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1
    const val ZEN_MODE_NO_INTERRUPTIONS = 2

    @JvmStatic
    var isActive: Boolean = false

    @JvmStatic
    var isGrayscale: Boolean = false

    @JvmStatic
    var isZenModeEnabled: Boolean = false

    @JvmStatic
    var isAppsDeactivated: Boolean = false

    /**
     * Convenience function to (de-)activate all DetoxDroid modules
     */
    @JvmStatic
    fun setActive(
        context: Context,
        active: Boolean,
        grayscale: Boolean = active,
        zenMode: Boolean = active,
        appsDeactivated: Boolean = active
    ) {
        setGrayscale(context, grayscale)
        setZenMode(context, zenMode)
        setAppsDeactivated(context, appsDeactivated)
        isActive = active
    }

    @JvmStatic
    fun setGrayscale(
        context: Context,
        grayscale: Boolean,
        forceSetting: Boolean = false
    ): Boolean {
        if (
            grayscale == isGrayscale ||
            context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED ||
            (!forceSetting && !Prefs_(context).grayscaleEnabled().get())
        ) return false

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
        val result3 = setExtraDim(context, grayscale)
        isGrayscale = grayscale
        return result1 && result2 && result3
    }

    @JvmStatic
    fun setExtraDim(
        context: Context,
        grayscale: Boolean
    ): Boolean {
        if (context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val contentResolver = context.contentResolver
        return Settings.Secure.putInt(
            contentResolver,
            EXTRA_DIM,
            if (grayscale) 1 else 0
        )
    }

    @JvmStatic
    fun setZenMode(
        context: Context,
        enabled: Boolean,
        forceSetting: Boolean = false
    ): Boolean {
        if (
            isZenModeEnabled == enabled ||
            (!forceSetting && !Prefs_(context).zenModeDefaultEnabled().get())
        ) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Fallback for Android 5, probably not working properly though (Settings don't seem to be persisted)
            return Settings.Global.putInt(
                context.contentResolver,
                ZEN_MODE,
                if (enabled) ZEN_MODE_IMPORTANT_INTERRUPTIONS else ZEN_MODE_OFF
            )
        }

        val notificationManager: NotificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                var policy = NotificationManager.Policy(
//                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,
//                    NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
//                    NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
//                    NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON and NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF
//                )
//                notificationManager.notificationPolicy = policy
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                var zenPolicy = ZenPolicy.Builder()
//                    .hideAllVisualEffects()
//                    .allowAlarms(true)
//                    .allowMedia(true)
//                    .build()
//            }
            notificationManager.setInterruptionFilter(if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            isZenModeEnabled = enabled
            return true
        }
        return false
    }

    @JvmStatic
    fun togglePause(
        context: Context
    ): Boolean {
        val now = System.currentTimeMillis()
        val prefs = Prefs_(context)
        var isPausing = (now < prefs.pauseUntil().get())
        if (!isPausing && now < prefs.nextPauseAllowedAt().get()) {
            // we are currently not in a pause, and a pause is not allowed right now either
            Toast.makeText(
                context,
                context.getString(
                    R.string.app_quickSettingsTile_noPause,
                    TimeUnit.MILLISECONDS.toMinutes(prefs.nextPauseAllowedAt().get() - now) + 1
                ),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        isPausing = !isPausing // new pause state: inversion of "are we currently pausing?"
        prefs.edit().pauseUntil().put(if (isPausing) now + TimeUnit.MINUTES.toMillis(prefs.pauseDuration().get().toLong()) else -1).apply()
        setActive(context, !isPausing)
        if (isPausing) {
            // a pause was made, let's show a hint to the user
            Toast.makeText(
                context,
                context.getString(
                    R.string.app_quickSettingsTile_paused,
                    prefs.pauseDuration().get()
                ),
                Toast.LENGTH_SHORT
            ).show()
            prefs.edit().nextPauseAllowedAt()
                .put(prefs.pauseUntil().get() + TimeUnit.MINUTES.toMillis(prefs.timeBetweenPauses().get().toLong()))
                .apply()
        }
        else {
            // a pause was interrupted
            prefs.edit().nextPauseAllowedAt()
                .put(now + TimeUnit.MINUTES.toMillis(prefs.timeBetweenPauses().get().toLong()))
                .apply()
        }
        return isPausing
    }

    @JvmStatic
    fun setAppsDeactivated(context: Context, deactivated: Boolean, forceSetting: Boolean = false) {
        val prefs = Prefs_(context)
        if (
            isAppsDeactivated == deactivated ||
            !DetoxDroidDeviceAdminReceiver.isGranted(context) ||
            (!prefs.deactivateAppsEnabled().get() && !forceSetting)
        ) return
        prefs.deactivatedApps().get().forEach {
            setApplicationHidden(context, it, deactivated)
        }
        isAppsDeactivated = deactivated
    }

    @JvmStatic
    fun setApplicationHidden(context: Context, pckg: String, hidden: Boolean) {
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .setApplicationHidden(
                ComponentName(context, DetoxDroidDeviceAdminReceiver::class.java),
                pckg,
                hidden
            )
    }
}