package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.system_integration.AppSuspender
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModelFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
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
        }
        if (mode == DisableAppsMode.SUSPEND && !AppSuspender.isAvailable) {
            // without Shizuku the mode would look selected and quietly do nothing
            return false
        }
        if (DisableAppsFeature.operationMode == DisableAppsMode.DEACTIVATE) {
            // whatever the new mode is, nothing may stay hidden under the old one
            DisableAppsFeature.reactivateAllApps(app)
        }
        AppSuspender.releaseAll()
        DisableAppsFeature.operationMode = mode
        _operationMode.value = mode
        return true
    }
}
