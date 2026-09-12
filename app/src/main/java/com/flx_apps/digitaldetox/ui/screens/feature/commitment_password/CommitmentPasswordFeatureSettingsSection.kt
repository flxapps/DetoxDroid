package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import com.flx_apps.digitaldetox.ui.widgets.CopyableText
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.formatCountdown

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

        // resetting the passphrase here hands out a new one right away, see completeRecovery
        CommitmentPasswordDialog.RECOVERY -> RecoveryDialog(
            onStart = { viewModel.initiateRecovery() },
            onCancelRecovery = { viewModel.cancelRecovery() },
            onReset = { viewModel.completeRecovery() },
            onDismiss = { viewModel.dismissDialog() }
        )

        CommitmentPasswordDialog.UNLOCK_TO_DISABLE -> UnlockToDisableDialog(
            viewModel, onFeatureStateChanged
        )

        CommitmentPasswordDialog.NONE -> {}
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
    SettingsGroup {
        RecoveryStatusTile(viewModel)
    }
}

@Composable
private fun FeatureSelectionSection(
    viewModel: CommitmentPasswordViewModel = viewModel(), enabled: Boolean
) {
    val lockableFeatures by viewModel.lockableFeatures.collectAsState()
    val selectedFeatureIds by viewModel.selectedFeatureIds.collectAsState()

    if (lockableFeatures.isEmpty()) return

    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp, top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.feature_commitmentPassword_selectFeatures),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.selectAllFeatures() }, enabled = enabled) {
                Text(stringResource(R.string.feature_commitmentPassword_selectAll))
            }
            TextButton(onClick = { viewModel.deselectAllFeatures() }, enabled = enabled) {
                Text(stringResource(R.string.feature_commitmentPassword_selectNone))
            }
        }
        SettingsGroup {
            lockableFeatures.forEach { feature ->
                val selected = selectedFeatureIds.contains(feature.id)
                ListItem(
                    headlineContent = { Text(text = stringResource(feature.texts.title)) },
                    leadingContent = {
                        Icon(painter = painterResource(feature.iconRes), contentDescription = null)
                    },
                    trailingContent = {
                        Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                    },
                    modifier = Modifier.toggleable(
                        value = selected, enabled = enabled, role = Role.Checkbox
                    ) { viewModel.toggleFeatureSelection(feature.id) }
                )
            }
        }
        Text(
            text = stringResource(
                R.string.feature_commitmentPassword_featuresSelected,
                selectedFeatureIds.size,
                lockableFeatures.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
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
            onClick = { viewModel.showRecoveryDialog() })
    } else {
        val remainingRecoveryTime by viewModel.remainingRecoveryTime.collectAsState()
        SimpleListTile(
            leadingIcon = Icons.Default.Timer,
            titleText = stringResource(R.string.feature_commitmentPassword_recovery_inProgress),
            subtitleText = stringResource(
                R.string.feature_commitmentPassword_recovery_timeRemaining,
                formatCountdown(LocalContext.current, remainingRecoveryTime)
            ),
            onClick = { viewModel.showRecoveryDialog() })
    }
}

/** What turning the password on means, one point per line, each with its icon. */
private val WalkthroughPoints = listOf(
    Icons.Default.Lock to R.string.feature_commitmentPassword_walkthrough_lock,
    Icons.Default.Key to R.string.feature_commitmentPassword_walkthrough_passphrase,
    Icons.Default.StopCircle to R.string.feature_commitmentPassword_walkthrough_stop,
    Icons.Default.Timer to R.string.feature_commitmentPassword_walkthrough_attempts,
    Icons.Default.History to R.string.feature_commitmentPassword_walkthrough_recovery,
)

@Composable
private fun WalkthroughDialog(viewModel: CommitmentPasswordViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_walkthrough_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WalkthroughPoints.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(text),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                Text(stringResource(R.string.feature_commitmentPassword_walkthrough_next))
            }
        },
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

/**
 * Hands out the new passphrase, set apart from the text around it so it can be read, selected or
 * copied without mistakes. It can't be dismissed: the only way on is to confirm it was saved.
 */
@Composable
private fun GeneratedPasswordDialog(
    viewModel: CommitmentPasswordViewModel, onFeatureStateChanged: () -> Unit
) {
    val generatedPassword by viewModel.generatedPassword.collectAsState()

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_commitmentPassword_generated_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_commitmentPassword_generated_message))
                CopyableText(
                    text = generatedPassword,
                    copyLabel = stringResource(R.string.feature_commitmentPassword_copy),
                    copiedMessage = stringResource(R.string.feature_commitmentPassword_generated_copied),
                    sensitive = true,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text(stringResource(R.string.feature_commitmentPassword_generated_hint))
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
private fun UnlockToDisableDialog(
    viewModel: CommitmentPasswordViewModel, onFeatureStateChanged: () -> Unit
) {
    PassphraseDialog(
        title = stringResource(R.string.feature_commitmentPassword_disable_title),
        description = stringResource(R.string.feature_commitmentPassword_disable_enterPassword),
        confirmLabel = stringResource(R.string.feature_commitmentPassword_disable),
        passphrase = viewModel.passwordInput.collectAsState().value,
        onPassphraseChange = { viewModel.onPasswordInputChanged(it) },
        wrongPassphrase = viewModel.wrongPassphrase.collectAsState().value,
        onConfirm = { viewModel.verifyAndDisable(onFeatureStateChanged) },
        onDismiss = { viewModel.dismissDialog() }
    )
}
