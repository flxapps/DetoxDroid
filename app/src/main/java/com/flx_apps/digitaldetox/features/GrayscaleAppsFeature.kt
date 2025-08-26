package com.flx_apps.digitaldetox.features

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.NeedsWriteSecureSettingsPermission
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.ScreenTimeTrackingFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature.eventuallyIncreaseUsedUpScreenTime
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature.onAppOpened
import com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps.GrayscaleAppsFeatureSettingsSection
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil

const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
const val EXTRA_DIM = "reduce_bright_colors_activated"

val GrayscaleAppsFeatureId = Feature.createId(GrayscaleAppsFeature::class.java)

/**
 * The grayscale feature can be used to turn the screen grayscale depending on the schedule and
 * which app is currently in the foreground.
 */
object GrayscaleAppsFeature : Feature(), OnAppOpenedSubscriptionFeature,
    OnScreenTurnedOffSubscriptionFeature,
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(GrayscaleAppsFeatureId),
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(GrayscaleAppsFeatureId),
    ScreenTimeTrackingFeature by ScreenTimeTrackingFeature.Impl(GrayscaleAppsFeatureId),
    NeedsPermissionsFeature by NeedsWriteSecureSettingsPermission() {
    override val texts: FeatureTexts = FeatureTexts(
        R.string.feature_grayscale,
        R.string.feature_grayscale_subtitle,
        R.string.feature_grayscale_description,
    )
    override val iconRes: Int = R.drawable.ic_contrast
    override val settingsContent: @Composable () -> Unit = { GrayscaleAppsFeatureSettingsSection() }

    /**
     * Represents, whether the grayscale filter is currently active.
     * We use this variable in order to avoid unnecessary calls to the system settings (i.e. only
     * call the system settings when the grayscale filter should be turned on or off).
     */
    private var isCurrentlyGrayscale: Boolean = false

    /**
     * We use this for people who use a color filter for color blindness. If the user has a color
     * filter enabled, we want to save the current filter and restore it when the grayscale filter
     * is turned off.
     * -1 means that the user does not have a color filter enabled.
     */
    private var defaultDaltonizer: Int = -1

    /**
     * Whether the extra dim filter should be turned on when the grayscale filter is active.
     */
    var extraDim: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_extraDim"), true
    )

    /**
     * Whether the grayscale filter should be ignored when the current app is not in full screen mode.
     * This is the case for example when the keyboard, sound controls or the notification bar are
     * shown.
     */
    var ignoreNonFullScreenApps: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_ignoreNonFullScreenApps"), true
    )

    /**
     * The allowed daily color screen time in milliseconds. Once this limit is reached, the grayscale
     * filter will be turned on and will stay on until the next day.
     */
    var allowedDailyColorScreenTime: Long by DataStoreProperty(
        longPreferencesKey("${id}_allowedDailyColorScreenTime"), 0L
    )

    /**
     * On start, we trigger [onAppOpened] once to turn the grayscale filter on or off depending on
     * the current app.
     */
    override fun onStart(context: Context) {
        val accessibilityEvent = AccessibilityEventUtil.createEvent()
        onAppOpened(context, accessibilityEvent.packageName.toString(), accessibilityEvent)
    }

    /**
     * On a pause, turn the grayscale filter off.
     */
    override fun onPause(context: Context) {
        setGrayscale(context, false)
    }

    /**
     * If an app is opened, turn the grayscale filter on or off depending on the app that is
     * currently in the foreground.
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        if (ignoreNonFullScreenApps && !accessibilityEvent.isFullScreen) {
            // we are not in full screen mode, so we do not want to interfere with the app
            return
        }
        val exceptionsContainApp = appExceptions.contains(packageName)
        // we want to turn the grayscale filter on if:
        // - the allowed screen time is exceeded and
        //   - either the app is not in the exceptions list and the exceptions list is a "not-list",
        //   - or if the app is in the exceptions list and the exceptions list is an "only-list"
        val shouldBeGrayscale =
            (!exceptionsContainApp && appExceptionListType == AppExceptionListType.NOT_LIST) || (exceptionsContainApp && appExceptionListType == AppExceptionListType.ONLY_LIST)

        if (!shouldBeGrayscale) {
            // the grayscale filter should not be turned on, so we increase the used up screen time
            eventuallyIncreaseUsedUpScreenTime()
        } else {
            eventuallyStartTracking()
            if (allowedDailyColorScreenTime > 0 && usedUpScreenTime <= allowedDailyColorScreenTime) {
                // the screen time has not been used up yet, so we do not need to turn the grayscale
                // filter on
                return
            }
        }

        if (shouldBeGrayscale != isCurrentlyGrayscale) {
            // only call the system settings if the grayscale filter state should be changed
            setGrayscale(context, shouldBeGrayscale)
            isCurrentlyGrayscale = shouldBeGrayscale
        }
    }

    /**
     * When the screen is turned off, the used up screen time is increased by the time since the
     * last grayscale app was opened.
     * @see eventuallyIncreaseUsedUpScreenTime
     */
    override fun onScreenTurnedOff(context: Context?) {
        eventuallyIncreaseUsedUpScreenTime()
    }

    /**
     * Function to turn the grayscale filter on or off.
     * The grayscale filter is a system-wide setting. In order for it to work, the app needs to
     * have the WRITE_SECURE_SETTINGS permission. This can be granted by running
     * `adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS`.
     * @param context The context.
     * @param grayscale Whether the grayscale filter should be turned on.
     * @return Whether the operation was successful.
     */
    private fun setGrayscale(
        context: Context, grayscale: Boolean
    ): Boolean {
        if (grayscale) {
            // save the current color filter
            defaultDaltonizer = Settings.Secure.getInt(
                context.contentResolver, DISPLAY_DALTONIZER, -1
            )
        }

        val contentResolver = context.contentResolver

        if (grayscale) {
            // save the current color filter
            val isDaltonizerEnabled = Settings.Secure.getInt(
                contentResolver, DISPLAY_DALTONIZER_ENABLED, -1
            ) == 1
            defaultDaltonizer = if (isDaltonizerEnabled) Settings.Secure.getInt(
                contentResolver, DISPLAY_DALTONIZER, -1
            ) else -1
        }

        // enable/disable grayscale
        val result1 = Settings.Secure.putInt(
            contentResolver,
            DISPLAY_DALTONIZER_ENABLED,
            if (grayscale || defaultDaltonizer != -1) 1 else 0
        )
        val result2 = Settings.Secure.putInt(
            contentResolver, DISPLAY_DALTONIZER, if (grayscale) 0 else defaultDaltonizer
        )
        var result3 = true
        if (extraDim) {
            // eventually enable/disable extra dim mode
            result3 = setExtraDim(context, grayscale)
        }
        if (result1 && result2 && result3) {
            isCurrentlyGrayscale = grayscale
            return true // everything went fine
        }
        return false // something went wrong
    }

    /**
     * Function to turn the extra dim filter on or off.
     * The extra dim filter is a system-wide setting. In order for it to work, the app needs to
     * have the WRITE_SECURE_SETTINGS permission. This can be granted by running
     * `adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS`.
     * @param context The context.
     * @param extraDim Whether the extra dim filter should be turned on.
     * @return Whether the operation was successful.
     */
    private fun setExtraDim(
        context: Context, extraDim: Boolean
    ): Boolean {
        val contentResolver = context.contentResolver
        return Settings.Secure.putInt(
            contentResolver, EXTRA_DIM, if (extraDim) 1 else 0
        )
    }
}
