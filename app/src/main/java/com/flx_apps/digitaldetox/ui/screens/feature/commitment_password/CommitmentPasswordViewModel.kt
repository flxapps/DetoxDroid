package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class CommitmentPasswordState {
    NOT_SET, SET_AND_LOCKED, SET_AND_UNLOCKED,
}

enum class CommitmentPasswordDialog {
    NONE, WALKTHROUGH, GENERATED_PASSWORD, FORGOT_PASSWORD, RECOVERY_IN_PROGRESS, RECOVERY_READY, UNLOCK_TO_DISABLE,
}

@HiltViewModel
class CommitmentPasswordViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _currentState = MutableStateFlow(CommitmentPasswordState.NOT_SET)
    val currentState: StateFlow<CommitmentPasswordState> = _currentState

    private val _showDialog = MutableStateFlow(CommitmentPasswordDialog.NONE)
    val showDialog: StateFlow<CommitmentPasswordDialog> = _showDialog

    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword: StateFlow<String> = _generatedPassword

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts

    private val _isLockedOut = MutableStateFlow(false)
    val isLockedOut: StateFlow<Boolean> = _isLockedOut

    private val _remainingLockoutTime = MutableStateFlow(0L)
    val remainingLockoutTime: StateFlow<Long> = _remainingLockoutTime

    private val _isRecoveryInProgress = MutableStateFlow(false)
    val isRecoveryInProgress: StateFlow<Boolean> = _isRecoveryInProgress

    private val _isRecoveryReady = MutableStateFlow(false)
    val isRecoveryReady: StateFlow<Boolean> = _isRecoveryReady

    private val _remainingRecoveryTime = MutableStateFlow(0L)
    val remainingRecoveryTime: StateFlow<Long> = _remainingRecoveryTime

    private val _lockableFeatures = MutableStateFlow<List<Feature>>(emptyList())
    val lockableFeatures: StateFlow<List<Feature>> = _lockableFeatures

    private val _selectedFeatureIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFeatureIds: StateFlow<Set<String>> = _selectedFeatureIds

    init {
        updateState()
        loadLockableFeatures()
        viewModelScope.launch {
            CommitmentPasswordFeature.stateToken.collect {
                updateState()
            }
        }
    }

    fun updateState() {
        val isPasswordSet = CommitmentPasswordFeature.isPasswordSet(context)
        val isLocked =
            CommitmentPasswordFeature.isActivated && !CommitmentPasswordFeature.isSessionUnlocked()

        _currentState.value = when {
            !isPasswordSet -> CommitmentPasswordState.NOT_SET
            isLocked -> CommitmentPasswordState.SET_AND_LOCKED
            else -> CommitmentPasswordState.SET_AND_UNLOCKED
        }

        _failedAttempts.value = CommitmentPasswordFeature.failedAttempts
        _isLockedOut.value = CommitmentPasswordFeature.isLockedOut()
        _remainingLockoutTime.value = CommitmentPasswordFeature.getRemainingLockoutTime()
        _isRecoveryInProgress.value = CommitmentPasswordFeature.isRecoveryInProgress()
        _isRecoveryReady.value = CommitmentPasswordFeature.isRecoveryReady()
        _remainingRecoveryTime.value = CommitmentPasswordFeature.getRemainingRecoveryTime()
        _selectedFeatureIds.value = CommitmentPasswordFeature.getConfiguredLockedFeatureIds()
    }

    private fun loadLockableFeatures() {
        val features = CommitmentPasswordFeature.getLockableFeatures()
        _lockableFeatures.value = features

        val configuredIds = CommitmentPasswordFeature.getConfiguredLockedFeatureIds()
        if (CommitmentPasswordFeature.lockedFeatureIds.isEmpty()) {
            _selectedFeatureIds.value =
                features.filter { (it as? LockableFeature)?.lockedByDefault == true }
                    .map { it.id }.toSet()
        } else {
            _selectedFeatureIds.value = configuredIds
        }
    }

    fun toggleFeatureSelection(featureId: String) {
        val current = _selectedFeatureIds.value.toMutableSet()
        if (current.contains(featureId)) current.remove(featureId) else current.add(featureId)
        _selectedFeatureIds.value = current
        CommitmentPasswordFeature.updateLockedFeatureIds(current)
    }

    fun selectAllFeatures() {
        val allIds = _lockableFeatures.value.map { it.id }.toSet()
        _selectedFeatureIds.value = allIds
        CommitmentPasswordFeature.updateLockedFeatureIds(allIds)
    }

    fun deselectAllFeatures() {
        _selectedFeatureIds.value = emptySet()
        CommitmentPasswordFeature.updateLockedFeatureIds(emptySet())
    }

    fun showWalkthroughDialog() {
        _showDialog.value = CommitmentPasswordDialog.WALKTHROUGH
    }

    fun onWalkthroughAccepted() {
        viewModelScope.launch {
            val password = CommitmentPasswordFeature.generatePassphrase()
            _generatedPassword.value = password
            if (CommitmentPasswordFeature.setPassword(context, password)) {
                _showDialog.value = CommitmentPasswordDialog.GENERATED_PASSWORD
            } else {
                Timber.e("Failed to set password")
                _showDialog.value = CommitmentPasswordDialog.NONE
            }
        }
    }

    fun onPasswordSaved() {
        CommitmentPasswordFeature.initializeLockedFeatures()
        CommitmentPasswordFeature.lockSession()
        CommitmentPasswordFeature.updateActivationState(true)
        _showDialog.value = CommitmentPasswordDialog.NONE
        updateState()
    }

    fun onPasswordInputChanged(input: String) {
        _passwordInput.value = input
        _errorMessage.value = ""
    }

    fun verifyPassword() {
        viewModelScope.launch {
            val isValid = CommitmentPasswordFeature.verifyPassword(context, _passwordInput.value)
            if (isValid) {
                CommitmentPasswordFeature.unlockSession()
                _showDialog.value = CommitmentPasswordDialog.NONE
                _passwordInput.value = ""
                updateState()
            } else {
                _errorMessage.value = context.getString(R.string.feature_commitmentPassword_incorrect)
                updateState()
                if (CommitmentPasswordFeature.isLockedOut()) {
                    _showDialog.value = CommitmentPasswordDialog.NONE
                }
            }
        }
    }

    fun lockSession() {
        CommitmentPasswordFeature.lockSession()
        updateState()
    }

    fun showForgotPasswordDialog() {
        _showDialog.value = CommitmentPasswordDialog.FORGOT_PASSWORD
    }

    fun initiateRecovery() {
        CommitmentPasswordFeature.initiateRecovery(context)
        _showDialog.value = CommitmentPasswordDialog.RECOVERY_IN_PROGRESS
        updateState()
    }

    fun cancelRecovery() {
        CommitmentPasswordFeature.cancelRecovery(context)
        _showDialog.value = CommitmentPasswordDialog.NONE
        updateState()
    }

    fun showRecoveryInProgressDialog() {
        _showDialog.value = CommitmentPasswordDialog.RECOVERY_IN_PROGRESS
    }

    fun showRecoveryReadyDialog() {
        _showDialog.value = CommitmentPasswordDialog.RECOVERY_READY
    }

    fun completeRecovery() {
        viewModelScope.launch {
            if (CommitmentPasswordFeature.completeRecovery(context)) {
                CommitmentPasswordFeature.updateActivationState(false)
                val password = CommitmentPasswordFeature.generatePassphrase()
                _generatedPassword.value = password
                if (CommitmentPasswordFeature.setPassword(context, password)) {
                    _showDialog.value = CommitmentPasswordDialog.GENERATED_PASSWORD
                } else {
                    _showDialog.value = CommitmentPasswordDialog.NONE
                }
                updateState()
            }
        }
    }

    fun showUnlockToDisableDialog() {
        _passwordInput.value = ""
        _errorMessage.value = ""
        _showDialog.value = CommitmentPasswordDialog.UNLOCK_TO_DISABLE
    }

    fun verifyAndDisable(onDisabled: () -> Unit) {
        viewModelScope.launch {
            val isValid = CommitmentPasswordFeature.verifyPassword(context, _passwordInput.value)
            if (isValid) {
                CommitmentPasswordFeature.clearPasswordData(context)
                CommitmentPasswordFeature.lockSession()
                CommitmentPasswordFeature.updateActivationState(false)
                _showDialog.value = CommitmentPasswordDialog.NONE
                _passwordInput.value = ""
                updateState()
                onDisabled()
            } else {
                _errorMessage.value = context.getString(R.string.feature_commitmentPassword_incorrect)
                updateState()
                if (CommitmentPasswordFeature.isLockedOut()) {
                    _showDialog.value = CommitmentPasswordDialog.NONE
                }
            }
        }
    }

    fun dismissDialog() {
        _showDialog.value = CommitmentPasswordDialog.NONE
        _passwordInput.value = ""
        _errorMessage.value = ""
    }

    fun copyPasswordToClipboard(password: String) {
        val clipboard =
            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Passphrase", password)
        clipboard.setPrimaryClip(clip)
    }

    fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }
}
