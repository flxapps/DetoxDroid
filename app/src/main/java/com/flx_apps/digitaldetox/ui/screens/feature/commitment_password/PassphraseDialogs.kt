package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.util.formatCountdown
import kotlinx.coroutines.delay

/**
 * Asks for the passphrase, for unlocking the settings as well as for switching the password off.
 * The field can show what was typed, since five random words are easy to get wrong blind, and
 * says how many tries are left; after too many wrong ones it counts the pause down instead, and
 * confirming waits until the pause is over.
 *
 * @param wrongPassphrase Whether the last passphrase confirmed was wrong.
 * @param extraAction Shown below the field, e.g. the way into the recovery.
 */
@Composable
internal fun PassphraseDialog(
    title: String,
    description: String,
    confirmLabel: String,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    wrongPassphrase: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    verifying: Boolean = false,
    extraAction: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val stateToken by CommitmentPasswordFeature.stateToken.collectAsState()
    val failedAttempts = remember(stateToken) { CommitmentPasswordFeature.failedAttempts }
    val lockedOutFor = rememberCountdown(stateToken) {
        CommitmentPasswordFeature.getRemainingLockoutTime()
    }
    var shown by rememberSaveable { mutableStateOf(false) }
    val canConfirm = passphrase.isNotEmpty() && !verifying && lockedOutFor == 0L
    // after a pause the count stays used up, and every wrong try pauses again right away
    val attemptsLeft = CommitmentPasswordFeature.MAX_FAILED_ATTEMPTS - failedAttempts
    val wrongText = stringResource(R.string.feature_commitmentPassword_incorrect)
    val attemptsText = pluralStringResource(
        R.plurals.feature_commitmentPassword_attemptsRemaining, attemptsLeft, attemptsLeft
    )
    val hint = if (lockedOutFor > 0L) {
        stringResource(
            R.string.feature_commitmentPassword_lockedOut, formatCountdown(context, lockedOutFor)
        )
    } else {
        listOfNotNull(
            wrongText.takeIf { wrongPassphrase },
            attemptsText.takeIf { failedAttempts > 0 && attemptsLeft > 0 }
        ).joinToString(" · ").ifEmpty { null }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(description)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text(stringResource(R.string.feature_commitmentPassword_enter)) },
                    singleLine = true,
                    isError = wrongPassphrase || lockedOutFor > 0L,
                    visualTransformation = if (shown) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm() }),
                    trailingIcon = {
                        IconButton(onClick = { shown = !shown }) {
                            Icon(
                                imageVector = if (shown) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (shown) {
                                        R.string.feature_commitmentPassword_hide
                                    } else {
                                        R.string.feature_commitmentPassword_show
                                    }
                                )
                            )
                        }
                    },
                    supportingText = hint?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
                extraAction()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** The way into the recovery from a [PassphraseDialog], lined up with the text above it. */
@Composable
internal fun ForgotPassphraseButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text(stringResource(R.string.feature_commitmentPassword_forgot))
    }
}

/**
 * The way back in without the passphrase: starting the 24-hour wait, the wait itself with how much
 * of it is left, and the reset once it is over, which the dialog moves on to by itself.
 */
@Composable
internal fun RecoveryDialog(
    onStart: () -> Unit,
    onCancelRecovery: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val stateToken by CommitmentPasswordFeature.stateToken.collectAsState()
    val started = remember(stateToken) { CommitmentPasswordFeature.recoveryInitiatedAt > 0L }
    val remaining = rememberCountdown(stateToken) {
        CommitmentPasswordFeature.getRemainingRecoveryTime()
    }
    when {
        started && remaining == 0L -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_ready)) },
            text = {
                Text(stringResource(R.string.feature_commitmentPassword_recovery_ready_message))
            },
            confirmButton = {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.feature_commitmentPassword_recovery_complete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        )

        started -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Timer, contentDescription = null) },
            title = {
                Text(stringResource(R.string.feature_commitmentPassword_recovery_inProgress))
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            R.string.feature_commitmentPassword_recovery_timeRemaining,
                            formatCountdown(context, remaining)
                        )
                    )
                    LinearProgressIndicator(
                        progress = {
                            1f - remaining.toFloat() / CommitmentPasswordFeature.RECOVERY_DURATION_MS
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelRecovery) {
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
                TextButton(onClick = onStart) {
                    Text(stringResource(R.string.feature_commitmentPassword_recovery_initiate))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/**
 * A time left that counts itself down while it is shown, started over whenever [key] changes. It
 * moves on whenever its [formatCountdown] text would change: by the second below an hour, by the
 * minute above.
 */
@Composable
private fun rememberCountdown(key: Any, remaining: () -> Long): Long =
    produceState(initialValue = remaining(), key) {
        value = remaining()
        while (value > 0L) {
            val step = if (value >= 3_600_000L) 60_000L else 1_000L
            delay(value % step + 1)
            value = remaining()
        }
    }.value
