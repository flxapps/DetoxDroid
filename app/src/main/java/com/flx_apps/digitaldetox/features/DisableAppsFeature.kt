package com.flx_apps.digitaldetox.features

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.AppDisabledOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.DisableAppsFeatureSettingsSection
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import java.time.LocalDate

/**
 * The [DisableAppsFeature] can either deactivate apps or block them.
 * For deactivating apps, the app needs to have DEVICE_ADMIN permission.
 * For blocking apps, the app needs to have SYSTEM_ALERT_WINDOW permission.
 * @see NeedsPermissionsFeature
 * @see android.Manifest.permission.SYSTEM_ALERT_WINDOW
 * @see android.Manifest.permission.BIND_DEVICE_ADMIN
 */
enum class DisableAppsMode {
    DEACTIVATE, BLOCK,
}

/**
 * This feature can disable apps. If DetoxDroid has DEVICE_ADMIN permission, it can completely
 * deactivate apps, so they are not visible in the launcher anymore. Otherwise it can only make
 * them unusable by showing a warning screen when the user tries to open them.
 */
object DisableAppsFeature : Feature(), OnAppOpenedSubscriptionFeature,
    OnScreenTurnedOffSubscriptionFeature,
    // SupportsScheduleFeature by SupportsScheduleFeature.Impl(DisableAppsFeature::class.simpleName!!),
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(
        DisableAppsFeature::class.simpleName!!
    ), NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature() {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_disableApps,
        subtitle = R.string.feature_disableApps_subtitle,
        description = R.string.feature_disableApps_description,
    )
    override val iconRes: Int = R.drawable.ic_disable_app
    override val settingsContent: @Composable () -> Unit = {
        DisableAppsFeatureSettingsSection()
    }

    /**
     * The [DisableAppsFeature] supports only the [AppExceptionListType.ONLY_LIST] type, as it is
     * only possible to configure apps that are to be disabled.
     */
    override val listTypes: List<AppExceptionListType>
        get() = listOf(
            AppExceptionListType.ONLY_LIST
        )

    /**
     * The allowed daily screen time in milliseconds. If the user has already used more screen time
     * than this value, the apps will be disabled. If the value is 0, the apps will always be
     * disabled, while the feature and DetoxDroid are active.
     */
    var allowedDailyScreenTime: Long by DataStoreProperty(
        longPreferencesKey("${id}_allowedDailyScreenTime"), 0L
    )

    /**
     * The operation mode of the feature.
     * @see DisableAppsMode
     */
    var operationMode: DisableAppsMode by DataStoreProperty(
        key = stringPreferencesKey("${id}_disableAppsMode"),
        DisableAppsMode.DEACTIVATE,
        dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(DisableAppsMode::class.java)
    )

    /**
     * The apps that are to be disabled are implemented using the [appExceptions].
     * @see SupportsAppExceptionsFeature
     */
    val disableableApps: Set<String>
        get() = appExceptions

    /**
     * The time when the last disableable app was opened.
     */
    private var disableableAppOpened = 0L

    /**
     * The time the user has already used up their daily screen time.
     */
    private var usedUpScreenTime = 0L

    /**
     * The current date. If the date changes, the [usedUpScreenTime] is reset.
     */
    private var today = LocalDate.now()

    /**
     * Whether the apps are currently deactivated.
     */
    private var isAppsDeactivated = false

    override fun onPause(context: Context) {
        if (operationMode == DisableAppsMode.DEACTIVATE) {
            // if the apps are deactivated, we need to reactivate them when DetoxDroid is paused
            setAppsDeactivated(context, false)
        }
    }

    /**
     * When an app is opened, it is checked whether the app is in the list of apps that should be
     * disabled. If it is, the used up screen time is increased by the time since the last
     * disableable app was opened.
     *
     * If the user has already used up their daily screen time, the apps are disabled.
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        if (!disableableApps.contains(packageName)) {
            eventuallyIncreaseUsedUpScreenTime()
            return
        }
        if (disableableAppOpened == 0L) {
            // remember the time the app was opened
            disableableAppOpened = System.currentTimeMillis()
        }
        if (allowedDailyScreenTime > 0L && usedUpScreenTime < allowedDailyScreenTime) {
            // the user has not used up their daily screen time yet
            return
        }
        when (operationMode) {
            DisableAppsMode.BLOCK -> {
                context.startService(Intent(context, AppDisabledOverlayService::class.java))
            }

            DisableAppsMode.DEACTIVATE -> {
                setAppsDeactivated(context, true)
            }
        }
    }

    /**
     * When the screen is turned off, the used up screen time is increased by the time since the
     * last disableable app was opened.
     * @see eventuallyIncreaseUsedUpScreenTime
     */
    override fun onScreenTurnedOff(context: Context?) {
        eventuallyIncreaseUsedUpScreenTime()
    }

    /**
     * Increases the used up screen time by the time since the last disableable app was opened.
     */
    private fun eventuallyIncreaseUsedUpScreenTime() {
        val today = LocalDate.now()
        if (today != DisableAppsFeature.today) {
            // the date has changed, so we reset the used up screen time
            usedUpScreenTime = 0L
            DisableAppsFeature.today = today
        }
        if (allowedDailyScreenTime == 0L) return
        usedUpScreenTime += System.currentTimeMillis() - disableableAppOpened
        allowedDailyScreenTime = 0L
    }

    /**
     * Sets whether the apps should be deactivated or not using the [DevicePolicyManager].
     * "Deactivated" means that the apps are not visible in the launcher anymore and cannot be
     * opened or used in any way.
     * @see DevicePolicyManager.setApplicationHidden
     */
    fun setAppsDeactivated(context: Context, deactivated: Boolean) {
        if (isAppsDeactivated == deactivated || !DetoxDroidDeviceAdminReceiver.isGranted(context)) return
        val devicePolicyManager =
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
        disableableApps.forEach {
            devicePolicyManager.setApplicationHidden(
                ComponentName(context, DetoxDroidDeviceAdminReceiver::class.java), it, deactivated
            )
        }
        isAppsDeactivated = deactivated
    }
}

