package com.flx_apps.digitaldetox.ui.screens.home

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.ui.screens.onboarding.OnboardingState
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * The state of the snackbar on the home screen.
 */
enum class HomeScreenSnackbarState {
    Hidden, ShowStartAcccessibilityServiceManually, CommitmentPasswordLocked
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
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
     * Returns null when blocked by the Commitment Password.
     * @return the new state of the accessibility service or null if the (de-)activation failed
     * @see DetoxDroidAccessibilityService
     * @see DetoxDroidState
     */
    fun toggleDetoxDroidIsRunning(): DetoxDroidState? {
        Timber.d("state = ${detoxDroidState.value}")

        // Block stopping DetoxDroid while Commitment Password is active and locked
        if (detoxDroidState.value == DetoxDroidState.Active
            && CommitmentPasswordFeature.isActivated
            && !CommitmentPasswordFeature.isSessionUnlocked()
        ) {
            setSnackbarState(HomeScreenSnackbarState.CommitmentPasswordLocked)
            return null
        }

        val shouldBeRunning = detoxDroidState.value != DetoxDroidState.Active
        kotlin.runCatching {
            if (shouldBeRunning && AccessibilityServiceController.activate(application)) {
                return DetoxDroidState.Active
            } else if (!shouldBeRunning && AccessibilityServiceController.deactivate(application)) {
                return DetoxDroidState.Inactive
            }
        }

        setSnackbarState(HomeScreenSnackbarState.ShowStartAcccessibilityServiceManually)
        return null
    }

    fun setSnackbarState(state: HomeScreenSnackbarState) {
        _snackbarState.value = state
    }

    /**
     * Called when the home screen (re)appears: if onboarding configured features it could not
     * activate for lack of a permission, and that permission showed up in the meantime (Shizuku
     * wizard, root, adb or the system settings), finish the pending activations.
     * @return whether the "finish setup" reminder card should be visible
     */
    fun resolvePendingOnboardingSetup(): Boolean =
        OnboardingState.resolvePendingFeatureActivations()

    /**
     * Which permission the pending onboarding setup is still waiting for, so the "finish setup"
     * card can route the user to the right place. Grayscale (WRITE_SECURE_SETTINGS) needs the
     * guided Shizuku/adb wizard and takes precedence; everything else needs the overlay permission,
     * which the user can grant from the system settings directly.
     */
    fun pendingSetupNeedsGrayscalePermission(): Boolean =
        OnboardingState.isGrayscaleActivationPending

    /**
     * Stops DetoxDroid and all running features, revokes the device admin permission and uninstalls.
     * Blocked whenever Commitment Password is active.
     */
    fun uninstallDetoxDroid(): Boolean {
        if (CommitmentPasswordFeature.isActivated) {
            setSnackbarState(HomeScreenSnackbarState.CommitmentPasswordLocked)
            return false
        }
        DetoxDroidAccessibilityService.instance?.onDestroy()
        kotlin.runCatching { AccessibilityServiceController.deactivate(application) }
        kotlin.runCatching { DetoxDroidDeviceAdminReceiver.revokePermission(application) }
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = "package:${application.packageName}".toUri()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        application.startActivity(intent)
        return true
    }
}