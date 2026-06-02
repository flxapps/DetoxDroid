package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * Settings UI for the Commitment Password feature.
 *
 * - When disabled (no password set): the activation switch in FeatureScreen triggers the walkthrough
 * - When enabled and locked: PasswordLockGate handles the banner and unlock flow
 * - Settings in this section manage feature selection and recovery status
 */
@Composable
fun CommitmentPasswordFeatureSettingsSection(
    viewModel: CommitmentPasswordViewModel = viewModel(), onFeatureStateChanged: () -> Unit = {}
) {
    val currentState by viewModel.currentState.collectAsState()
    val showDialog by viewModel.showDialog.collectAsState()
    val context = LocalContext.current
    val navViewModel: NavViewModel = NavViewModel.navViewModel()

    LaunchedEffect(Unit) {
        viewModel.updateState()
    }

    when (showDialog) {
        CommitmentPasswordDialog.WALKTHROUGH -> WalkthroughDialog(viewModel)
        CommitmentPasswordDialog.GENERATED_PASSWORD -> GeneratedPasswordDialog(
            viewModel, onFeatureStateChanged
        )

        CommitmentPasswordDialog.FORGOT_PASSWORD -> ForgotPasswordDialog(viewModel)
        CommitmentPasswordDialog.RECOVERY_IN_PROGRESS -> RecoveryInProgressDialog(viewModel)
        CommitmentPasswordDialog.RECOVERY_READY -> RecoveryReadyDialog(viewModel)
        CommitmentPasswordDialog.UNLOCK_TO_DISABLE -> UnlockToDisableDialog(
            viewModel, onFeatureStateChanged
        )

        else -> {}
    }

    if (!DetoxDroidDeviceAdminReceiver.isGranted(context)) {
        IconCard(
            icon = Icons.Default.Security,
            contentDescription = stringResource(R.string.feature_commitmentPassword_deviceOwner_hint_title),
            modifier = Modifier.clickable {
                navViewModel.openRoute(
                    NavigationRoutes.PermissionsRequired(
                        GrantPermissionsCommand(
                            command = context.getString(R.string.rootCommand_grantDeviceAdminPermission),
                            supportsShizuku = false
                        )
                    )
                )
            }) {
            Text(
                text = stringResource(R.string.feature_commitmentPassword_deviceOwner_hint_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.feature_commitmentPassword_deviceOwner_hint_subtitle),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    val selectionEnabled = currentState != CommitmentPasswordState.SET_AND_LOCKED
    FeatureSelectionSection(viewModel = viewModel, enabled = selectionEnabled)

    RecoveryStatusTile(viewModel)
}

@Composable
private fun FeatureSelectionSection(
    viewModel: CommitmentPasswordViewModel = viewModel(), enabled: Boolean
) {
    val lockableFeatures by viewModel.lockableFeatures.collectAsState()
    val selectedFeatureIds by viewModel.selectedFeatureIds.collectAsState()
    val context = LocalContext.current

    if (lockableFeatures.isEmpty()) return

    Column(
        modifier = Modifier
            .padding(16.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.feature_commitmentPassword_selectFeatures),
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                TextButton(onClick = { viewModel.selectAllFeatures() }, enabled = enabled) {
                    Text(stringResource(R.string.feature_commitmentPassword_selectAll))
                }
                TextButton(onClick = { viewModel.deselectAllFeatures() }, enabled = enabled) {
                    Text(stringResource(R.string.feature_commitmentPassword_selectNone))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        lockableFeatures.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { viewModel.toggleFeatureSelection(feature.id) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(feature.texts.title), modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = selectedFeatureIds.contains(feature.id),
                    onCheckedChange = { viewModel.toggleFeatureSelection(feature.id) },
                    enabled = enabled
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.feature_commitmentPassword_featuresSelected,
                selectedFeatureIds.size,
                lockableFeatures.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RecoveryStatusTile(viewModel: CommitmentPasswordViewModel) {
    val isRecoveryInProgress by viewModel.isRecoveryInProgress.collectAsState()
    val isRecoveryReady by viewModel.isRecoveryReady.collectAsState()

    if (!isRecoveryInProgress && !isRecoveryReady) return

    if (isRecoveryReady) {
        SimpleListTile(
            leadingIcon = Icons.Default.CheckCircle,
            titleText = stringResource(R.string.feature_commitmentPassword_recovery_ready),
            subtitleText = stringResource(R.string.feature_commitmentPassword_recovery_ready_message),
            onClick = { viewModel.showRecoveryReadyDialog() })
    } else {
        val remainingRecoveryTime by viewModel.remainingRecoveryTime.collectAsState()
        SimpleListTile(
            leadingIcon = Icons.Default.Timer,
            titleText = stringResource(R.string.feature_commitmentPassword_recovery_inProgress),
            subtitleText = stringResource(
                R.string.feature_commitmentPassword_recovery_timeRemaining,
                viewModel.formatDuration(remainingRecoveryTime)
            ),
            onClick = { viewModel.showRecoveryInProgressDialog() })
    }
}

@Composable
private fun WalkthroughDialog(viewModel: CommitmentPasswordViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_walkthrough_title)) },
        text = { Text(stringResource(R.string.feature_commitmentPassword_walkthrough_message)) },
        confirmButton = {
            TextButton(onClick = { viewModel.onWalkthroughAccepted() }) {
                Text(stringResource(R.string.feature_commitmentPassword_walkthrough_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text(stringResource(R.string.action_cancel))
            }
        })
}

@Composable
private fun GeneratedPasswordDialog(
    viewModel: CommitmentPasswordViewModel, onFeatureStateChanged: () -> Unit
) {
    val context = LocalContext.current
    val generatedPassword by viewModel.generatedPassword.collectAsState()

    AlertDialog(
        onDismissRequest = { /* Prevent dismissal – user must save the passphrase */ },
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_generated_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.feature_commitmentPassword_generated_message, generatedPassword
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.copyPasswordToClipboard(generatedPassword)
                        Toast.makeText(
                            context,
                            R.string.feature_commitmentPassword_generated_copied,
                            Toast.LENGTH_SHORT
                        ).show()
                    }, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.feature_commitmentPassword_copy))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onPasswordSaved()
                onFeatureStateChanged()
            }) {
                Text(stringResource(R.string.feature_commitmentPassword_generated_confirm))
            }
        })
}

@Composable
private fun ForgotPasswordDialog(viewModel: CommitmentPasswordViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_title)) },
        text = { Text(stringResource(R.string.feature_commitmentPassword_recovery_message)) },
        confirmButton = {
            TextButton(onClick = { viewModel.initiateRecovery() }) {
                Text(stringResource(R.string.feature_commitmentPassword_recovery_initiate))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text(stringResource(R.string.action_cancel))
            }
        })
}

@Composable
private fun RecoveryInProgressDialog(viewModel: CommitmentPasswordViewModel) {
    val remainingRecoveryTime by viewModel.remainingRecoveryTime.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_inProgress)) },
        text = {
            Text(
                stringResource(
                    R.string.feature_commitmentPassword_recovery_timeRemaining,
                    viewModel.formatDuration(remainingRecoveryTime)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelRecovery() }) {
                Text(stringResource(R.string.feature_commitmentPassword_recovery_cancel))
            }
        })
}

@Composable
private fun RecoveryReadyDialog(viewModel: CommitmentPasswordViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_recovery_ready)) },
        text = { Text(stringResource(R.string.feature_commitmentPassword_recovery_ready_message)) },
        confirmButton = {
            TextButton(onClick = { viewModel.completeRecovery() }) {
                Text(stringResource(R.string.feature_commitmentPassword_recovery_complete))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text(stringResource(R.string.action_cancel))
            }
        })
}

@Composable
private fun UnlockToDisableDialog(
    viewModel: CommitmentPasswordViewModel, onFeatureStateChanged: () -> Unit
) {
    val passwordInput by viewModel.passwordInput.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val failedAttempts by viewModel.failedAttempts.collectAsState()
    val isLockedOut by viewModel.isLockedOut.collectAsState()
    val remainingLockoutTime by viewModel.remainingLockoutTime.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_disable_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_commitmentPassword_disable_enterPassword))
                Spacer(modifier = Modifier.height(16.dp))
                if (isLockedOut) {
                    Text(
                        text = stringResource(
                            R.string.feature_commitmentPassword_lockedOut,
                            viewModel.formatDuration(remainingLockoutTime)
                        ), color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { viewModel.onPasswordInputChanged(it) },
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
                                (CommitmentPasswordFeature.MAX_FAILED_ATTEMPTS - failedAttempts).coerceAtLeast(
                                    0
                                )
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.verifyAndDisable(onFeatureStateChanged) },
                enabled = passwordInput.isNotEmpty()
            ) {
                Text(stringResource(R.string.feature_commitmentPassword_disable))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text(stringResource(R.string.action_cancel))
            }
        })
}
