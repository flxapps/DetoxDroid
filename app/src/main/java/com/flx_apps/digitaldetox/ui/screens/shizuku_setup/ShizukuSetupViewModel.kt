package com.flx_apps.digitaldetox.ui.screens.shizuku_setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.util.PrivilegedShell
import com.flx_apps.digitaldetox.util.ShizukuUtils
import com.flx_apps.digitaldetox.util.hasWriteSecureSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the guided Shizuku setup is for. It decides where the wizard ends and how it is worded.
 */
enum class ShizukuSetupGoal {
    /**
     * The WRITE_SECURE_SETTINGS permission behind the real grayscale filter. Shizuku grants it
     * once (root or adb do as well) and may stop afterwards: the permission stays until an
     * uninstall.
     */
    GRAYSCALE,

    /**
     * Shizuku itself, running and allowed to run commands for DetoxDroid, so that locked apps can
     * be suspended. Unlike a permission this is lost at every reboot, until Shizuku is started
     * again.
     */
    SUSPEND,
}

/**
 * Where the user currently stands in the guided Shizuku setup. [READY] means Shizuku runs and
 * DetoxDroid may use it, [COMPLETED] that the goal is reached: for [ShizukuSetupGoal.SUSPEND] the
 * same thing, for [ShizukuSetupGoal.GRAYSCALE] the granted permission, no matter how it was granted.
 */
enum class ShizukuSetupState {
    NOT_INSTALLED, INSTALLED_NOT_RUNNING, RUNNING_NOT_GRANTED, READY, COMPLETED
}

/**
 * Drives the guided Shizuku setup wizard. The state is derived from binder/package-manager checks
 * and polled every few seconds (off the main thread), so the wizard advances by itself while the
 * user hops between Shizuku, the system settings and DetoxDroid. For the grayscale permission the
 * polling stops once it is granted, as it cannot be lost short of an uninstall. A running Shizuku
 * can stop at any time, so for the suspend goal the polling goes on while the wizard is open.
 */
@HiltViewModel
class ShizukuSetupViewModel @Inject constructor(
    private val application: Application, savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    companion object {
        private const val POLL_INTERVAL_MS = 2000L

        /** The argument naming the [ShizukuSetupGoal] the wizard was opened for. */
        const val ARG_GOAL = "goal"
    }

    val goal: ShizukuSetupGoal = savedStateHandle.get<String>(ARG_GOAL)
        ?.let(ShizukuSetupGoal::valueOf) ?: ShizukuSetupGoal.GRAYSCALE

    private val _setupState = MutableStateFlow(
        // the full derivation talks to binders — seed only with the cheap local permission check
        // and let the first poll tick fill in the exact step
        if (goal == ShizukuSetupGoal.GRAYSCALE && application.hasWriteSecureSettings()) {
            ShizukuSetupState.COMPLETED
        } else {
            ShizukuSetupState.NOT_INSTALLED
        }
    )
    val setupState: StateFlow<ShizukuSetupState> = _setupState

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable

    init {
        // the permission may already be there (adb grant, earlier wizard run) — still finish any
        // pending grayscale activation from onboarding
        if (_setupState.value == ShizukuSetupState.COMPLETED) {
            viewModelScope.launch(Dispatchers.IO) { onCompleted() }
        }
        if (goal == ShizukuSetupGoal.GRAYSCALE) {
            // root can grant the permission, but suspending always goes through Shizuku, so the
            // check (which spawns an `su` process and can block) is only worth it here
            viewModelScope.launch(Dispatchers.IO) {
                _isRootAvailable.value = PrivilegedShell.isRootAvailable()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshStateNow()
                val permanent = goal == ShizukuSetupGoal.GRAYSCALE &&
                        _setupState.value == ShizukuSetupState.COMPLETED
                if (permanent) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Triggers an immediate re-check, e.g. when the user returns from Shizuku or the settings.
     */
    fun refreshState() {
        viewModelScope.launch(Dispatchers.IO) { refreshStateNow() }
    }

    private fun refreshStateNow() {
        val newState = deriveState()
        if (newState == ShizukuSetupState.COMPLETED && _setupState.value != ShizukuSetupState.COMPLETED) {
            onCompleted()
        }
        _setupState.value = newState
    }

    private fun deriveState(): ShizukuSetupState = when {
        goal == ShizukuSetupGoal.GRAYSCALE && application.hasWriteSecureSettings() ->
            ShizukuSetupState.COMPLETED

        ShizukuUtils.isShizukuAvailable() -> when {
            !ShizukuUtils.hasShizukuPermission() -> ShizukuSetupState.RUNNING_NOT_GRANTED
            goal == ShizukuSetupGoal.SUSPEND -> ShizukuSetupState.COMPLETED
            else -> ShizukuSetupState.READY
        }

        ShizukuUtils.isShizukuInstalled() -> ShizukuSetupState.INSTALLED_NOT_RUNNING
        else -> ShizukuSetupState.NOT_INSTALLED
    }

    /**
     * Asks Shizuku to let DetoxDroid use it (Shizuku shows its own confirmation dialog); the
     * polling loop picks up the result.
     */
    fun requestShizukuAccess() {
        ShizukuUtils.requestShizukuPermission()
    }

    /**
     * The final step of the grayscale goal: grants WRITE_SECURE_SETTINGS through Shizuku.
     */
    fun grantViaShizuku() {
        val command =
            application.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission)
        PrivilegedShell.runViaShizuku(command) { refreshState() }
    }

    /**
     * Shortcut for rooted devices: grants WRITE_SECURE_SETTINGS directly via a root shell.
     */
    fun grantViaRoot() {
        val command =
            application.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission)
        PrivilegedShell.runAsRoot(command) { refreshState() }
    }

    /**
     * Starts the accessibility service now that the grayscale permission is there, so a gray step
     * that was waiting for it takes hold on its own. A Shizuku set up for suspending changes
     * nothing about the service.
     */
    private fun onCompleted() {
        if (goal != ShizukuSetupGoal.GRAYSCALE) return
        if (DetoxDroidAccessibilityService.state.value == DetoxDroidState.Inactive) {
            runCatching { AccessibilityServiceController.activate(application) }
        }
    }
}
