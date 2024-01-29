package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModelFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The view model for the [DisableAppsFeatureSettingsSection].
 */
@HiltViewModel
class DisableAppsFeatureSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    companion object : FeatureViewModelFactory()

    private val _operationMode = MutableStateFlow(DisableAppsFeature.operationMode)
    val operationMode = _operationMode.asStateFlow()

    /**
     * The allowed daily screen time in minutes.
     * @see [DisableAppsFeature.allowedDailyScreenTime]
     */
    private val _allowedDailyScreenTime =
        MutableStateFlow(TimeUnit.MILLISECONDS.toMinutes(DisableAppsFeature.allowedDailyScreenTime))
    val allowedDailyTime = _allowedDailyScreenTime.asStateFlow()

    /**
     * Whether the daily screen time picker dialog is visible.
     */
    private val _dailyScreenTimePickerDialogVisible = MutableStateFlow(false)
    val dailyScreenTimePickerDialogVisible = _dailyScreenTimePickerDialogVisible.asStateFlow()

    /**
     * Changes the operation mode of the feature.
     * @param mode The new operation mode.
     * @return True if the operation mode was changed, false otherwise.
     * @see DisableAppsMode
     */
    fun changeOperationMode(mode: DisableAppsMode): Boolean {
        Timber.d("Changing operation mode to $mode")
        if (mode == DisableAppsFeature.operationMode) return true
        val app = getApplication<Application>()
        if (mode == DisableAppsMode.DEACTIVATE && !DetoxDroidDeviceAdminReceiver.isGranted(app)) {
            return false
        } else if (DisableAppsFeature.operationMode == DisableAppsMode.DEACTIVATE && mode == DisableAppsMode.BLOCK) {
            // we eventually need to reactivate the apps
            DisableAppsFeature.setAppsDeactivated(app, false, forceOperation = true)
        }
        DisableAppsFeature.operationMode = mode
        _operationMode.value = mode
        return true
    }

    /**
     * Sets the visibility of the daily screen time picker dialog.
     */
    fun setShowDailyScreenTimePickerDialog(visible: Boolean) {
        _dailyScreenTimePickerDialogVisible.value = visible
    }

    /**
     * Sets the allowed daily screen time.
     * @param minutes The allowed daily screen time in minutes.
     */
    fun setAllowedDailyScreenTime(minutes: Long) {
        DisableAppsFeature.allowedDailyScreenTime = TimeUnit.MINUTES.toMillis(minutes)
        _allowedDailyScreenTime.value = minutes
    }
}