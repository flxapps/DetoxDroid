package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import kotlinx.coroutines.delay

private data class LockGateState(
    val isPasswordActive: Boolean,
    val isPasswordSet: Boolean,
    val isFeatureProtected: Boolean,
    val isFeatureLocked: Boolean,
)

@Composable
private fun rememberLockGateState(featureId: String?): LockGateState {
    val context = LocalContext.current
    var state by remember {
        mutableStateOf(
            LockGateState(
                isPasswordActive = CommitmentPasswordFeature.isActivated,
                isPasswordSet = CommitmentPasswordFeature.isPasswordSet(context),
                isFeatureProtected = featureId?.let { CommitmentPasswordFeature.isFeatureProtected(it) }
                    ?: CommitmentPasswordFeature.isActivated,
                isFeatureLocked = featureId?.let { CommitmentPasswordFeature.isFeatureLocked(it) }
                    ?: (CommitmentPasswordFeature.isActivated && !CommitmentPasswordFeature.isSessionUnlocked())
            )
        )
    }

    LaunchedEffect(featureId) {
        while (true) {
            delay(500)
            val isPasswordActive = CommitmentPasswordFeature.isActivated
            val isPasswordSet = CommitmentPasswordFeature.isPasswordSet(context)
            val isFeatureProtected =
                featureId?.let { CommitmentPasswordFeature.isFeatureProtected(it) }
                    ?: isPasswordActive
            val isFeatureLocked =
                featureId?.let { CommitmentPasswordFeature.isFeatureLocked(it) }
                    ?: (isPasswordActive && !CommitmentPasswordFeature.isSessionUnlocked())
            state = LockGateState(
                isPasswordActive = isPasswordActive,
                isPasswordSet = isPasswordSet,
                isFeatureProtected = isFeatureProtected,
                isFeatureLocked = isFeatureLocked,
            )
        }
    }

    return state
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

@Composable
private fun SettingsLockBanner(isLocked: Boolean) {
    var showUnlockDialog by remember { mutableStateOf(false) }

    val backgroundColor =
        if (isLocked) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer
    val contentColor =
        if (isLocked) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isLocked) stringResource(R.string.feature_commitmentPassword_banner_locked)
                    else stringResource(R.string.feature_commitmentPassword_banner_unlocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isLocked) {
                Button(
                    onClick = { showUnlockDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = contentColor, contentColor = backgroundColor
                    )
                ) {
                    Text(stringResource(R.string.feature_commitmentPassword_unlock))
                }
            } else {
                OutlinedButton(onClick = { CommitmentPasswordFeature.lockSession() }) {
                    Text(stringResource(R.string.feature_commitmentPassword_lockAgain))
                }
            }
        }
    }

    if (showUnlockDialog) {
        UnlockPasswordDialog(
            onDismiss = { showUnlockDialog = false },
            onUnlocked = { showUnlockDialog = false }
        )
    }
}

@Composable
private fun UnlockPasswordDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(CommitmentPasswordFeature.failedAttempts) }
    var isLockedOut by remember { mutableStateOf(CommitmentPasswordFeature.isLockedOut()) }
    var remainingLockoutTime by remember { mutableStateOf(CommitmentPasswordFeature.getRemainingLockoutTime()) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (CommitmentPasswordFeature.isLockedOut()) {
                delay(1000)
                remainingLockoutTime = CommitmentPasswordFeature.getRemainingLockoutTime()
                isLockedOut = CommitmentPasswordFeature.isLockedOut()
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordFlow(onDismiss = { showForgotPasswordDialog = false })
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.feature_commitmentPassword_unlock)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isLockedOut) {
                        Text(
                            text = stringResource(
                                R.string.feature_commitmentPassword_lockedOut,
                                formatDuration(remainingLockoutTime)
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(stringResource(R.string.feature_commitmentPassword_unlock_description))
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = ""
                            },
                            label = { Text(stringResource(R.string.feature_commitmentPassword_enter)) },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = errorMessage.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (failedAttempts > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.feature_commitmentPassword_attemptsRemaining,
                                        CommitmentPasswordFeature.MAX_FAILED_ATTEMPTS - failedAttempts
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showForgotPasswordDialog = true }) {
                            Text(stringResource(R.string.feature_commitmentPassword_forgot))
                        }
                    }
                }
            },
            confirmButton = {
                if (!isLockedOut) {
                    TextButton(
                        onClick = {
                            val isValid =
                                CommitmentPasswordFeature.verifyPassword(context, passwordInput)
                            if (isValid) {
                                CommitmentPasswordFeature.unlockSession()
                                passwordInput = ""
                                onUnlocked()
                            } else {
                                errorMessage =
                                    context.getString(R.string.feature_commitmentPassword_incorrect)
                                failedAttempts = CommitmentPasswordFeature.failedAttempts
                                if (CommitmentPasswordFeature.isLockedOut()) {
                                    isLockedOut = true
                                    remainingLockoutTime =
                                        CommitmentPasswordFeature.getRemainingLockoutTime()
                                }
                            }
                        },
                        enabled = passwordInput.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.feature_commitmentPassword_verify))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    passwordInput = ""
                    errorMessage = ""
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ForgotPasswordFlow(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isRecoveryInProgress by remember { mutableStateOf(CommitmentPasswordFeature.isRecoveryInProgress()) }
    var isRecoveryReady by remember { mutableStateOf(CommitmentPasswordFeature.isRecoveryReady()) }
    var remainingRecoveryTime by remember { mutableStateOf(CommitmentPasswordFeature.getRemainingRecoveryTime()) }

    LaunchedEffect(isRecoveryInProgress) {
        while (isRecoveryInProgress && !isRecoveryReady) {
            delay(1000)
            isRecoveryInProgress = CommitmentPasswordFeature.isRecoveryInProgress()
            isRecoveryReady = CommitmentPasswordFeature.isRecoveryReady()
            remainingRecoveryTime = CommitmentPasswordFeature.getRemainingRecoveryTime()
        }
    }

    when {
        isRecoveryReady -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_ready)) },
            text = { Text(stringResource(R.string.feature_commitmentPassword_recovery_ready_message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (CommitmentPasswordFeature.completeRecovery(context)) {
                        CommitmentPasswordFeature.isActivated = false
                        onDismiss()
                    }
                }) {
                    Text(stringResource(R.string.feature_commitmentPassword_recovery_complete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )

        isRecoveryInProgress -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Timer, contentDescription = null) },
            title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_inProgress)) },
            text = {
                Text(
                    stringResource(
                        R.string.feature_commitmentPassword_recovery_timeRemaining,
                        formatDuration(remainingRecoveryTime)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    CommitmentPasswordFeature.cancelRecovery(context)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.feature_commitmentPassword_recovery_cancel))
                }
            }
        )

        else -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
            title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_title)) },
            text = { Text(stringResource(R.string.feature_commitmentPassword_recovery_message)) },
            confirmButton = {
                TextButton(onClick = {
                    CommitmentPasswordFeature.initiateRecovery(context)
                    isRecoveryInProgress = true
                    remainingRecoveryTime = CommitmentPasswordFeature.getRemainingRecoveryTime()
                }) {
                    Text(stringResource(R.string.feature_commitmentPassword_recovery_initiate))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
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
