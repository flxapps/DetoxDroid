package com.flx_apps.digitaldetox.ui.screens.home;

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import javax.inject.Inject

val AccessibilityServiceComponent =
    DetoxDroidApplication::class.java.`package`!!.name + "/" + DetoxDroidAccessibilityService::class.java.name

enum class HomeScreenSnackbarState {
    Hidden, ShowRequireWriteSecureSettingsPermission
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver

    /**
     * The current state of the accessibility service. This is a [StateFlow], so it can be observed
     * by other components.
     * @see DetoxDroidAccessibilityService.state
     */
    val detoxDroidState: StateFlow<DetoxDroidState> = DetoxDroidAccessibilityService.state

    private val _snackbarState = MutableStateFlow(HomeScreenSnackbarState.Hidden)
    val snackbarState: StateFlow<HomeScreenSnackbarState> = _snackbarState

    /**
     * Toggles the state of the accessibility service.
     * TODO right now, this method requires the WRITE_SECURE_SETTINGS permission. We should consider
     *   using the AccessibilityService API instead, because some features of DetoxDroid can be
     *   used without the WRITE_SECURE_SETTINGS permission. (However, DetoxDroid makes much more
     *   sense if this permission is granted.)
     * @return the new state of the accessibility service or null if the (de-)activation failed
     * @see DetoxDroidAccessibilityService
     * @see DetoxDroidState
     */
    fun toggleDetoxDroidIsRunning(): DetoxDroidState? {
        val shouldBeRunning = detoxDroidState.value != DetoxDroidState.Active
        kotlin.runCatching { // if we don't have the permission to write secure settings, an exception will be thrown
            if (shouldBeRunning) {
                activateAccessibilityService()
                if (getEnabledAccessibilityServices().contains(AccessibilityServiceComponent)) {
                    return DetoxDroidState.Active
                }
            } else {
                disableAccessibilityService()
                if (!getEnabledAccessibilityServices().contains(AccessibilityServiceComponent)) {
                    return DetoxDroidState.Inactive
                }
            }
        }
        // (de-)activation of accessibility service failed, so the WRITE_SECURE_SETTINGS permission is missing

        viewModelScope.launch {
            _snackbarState.onEmpty {
                emit(HomeScreenSnackbarState.ShowRequireWriteSecureSettingsPermission)
            }
        }

        setSnackbarState(HomeScreenSnackbarState.ShowRequireWriteSecureSettingsPermission)
        return null
    }

    fun setSnackbarState(state: HomeScreenSnackbarState) {
        _snackbarState.value = state
    }

    /**
     * Returns a list of all enabled accessibility services.
     */
    private fun getEnabledAccessibilityServices(): String {
        return Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
    }

    /**
     * Activates the accessibility service. This is done by adding the service to the list of
     * enabled accessibility services and starting the service. The service is then triggered
     * manually once to make sure it is running.
     * @see DetoxDroidAccessibilityService
     */
    private fun activateAccessibilityService() {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "$accessibilityServices:$AccessibilityServiceComponent".trim(':')
        )
        Settings.Secure.putString(
            contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1"
        )
        application.startService(
            Intent(
                application, DetoxDroidAccessibilityService::class.java
            )
        )
    }

    /**
     * Disables the accessibility service.
     * @see DetoxDroidAccessibilityService
     */
    private fun disableAccessibilityService() {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            accessibilityServices.replace(AccessibilityServiceComponent, "").trim(':')
        )
        application.stopService(
            Intent(
                application, DetoxDroidAccessibilityService::class.java
            )
        )
    }
}