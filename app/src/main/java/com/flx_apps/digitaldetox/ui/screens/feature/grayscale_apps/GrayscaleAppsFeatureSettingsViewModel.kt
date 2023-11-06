package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import androidx.lifecycle.ViewModel
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
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
class GrayscaleAppsFeatureSettingsViewModel @Inject constructor() : ViewModel() {
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
     * Toggles the extra dim setting.
     */
    fun toggleExtraDim() {
        GrayscaleAppsFeature.extraDim = !GrayscaleAppsFeature.extraDim
        _extraDimActivated.value = GrayscaleAppsFeature.extraDim
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
        if (show && UsageStatsProvider.screenTimeToday == 0L) {
            // the user has not given the permission to access usage stats yet, which is required
            // for this feature to work. We do not want to show the dialog in this case.
            return
        }
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