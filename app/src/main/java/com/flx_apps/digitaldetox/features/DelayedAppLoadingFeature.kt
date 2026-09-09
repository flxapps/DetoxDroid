package com.flx_apps.digitaldetox.features

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.delayed_loading.DelayedAppLoadingOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.delayed_loading.DelayedAppLoadingSettingsSection
import com.flx_apps.digitaldetox.util.DailyAppCounter
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

val DelayedAppLoadingFeatureId = Feature.createId(DelayedAppLoadingFeature::class.java)

/**
 * The [DelayedAppLoadingFeature] introduces a mindful delay (countdown timer) before
 * launching configured apps to break impulsive/compulsive phone habits.
 */
object DelayedAppLoadingFeature : Feature(), OnAppOpenedSubscriptionFeature,
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(DelayedAppLoadingFeatureId),
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(DelayedAppLoadingFeatureId),
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature(),
    LockableFeature, PausableFeature {

    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_delayedLoading,
        subtitle = R.string.feature_delayedLoading_subtitle,
        description = R.string.feature_delayedLoading_description,
    )
    override val iconRes: Int = R.drawable.ic_pause

    override val settingsContent: @Composable () -> Unit = {
        DelayedAppLoadingSettingsSection()
    }

    override val listTypes: List<AppExceptionListType>
        get() = listOf(AppExceptionListType.ONLY_LIST)

    /**
     * Delay in seconds before the app is loaded (default: 10s).
     */
    var delaySeconds: Int by DataStoreProperty(
        intPreferencesKey("${id}_delaySeconds"), 10
    )

    /**
     * Temporary unlock grace period in seconds so switching back and forth does not re-trigger
     * the delay immediately (default: 120s / 2 minutes).
     */
    var gracePeriodSeconds: Int by DataStoreProperty(
        intPreferencesKey("${id}_gracePeriodSeconds"), 120
    )

    val delayCounter = DailyAppCounter()

    val targetApps: Set<String>
        get() = appExceptions

    // Keeps track of temporarily cleared packages and their expiration timestamp (uptimeMillis)
    private val clearedSessions = ConcurrentHashMap<String, Long>()

    /**
     * Clears an app session so the user can use the app without delay until the grace period expires.
     */
    fun grantTemporaryAccess(packageName: String) {
        val expiry = SystemClock.uptimeMillis() + (gracePeriodSeconds * 1000L)
        clearedSessions[packageName] = expiry
    }

    private fun isAccessGranted(packageName: String): Boolean {
        val expiry = clearedSessions[packageName] ?: return false
        if (SystemClock.uptimeMillis() < expiry) {
            return true
        }
        clearedSessions.remove(packageName)
        return false
    }

    override fun onAppOpened(
        context: Context,
        packageName: String,
        accessibilityEvent: AccessibilityEvent
    ) {
        if (!targetApps.contains(packageName)) return
        if (isAccessGranted(packageName)) {
            Timber.d("DelayedAppLoadingFeature: Access granted within grace period for $packageName")
            return
        }

        delayCounter.increment(packageName)
        Timber.d("DelayedAppLoadingFeature: Intercepting $packageName with ${delaySeconds}s delay")

        val intent = Intent(context, DelayedAppLoadingOverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RUNNING_APP_PACKAGE_NAME, packageName)
            putExtra(DelayedAppLoadingOverlayService.EXTRA_DELAY_SECONDS, delaySeconds)
        }
        context.startService(intent)
    }

    override fun onPause(context: Context) {
        clearedSessions.clear()
    }
}
