package com.flx_apps.digitaldetox.ui.screens.home;

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject


val AccessibilityServiceComponent =
    DetoxDroidApplication::class.java.`package`!!.name + "/" + DetoxDroidAccessibilityService::class.java.name

enum class HomeScreenSnackbarState {
    Hidden, ShowStartAcccessibilityServiceManually
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
        Timber.d("state = ${detoxDroidState.value}")
        val shouldBeRunning = detoxDroidState.value != DetoxDroidState.Active
        kotlin.runCatching { // if we don't have the permission to write secure settings, an exception will be thrown
            if (shouldBeRunning) {
                activateAccessibilityService()
                if (DetoxDroidAccessibilityService.updateState() == DetoxDroidState.Active) {
                    return DetoxDroidState.Active
                }
            } else {
                disableAccessibilityService()
                if (DetoxDroidAccessibilityService.updateState() == DetoxDroidState.Inactive) {
                    return DetoxDroidState.Inactive
                }
            }
        }

        // (de-)activation of accessibility service failed, so we need to show a snackbar to ask the user to do it manually
        setSnackbarState(HomeScreenSnackbarState.ShowStartAcccessibilityServiceManually)
        return null
    }

    fun setSnackbarState(state: HomeScreenSnackbarState) {
        _snackbarState.value = state
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

    /**
     * Stops DetoxDroid and all running features, revokes the device admin permission and uninstalls.
     */
    fun uninstallDetoxDroid() {
        // call onDestroy() manually to run all cleanup tasks (e.g. stop all features) in a blocking way
        // (as application.stopService() is asynchronous)
        DetoxDroidAccessibilityService.instance?.onDestroy()
        kotlin.runCatching { disableAccessibilityService() } // run this anyway
        kotlin.runCatching { DetoxDroidDeviceAdminReceiver.revokePermission(application) }
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = "package:${application.packageName}".toUri()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        application.startActivity(intent)
    }
}