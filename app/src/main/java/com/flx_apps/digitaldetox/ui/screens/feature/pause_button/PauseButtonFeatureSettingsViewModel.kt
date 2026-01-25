package com.flx_apps.digitaldetox.ui.screens.feature.pause_button

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The possible dialogs that can be shown.
 * We use an enum, as only one dialog can be shown at a time.
 */
enum class PauseButtonFeatureSettingsViewModelDialog {
    NONE, PAUSE_DURATION, TIME_BETWEEN_PAUSES_DURATION, PICK_HARDWARE_KEY
}

/**
 * The view model for the pause button feature settings tile.
 */
@HiltViewModel
class PauseButtonFeatureSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    /**
     * The duration of the pause in minutes.
     */
    private val _pauseDuration: MutableStateFlow<Int> =
        MutableStateFlow(TimeUnit.MILLISECONDS.toMinutes(PauseButtonFeature.pauseDuration).toInt())
    val pauseDuration = _pauseDuration.asStateFlow()

    /**
     * The duration between pauses in minutes.
     */
    private val _timeBetweenPausesDuration: MutableStateFlow<Int> = MutableStateFlow(
        TimeUnit.MILLISECONDS.toMinutes(PauseButtonFeature.timeBetweenPausesDuration).toInt()
    )
    val timeBetweenPausesDuration = _timeBetweenPausesDuration.asStateFlow()

    /**
     * Whether to show the pause duration number picker dialog.
     */
    private val _visibleDialog: MutableStateFlow<PauseButtonFeatureSettingsViewModelDialog> =
        MutableStateFlow(PauseButtonFeatureSettingsViewModelDialog.NONE)
    val showPauseDurationNumberPickerDialog = _visibleDialog.asStateFlow()

    private val _hardwareKey = MutableStateFlow(PauseButtonFeature.hardwareKey)
    val hardwareKey = _hardwareKey.asStateFlow()

    private val _newHardwareKeySelection = MutableStateFlow(KeyEvent.KEYCODE_UNKNOWN)
    val newHardwareKeySelection = _newHardwareKeySelection.asStateFlow()

    /**
     * Sets the duration of the pause in minutes.
     */
    fun setPauseDuration(durationInMinutes: Int) {
        PauseButtonFeature.pauseDuration = TimeUnit.MINUTES.toMillis(durationInMinutes.toLong())
        _pauseDuration.value = durationInMinutes
    }

    /**
     * Sets the minimum time that has to pass between two pauses in minutes.
     */
    fun setTimeBetweenPausesDuration(durationInMinutes: Int) {
        PauseButtonFeature.timeBetweenPausesDuration =
            TimeUnit.MINUTES.toMillis(durationInMinutes.toLong())
        _timeBetweenPausesDuration.value = durationInMinutes
    }

    /**
     * Sets which dialog should be visible.
     */
    fun setVisibilityOfDialog(dialog: PauseButtonFeatureSettingsViewModelDialog) {
        _visibleDialog.value = dialog
    }

    /**
     * Shows the hardware key dialog if the accessibility service is running and we can listen for
     * key events.
     */
    fun showHardwareKeyDialog() {
        if (DetoxDroidAccessibilityService.instance == null) {
            // the accessibility service is not running, so we can't pick a hardware key
            val context = getApplication<Application>()
            Toast.makeText(
                context,
                context.getString(R.string.feature_pause_fromHardwareButton_appNotRunning),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // we want to pick a hardware key, so we have to listen for key events
        _newHardwareKeySelection.value = _hardwareKey.value
        DetoxDroidAccessibilityService.instance?.onKeyEventListener = { event ->
            _newHardwareKeySelection.value = event.keyCode
            true
        }
        setVisibilityOfDialog(PauseButtonFeatureSettingsViewModelDialog.PICK_HARDWARE_KEY)
    }

    /**
     * Hides the hardware key dialog and sets the new hardware key.
     * @param newHardwareKeyCode The new hardware key code.
     */
    fun hideHardwareKeyDialog(newHardwareKeyCode: Int? = null) {
        DetoxDroidAccessibilityService.instance?.onKeyEventListener = null
        if (newHardwareKeyCode != null) {
            _hardwareKey.value = newHardwareKeyCode
            PauseButtonFeature.hardwareKey = newHardwareKeyCode
        }
        setVisibilityOfDialog(PauseButtonFeatureSettingsViewModelDialog.NONE)
    }

    /**
     * Called when the user clicks on the "pause from assistant" tile.
     * Opens the Android settings for setting the assistant app.
     */
    fun callAndroidAssistantSettings() {
        getApplication<Application>().startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    /**
     * Opens the Android notification settings for the service channel.
     */
    fun openNotificationSettings() {
        NotificationHelper.openNotificationSettings(getApplication())
    }
}