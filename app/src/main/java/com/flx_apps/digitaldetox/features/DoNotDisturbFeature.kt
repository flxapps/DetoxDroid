package com.flx_apps.digitaldetox.features

import android.app.NotificationManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.ui.screens.feature.do_not_disturb.DoNotDisturbFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.util.NavigationUtil

val DoNotDisturbFeatureId = Feature.createId(DoNotDisturbFeature::class.java)

/**
 * This feature can enable the zen mode (do not disturb mode) on the device depending on the
 * schedule. It is enabled frequently, as the user might have disabled it manually.
 */
object DoNotDisturbFeature : Feature(), OnAppOpenedSubscriptionFeature, NeedsPermissionsFeature,
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(DoNotDisturbFeatureId), LockableFeature,
    PausableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_doNotDisturb,
        subtitle = R.string.feature_doNotDisturb_subtitle,
        description = R.string.feature_doNotDisturb_description,
    )
    override val iconRes: Int = R.drawable.ic_do_not_disturb
    override val settingsContent: @Composable () -> Unit = { DoNotDisturbFeatureSettingsSection() }

    /**
     * The interruption filter that was active before the feature enabled zen mode, so [onPause]
     * can restore exactly what the user had (e.g. "alarms only" instead of blindly "all").
     */
    private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL

    /**
     * On start, we save the current interruption filter and then enable the zen mode.
     */
    override fun onStart(context: Context) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        previousInterruptionFilter = notificationManager.currentInterruptionFilter.takeIf {
            it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } ?: NotificationManager.INTERRUPTION_FILTER_ALL
        setZenMode(context, enabled = true, forceSetting = true)
    }

    /**
     * On pause, we restore the interruption filter that was active before the feature started.
     */
    override fun onPause(context: Context) {
        setZenMode(context, enabled = false, forceSetting = true)
    }

    /**
     * We use [onAppOpened] to enable the zen mode frequently, as the user might have disabled it
     * manually.
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        setZenMode(context, true)
    }

    /**
     * Sets the zen mode to the given value. Disabling restores the filter saved in [onStart].
     */
    @JvmStatic
    fun setZenMode(
        context: Context, enabled: Boolean, forceSetting: Boolean = false
    ): Boolean {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Query the real system state to avoid stale cache drift
        val actuallyEnabled =
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        if (actuallyEnabled == enabled && !forceSetting) {
            return false
        }

        // we don't need to check for permissions (again), as we assume that they are already granted at this point
        // as the feature has been activated before
        notificationManager.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else previousInterruptionFilter
        )
        return true
    }

    /**
     * Checks whether the user has granted the app the permission to change the zen mode.
     */
    override fun hasPermissions(context: Context): Boolean {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    /**
     * Opens the system settings to grant the permission to change the zen mode.
     */
    override fun requestPermissions(context: Context, navViewModel: NavViewModel) {
        NavigationUtil.openDoNotDisturbSystemSettings(context)
    }
}