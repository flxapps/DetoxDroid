package com.flx_apps.digitaldetox.ui.screens.feature.emergency_brake

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.system_integration.EmergencyBrakeTileService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The possible dialogs that can be shown.
 * We use an enum, as only one dialog can be shown at a time.
 */
enum class EmergencyBrakeDialog {
    NONE, END_EARLY, AUTO_PULL_BREAKS, AUTO_PULL_WINDOW, AUTO_PULL_DURATION
}

/**
 * The view model for the emergency brake settings. The running brake is not in here: it changes
 * from the outside as well (the tile, the notification, its own end), so the screen reads it off
 * [EmergencyBrakeFeature.stateToken].
 */
@HiltViewModel
class EmergencyBrakeFeatureSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    /** The duration picked for the next brake, in minutes. */
    private val _duration = MutableStateFlow(
        TimeUnit.MILLISECONDS.toMinutes(EmergencyBrakeFeature.lastDuration).toInt()
    )
    val duration = _duration.asStateFlow()

    private val _visibleDialog = MutableStateFlow(EmergencyBrakeDialog.NONE)
    val visibleDialog = _visibleDialog.asStateFlow()

    private val _autoPull = MutableStateFlow(EmergencyBrakeFeature.autoPull)
    val autoPull = _autoPull.asStateFlow()

    private val _autoPullBreaks = MutableStateFlow(EmergencyBrakeFeature.autoPullBreaks)
    val autoPullBreaks = _autoPullBreaks.asStateFlow()

    /** In minutes. */
    private val _autoPullWindow = MutableStateFlow(
        TimeUnit.MILLISECONDS.toMinutes(EmergencyBrakeFeature.autoPullWindow).toInt()
    )
    val autoPullWindow = _autoPullWindow.asStateFlow()

    /** In minutes. */
    private val _autoPullDuration = MutableStateFlow(
        TimeUnit.MILLISECONDS.toMinutes(EmergencyBrakeFeature.autoPullDuration).toInt()
    )
    val autoPullDuration = _autoPullDuration.asStateFlow()

    fun setDuration(minutes: Int) {
        _duration.value = minutes
    }

    fun setVisibilityOfDialog(dialog: EmergencyBrakeDialog) {
        _visibleDialog.value = dialog
    }

    /** Pulls the brake for the picked duration. */
    fun pull() {
        EmergencyBrakeFeature.pull(getApplication(), pickedDurationMs())
    }

    /** Makes the running brake last the picked duration longer. */
    fun extend() {
        EmergencyBrakeFeature.extend(getApplication(), pickedDurationMs())
    }

    private fun pickedDurationMs(): Long = TimeUnit.MINUTES.toMillis(_duration.value.toLong())

    fun undo() {
        EmergencyBrakeFeature.undo(getApplication())
    }

    fun requestEnd() {
        EmergencyBrakeFeature.requestEnd(getApplication())
        setVisibilityOfDialog(EmergencyBrakeDialog.NONE)
    }

    fun withdrawEndRequest() {
        EmergencyBrakeFeature.withdrawEndRequest(getApplication())
    }

    fun toggleAutoPull() {
        EmergencyBrakeFeature.autoPull = !EmergencyBrakeFeature.autoPull
        _autoPull.value = EmergencyBrakeFeature.autoPull
    }

    fun setAutoPullBreaks(breaks: Int) {
        EmergencyBrakeFeature.autoPullBreaks = breaks
        _autoPullBreaks.value = breaks
    }

    fun setAutoPullWindow(minutes: Int) {
        EmergencyBrakeFeature.autoPullWindow = TimeUnit.MINUTES.toMillis(minutes.toLong())
        _autoPullWindow.value = minutes
    }

    fun setAutoPullDuration(minutes: Int) {
        EmergencyBrakeFeature.autoPullDuration = TimeUnit.MINUTES.toMillis(minutes.toLong())
        _autoPullDuration.value = minutes
    }

    /**
     * Asks the system to add the brake tile to the Quick Settings. The system shows its own dialog;
     * only a tile that is already there gets no answer, so that case is confirmed here.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestAddQuickSettingsTile() {
        val app = getApplication<Application>()
        app.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(app, EmergencyBrakeTileService::class.java),
            app.getString(R.string.app_brakeTile),
            Icon.createWithResource(app, R.drawable.ic_brake),
            app.mainExecutor
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                Toast.makeText(
                    app, R.string.feature_brake_fromQuickSettings_alreadyAdded, Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
