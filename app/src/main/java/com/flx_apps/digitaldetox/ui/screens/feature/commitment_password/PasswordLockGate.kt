package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LockGateState(
    val isPasswordActive: Boolean,
    val isPasswordSet: Boolean,
    val isFeatureProtected: Boolean,
    val isFeatureLocked: Boolean,
)

@Composable
private fun rememberLockGateState(featureId: String?): LockGateState {
    val context = LocalContext.current
    val stateToken by CommitmentPasswordFeature.stateToken.collectAsState()
    return remember(stateToken, featureId, context) {
        val isPasswordActive = CommitmentPasswordFeature.isActivated
        val isPasswordSet = CommitmentPasswordFeature.isPasswordSet(context)
        val isFeatureProtected =
            featureId?.let { CommitmentPasswordFeature.isFeatureProtected(it) } ?: isPasswordActive
        val isFeatureLocked =
            featureId?.let { CommitmentPasswordFeature.isFeatureLocked(it) }
                ?: (isPasswordActive && !CommitmentPasswordFeature.isSessionUnlocked())
        LockGateState(
            isPasswordActive = isPasswordActive,
            isPasswordSet = isPasswordSet,
            isFeatureProtected = isFeatureProtected,
            isFeatureLocked = isFeatureLocked,
        )
    }
}

/**
 * Wraps content that can be locked by the Commitment Password feature.
 *
 * When the password is active and the given feature is locked, provides `LocalSettingsLocked = true`
 * to child composables. A banner is shown above the content with an unlock / lock-again button.
 *
 * @param featureId The feature whose lock state to check. Pass `null` to check global activation.
 * @param showBanner Whether to show the lock/unlock banner.
 * @param content The protected content.
 */
@Composable
fun PasswordLockGate(
    featureId: String? = null,
    showBanner: Boolean = true,
    content: @Composable () -> Unit
) {
    val state = rememberLockGateState(featureId)

    if (state.isPasswordActive && state.isPasswordSet && state.isFeatureProtected) {
        Column {
            if (showBanner) {
                SettingsLockBanner(isLocked = state.isFeatureLocked)
            }
            CompositionLocalProvider(LocalSettingsLocked provides state.isFeatureLocked) {
                content()
            }
        }
    } else {
        CompositionLocalProvider(LocalSettingsLocked provides false) {
            content()
        }
    }
}

/**
 * Shows the lock banner without wrapping content in a Column. Useful for placing the banner
 * inside an existing scrollable layout.
 */
@Composable
fun SettingsLockBannerIfNeeded(featureId: String? = null) {
    val state = rememberLockGateState(featureId)
    if (state.isPasswordActive && state.isPasswordSet && state.isFeatureProtected) {
        SettingsLockBanner(isLocked = state.isFeatureLocked)
    }
}

/**
 * Says whether the settings below are locked, with the way to unlock them, or to lock them again
 * for the rest of the session once they are open.
 */
@Composable
private fun SettingsLockBanner(isLocked: Boolean) {
    var showUnlockDialog by remember { mutableStateOf(false) }
    IconCard(icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen) {
        Text(
            text = stringResource(
                if (isLocked) {
                    R.string.feature_commitmentPassword_banner_locked
                } else {
                    R.string.feature_commitmentPassword_banner_unlocked
                }
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        if (isLocked) {
            Button(
                onClick = { showUnlockDialog = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.feature_commitmentPassword_unlock))
            }
        } else {
            OutlinedButton(
                onClick = { CommitmentPasswordFeature.lockSession() },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.feature_commitmentPassword_lockAgain))
            }
        }
    }

    if (showUnlockDialog) {
        UnlockPasswordDialog(onDismiss = { showUnlockDialog = false })
    }
}

/**
 * Unlocks the settings for the session, or leads into the recovery for a forgotten passphrase.
 * Resetting the passphrase there switches the password off.
 */
@Composable
private fun UnlockPasswordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var wrongPassphrase by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }

    if (recovering) {
        RecoveryDialog(
            onStart = { CommitmentPasswordFeature.initiateRecovery(context) },
            onCancelRecovery = {
                CommitmentPasswordFeature.cancelRecovery(context)
                recovering = false
            },
            onReset = {
                if (CommitmentPasswordFeature.completeRecovery(context)) {
                    CommitmentPasswordFeature.updateActivationState(false, dispatchLifecycle = true)
                    onDismiss()
                }
            },
            onDismiss = { recovering = false }
        )
        return
    }
    PassphraseDialog(
        title = stringResource(R.string.feature_commitmentPassword_unlock),
        description = stringResource(R.string.feature_commitmentPassword_unlock_description),
        confirmLabel = stringResource(R.string.feature_commitmentPassword_verify),
        passphrase = passphrase,
        onPassphraseChange = {
            passphrase = it
            wrongPassphrase = false
        },
        wrongPassphrase = wrongPassphrase,
        verifying = verifying,
        onConfirm = {
            // BCrypt (work factor 12) takes a few hundred ms, too long for the main thread
            verifying = true
            scope.launch {
                val isValid = withContext(Dispatchers.Default) {
                    CommitmentPasswordFeature.verifyPassword(context, passphrase)
                }
                verifying = false
                if (isValid) {
                    CommitmentPasswordFeature.unlockSession()
                    onDismiss()
                } else {
                    wrongPassphrase = true
                }
            }
        },
        onDismiss = onDismiss,
        extraAction = { ForgotPassphraseButton(onClick = { recovering = true }) }
    )
}
