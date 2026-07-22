package com.flx_apps.digitaldetox.ui.screens.shizuku_setup

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.ui.screens.onboarding.OnboardingState
import com.flx_apps.digitaldetox.util.ShizukuUtils
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where the user currently stands in the guided Shizuku setup. [COMPLETED] means
 * WRITE_SECURE_SETTINGS is granted — no matter how (Shizuku, root or adb).
 */
enum class ShizukuSetupState {
    NOT_INSTALLED, INSTALLED_NOT_RUNNING, RUNNING_NOT_GRANTED, READY, COMPLETED
}

/**
 * Drives the guided Shizuku setup wizard. The state is derived from binder/package-manager checks
 * and polled every few seconds (off the main thread), so the wizard advances by itself while the
 * user hops between Shizuku, the system settings and DetoxDroid. Polling stops once the permission
 * is granted — it cannot be lost short of an uninstall.
 */
@HiltViewModel
class ShizukuSetupViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    companion object {
        private const val POLL_INTERVAL_MS = 2000L
    }

    private val _setupState = MutableStateFlow(
        // the full derivation talks to binders — seed only with the cheap local permission check
        // and let the first poll tick fill in the exact step
        if (hasWriteSecureSettings()) ShizukuSetupState.COMPLETED
        else ShizukuSetupState.NOT_INSTALLED
    )
    val setupState: StateFlow<ShizukuSetupState> = _setupState

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable

    init {
        // the permission may already be there (adb grant, earlier wizard run) — still finish any
        // pending grayscale activation from onboarding
        if (_setupState.value == ShizukuSetupState.COMPLETED) {
            viewModelScope.launch(Dispatchers.IO) { onPermissionGranted() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // the root check spawns an `su` process and can block — run it once, off the UI
            _isRootAvailable.value = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _setupState.value != ShizukuSetupState.COMPLETED) {
                refreshStateNow()
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
            onPermissionGranted()
        }
        _setupState.value = newState
    }

    private fun deriveState(): ShizukuSetupState = when {
        hasWriteSecureSettings() -> ShizukuSetupState.COMPLETED
        ShizukuUtils.isShizukuAvailable() -> if (ShizukuUtils.hasShizukuPermission()) {
            ShizukuSetupState.READY
        } else {
            ShizukuSetupState.RUNNING_NOT_GRANTED
        }

        ShizukuUtils.isShizukuInstalled() -> ShizukuSetupState.INSTALLED_NOT_RUNNING
        else -> ShizukuSetupState.NOT_INSTALLED
    }

    private fun hasWriteSecureSettings(): Boolean =
        application.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Asks Shizuku to let DetoxDroid use it (Shizuku shows its own confirmation dialog); the
     * polling loop picks up the result.
     */
    fun requestShizukuAccess() {
        ShizukuUtils.requestShizukuPermission()
    }

    /**
     * The final step: grants WRITE_SECURE_SETTINGS through Shizuku.
     */
    fun grantViaShizuku() {
        val command =
            application.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission)
        ShizukuUtils.executeCommand(command) { _, _ -> refreshState() }
    }

    /**
     * Shortcut for rooted devices: grants WRITE_SECURE_SETTINGS directly via a root shell.
     */
    fun grantViaRoot() {
        val command =
            application.getString(R.string.rootCommand_grantWriteSecuritySettingsPermission)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Shell.cmd(command).exec() }
            refreshStateNow()
        }
    }

    /**
     * Finishes what onboarding could not: activates any pending feature whose permission just
     * arrived (grayscale via the freshly granted WRITE_SECURE_SETTINGS) and starts the
     * accessibility service.
     */
    private fun onPermissionGranted() {
        OnboardingState.resolvePendingFeatureActivations()
        if (DetoxDroidAccessibilityService.state.value == DetoxDroidState.Inactive) {
            runCatching { AccessibilityServiceController.activate(application) }
        }
    }
}
