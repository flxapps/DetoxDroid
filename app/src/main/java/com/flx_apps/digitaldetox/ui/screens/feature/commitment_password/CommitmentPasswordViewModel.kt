package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

enum class CommitmentPasswordState {
    NOT_SET, SET_AND_LOCKED, SET_AND_UNLOCKED,
}

enum class CommitmentPasswordDialog {
    NONE, WALKTHROUGH, GENERATED_PASSWORD, RECOVERY, UNLOCK_TO_DISABLE,
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

    private val _wrongPassphrase = MutableStateFlow(false)
    val wrongPassphrase: StateFlow<Boolean> = _wrongPassphrase

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
            // BCrypt with work factor 12 takes a few hundred ms — keep it off the main thread
            val passwordSet = withContext(Dispatchers.Default) {
                CommitmentPasswordFeature.setPassword(context, password)
            }
            if (passwordSet) {
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
        CommitmentPasswordFeature.updateActivationState(true, dispatchLifecycle = true)
        _showDialog.value = CommitmentPasswordDialog.NONE
        updateState()
    }

    fun onPasswordInputChanged(input: String) {
        _passwordInput.value = input
        _wrongPassphrase.value = false
    }

    fun initiateRecovery() {
        CommitmentPasswordFeature.initiateRecovery(context)
        _showDialog.value = CommitmentPasswordDialog.RECOVERY
        updateState()
    }

    fun cancelRecovery() {
        CommitmentPasswordFeature.cancelRecovery(context)
        _showDialog.value = CommitmentPasswordDialog.NONE
        updateState()
    }

    fun showRecoveryDialog() {
        _showDialog.value = CommitmentPasswordDialog.RECOVERY
    }

    fun completeRecovery() {
        viewModelScope.launch {
            if (CommitmentPasswordFeature.completeRecovery(context)) {
                CommitmentPasswordFeature.updateActivationState(false, dispatchLifecycle = true)
                val password = CommitmentPasswordFeature.generatePassphrase()
                _generatedPassword.value = password
                val passwordSet = withContext(Dispatchers.Default) {
                    CommitmentPasswordFeature.setPassword(context, password)
                }
                if (passwordSet) {
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
        _wrongPassphrase.value = false
        _showDialog.value = CommitmentPasswordDialog.UNLOCK_TO_DISABLE
    }

    fun verifyAndDisable(onDisabled: () -> Unit) {
        viewModelScope.launch {
            val isValid = withContext(Dispatchers.Default) {
                CommitmentPasswordFeature.verifyPassword(context, _passwordInput.value)
            }
            if (isValid) {
                CommitmentPasswordFeature.clearPasswordData(context)
                CommitmentPasswordFeature.lockSession()
                CommitmentPasswordFeature.updateActivationState(false, dispatchLifecycle = true)
                _showDialog.value = CommitmentPasswordDialog.NONE
                _passwordInput.value = ""
                updateState()
                onDisabled()
            } else {
                _wrongPassphrase.value = true
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
        _wrongPassphrase.value = false
    }

    /**
     * Copies the passphrase, marked as sensitive so the system's clipboard preview and keyboard
     * suggestions don't show it around.
     */
    fun copyPasswordToClipboard(password: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("Passphrase", password).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

}
