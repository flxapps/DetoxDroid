package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import androidx.lifecycle.ViewModel
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DoomScrollingSensitivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BreakDoomScrollingFeatureSettingsViewModel @Inject constructor() : ViewModel() {
    private val _timeUntilWarning: MutableStateFlow<Int> = MutableStateFlow(
        TimeUnit.MILLISECONDS.toMinutes(BreakDoomScrollingFeature.timeUntilWarning).toInt()
    )
    val timeUntilWarning: StateFlow<Int> = _timeUntilWarning

    private val _showTimeUntilWarningNumberPickerDialog: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val showTimeUntilWarningNumberPickerDialog: StateFlow<Boolean> =
        _showTimeUntilWarningNumberPickerDialog

    /**
     * Shows/hides the time until warning number picker dialog.
     */
    fun setTimeUntilNumberPickerDialogVisible(visible: Boolean) {
        _showTimeUntilWarningNumberPickerDialog.value = visible
    }

    /**
     * Sets the time until the warning is shown.
     * @param timeInMinutes the time in minutes
     */
    fun setTimeUntilWarning(timeInMinutes: Int) {
        _timeUntilWarning.value = timeInMinutes
        BreakDoomScrollingFeature.timeUntilWarning =
            TimeUnit.MINUTES.toMillis(timeInMinutes.toLong())
    }

    private val _cooldownTime: MutableStateFlow<Int> = MutableStateFlow(
        TimeUnit.MILLISECONDS.toMinutes(BreakDoomScrollingFeature.cooldownTime).toInt()
    )
    val cooldownTime: StateFlow<Int> = _cooldownTime

    private val _showCooldownTimeNumberPickerDialog: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val showCooldownTimeNumberPickerDialog: StateFlow<Boolean> =
        _showCooldownTimeNumberPickerDialog

    /**
     * Shows/hides the cooldown time number picker dialog.
     */
    fun setCooldownTimeNumberPickerDialogVisible(visible: Boolean) {
        _showCooldownTimeNumberPickerDialog.value = visible
    }

    /**
     * Sets how long an app stays locked after a doom-scrolling incident.
     * @param timeInMinutes the time in minutes
     */
    fun setCooldownTime(timeInMinutes: Int) {
        _cooldownTime.value = timeInMinutes
        BreakDoomScrollingFeature.cooldownTime = TimeUnit.MINUTES.toMillis(timeInMinutes.toLong())
    }

    private val _detectionSensitivity: MutableStateFlow<DoomScrollingSensitivity> =
        MutableStateFlow(BreakDoomScrollingFeature.detectionSensitivity)
    val detectionSensitivity: StateFlow<DoomScrollingSensitivity> = _detectionSensitivity

    /**
     * Sets how easily the scroll-intensity trigger fires.
     */
    fun setDetectionSensitivity(sensitivity: DoomScrollingSensitivity) {
        _detectionSensitivity.value = sensitivity
        BreakDoomScrollingFeature.detectionSensitivity = sensitivity
    }
}