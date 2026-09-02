package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.MinScreenFilterIntensity
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.ScreenFilterMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The view model for the grayscale apps feature settings tile.
 */
@HiltViewModel
class GrayscaleAppsFeatureSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    /**
     * Whether the system color filter is switched to grayscale.
     * @see GrayscaleAppsFeature.systemGrayscale
     */
    private var _systemGrayscale: MutableStateFlow<Boolean> =
        MutableStateFlow(GrayscaleAppsFeature.systemGrayscale)
    val systemGrayscale: StateFlow<Boolean> = _systemGrayscale

    /**
     * Whether the screen filter is in use, with [ScreenFilterMode.AUTO] already resolved.
     * @see GrayscaleAppsFeature.isScreenFilterEnabled
     */
    private var _screenFilterEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(GrayscaleAppsFeature.isScreenFilterEnabled(application))
    val screenFilterEnabled: StateFlow<Boolean> = _screenFilterEnabled

    /**
     * The strength of the screen filter in percent.
     * @see GrayscaleAppsFeature.screenFilterIntensity
     */
    private var _screenFilterIntensity: MutableStateFlow<Int> =
        MutableStateFlow(GrayscaleAppsFeature.screenFilterIntensity)
    val screenFilterIntensity: StateFlow<Int> = _screenFilterIntensity

    /**
     * Whether the screen filter blurs what is behind it.
     * @see GrayscaleAppsFeature.screenFilterBlur
     */
    private var _screenFilterBlur: MutableStateFlow<Boolean> =
        MutableStateFlow(GrayscaleAppsFeature.screenFilterBlur)
    val screenFilterBlur: StateFlow<Boolean> = _screenFilterBlur

    /**
     * Whether the dialog to set the screen filter strength should be shown.
     */
    private var _showScreenFilterIntensityDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showScreenFilterIntensityDialog: StateFlow<Boolean> = _showScreenFilterIntensityDialog

    /**
     * Whether the extra dim feature is activated.
     * @see GrayscaleAppsFeature.extraDim
     */
    private var _extraDimActivated: MutableStateFlow<Boolean> =
        MutableStateFlow(GrayscaleAppsFeature.extraDim)
    val extraDimActivated: StateFlow<Boolean> = _extraDimActivated

    /**
     * Whether the grayscale filter should be ignored when the current app is not in full screen mode.
     * @see GrayscaleAppsFeature.ignoreNonFullScreenApps
     */
    private var _ignoreNonFullScreenApps: MutableStateFlow<Boolean> =
        MutableStateFlow(GrayscaleAppsFeature.ignoreNonFullScreenApps)
    val ignoreNonFullScreenApps: StateFlow<Boolean> = _ignoreNonFullScreenApps

    /**
     * The allowed daily color screen time *in minutes*.
     * @see GrayscaleAppsFeature.allowedDailyColorScreenTime
     */
    private var _allowedDailyColorScreenTime: MutableStateFlow<Long> =
        MutableStateFlow(GrayscaleAppsFeature.allowedDailyColorScreenTime.milliseconds.inWholeMinutes)
    val allowedDailyColorScreenTime: StateFlow<Long> = _allowedDailyColorScreenTime

    /**
     * Whether the dialog to set the allowed daily color screen time should be shown.
     */
    private var _showAllowedDailyColorScreenTimeDialog: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val showAllowedDailyColorScreenTimeDialog: StateFlow<Boolean> =
        _showAllowedDailyColorScreenTimeDialog

    /**
     * Turns the system grayscale filter on or off. The feature itself stays activated: it can run
     * on the screen filter alone.
     */
    fun toggleSystemGrayscale(): Boolean {
        GrayscaleAppsFeature.systemGrayscale = !GrayscaleAppsFeature.systemGrayscale
        _systemGrayscale.value = GrayscaleAppsFeature.systemGrayscale
        return refreshEffects()
    }

    /**
     * Turns the screen filter on or off. Either way the choice is stored explicitly, so it sticks
     * when the WRITE_SECURE_SETTINGS permission arrives later.
     */
    fun toggleScreenFilter(): Boolean {
        val enabled = !_screenFilterEnabled.value
        GrayscaleAppsFeature.screenFilterMode =
            if (enabled) ScreenFilterMode.ON else ScreenFilterMode.OFF
        _screenFilterEnabled.value = enabled
        return refreshEffects()
    }

    /**
     * Toggles the blur part of the screen filter.
     */
    fun toggleScreenFilterBlur() {
        GrayscaleAppsFeature.screenFilterBlur = !GrayscaleAppsFeature.screenFilterBlur
        _screenFilterBlur.value = GrayscaleAppsFeature.screenFilterBlur
        refreshEffects()
    }

    /**
     * Sets the screen filter strength in percent.
     */
    fun setScreenFilterIntensity(percent: Int) {
        val value = percent.coerceIn(MinScreenFilterIntensity, 100)
        GrayscaleAppsFeature.screenFilterIntensity = value
        _screenFilterIntensity.value = value
        refreshEffects()
    }

    /**
     * Drops the effects that are currently applied, so a setting the user just changed takes hold
     * immediately instead of at the next app switch. Switching off the last remaining effect also
     * switches the feature itself off: it could not do anything any more, and a feature that shows
     * as activated while it silently does nothing is worse than an honest off.
     * @return whether the feature's own activation state changed, so the caller can refresh the
     * switch in the app bar
     */
    private fun refreshEffects(): Boolean {
        val application = getApplication<Application>()
        GrayscaleAppsFeature.refreshEffects(application)
        if (!GrayscaleAppsFeature.isActivated || GrayscaleAppsFeature.hasPermissions(application)) {
            return false
        }
        GrayscaleAppsFeature.isActivated = false
        FeaturesProvider.startOrStopFeature(GrayscaleAppsFeature)
        return true
    }

    /**
     * Sets whether the dialog to set the screen filter strength should be shown.
     */
    fun setShowScreenFilterIntensityDialog(show: Boolean) {
        _showScreenFilterIntensityDialog.value = show
    }

    /**
     * Toggles the extra dim setting.
     */
    fun toggleExtraDim(): Boolean {
        GrayscaleAppsFeature.extraDim = !GrayscaleAppsFeature.extraDim
        _extraDimActivated.value = GrayscaleAppsFeature.extraDim
        return refreshEffects()
    }

    /**
     * Toggles the ignore non full screen apps setting.
     */
    fun toggleIgnoreNonFullScreenApps() {
        GrayscaleAppsFeature.ignoreNonFullScreenApps = !GrayscaleAppsFeature.ignoreNonFullScreenApps
        _ignoreNonFullScreenApps.value = GrayscaleAppsFeature.ignoreNonFullScreenApps
    }

    /**
     * Sets whether the dialog to set the allowed daily color screen time should be shown.
     */
    fun setShowAllowedDailyColorScreenTimeDialog(show: Boolean) {
        _showAllowedDailyColorScreenTimeDialog.value = show
    }

    /**
     * Sets the allowed daily color screen time in minutes.
     */
    fun setAllowedDailyColorScreenTime(minutes: Long) {
        GrayscaleAppsFeature.allowedDailyColorScreenTime =
            minutes.minutes.inWholeMilliseconds // convert to milliseconds
        _allowedDailyColorScreenTime.value = minutes
    }
}