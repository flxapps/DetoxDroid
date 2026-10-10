package com.flx_apps.digitaldetox.ui.screens.about

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.ConfigBackup
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Saves the configuration to a file of the user's choosing and reads it back.
 *
 * Restoring shows what the file holds and what would change before anything is written, then
 * restarts DetoxDroid. It is out of reach while the commitment password holds any feature, because
 * a file exported before the password went on would otherwise put that configuration back over it.
 * Saving stays available: the file carries neither the password nor anything it protects.
 */
@Composable
fun ConfigBackupTiles() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ConfigBackup.Preview?>(null) }
    var busy by remember { mutableStateOf(false) }
    val commitmentPasswordToken by CommitmentPasswordFeature.stateToken.collectAsState()
    val brakeToken by EmergencyBrakeFeature.stateToken.collectAsState()
    // a file from before the brake would put looser settings back over it
    val braking = remember(brakeToken) { EmergencyBrakeFeature.isBraking() }
    val restoreLocked = remember(commitmentPasswordToken, braking) {
        braking || FeaturesProvider.featureList.any {
            CommitmentPasswordFeature.isFeatureLocked(it.id)
        }
    }
    val exportFailed = stringResource(id = R.string.about_backup_export_failed)
    val exportSaved = stringResource(id = R.string.about_backup_export_saved)
    val notASettingsFile = stringResource(id = R.string.about_backup_restore_failed)
    val readFailed = stringResource(id = R.string.about_backup_restore_readFailed)
    val nothingApplies = stringResource(id = R.string.about_backup_restore_nothingApplies)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigBackup.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = ConfigBackup.export()
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(content.toByteArray())
                    } ?: error("no output stream for $uri")
                    displayName(context, uri) ?: ConfigBackup.suggestedFileName()
                }.onFailure { Timber.w(it, "Could not write the settings file") }
            }
            busy = false
            val message = result.map { exportSaved.format(it) }.getOrDefault(exportFailed)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes().decodeToString() }
                        ?: error("no input stream for $uri")
                    ConfigBackup.preview(content)
                }.onFailure { Timber.w(it, "Could not read the settings file") }
            }
            busy = false
            result.onSuccess {
                if (it.values.isEmpty()) {
                    Toast.makeText(context, nothingApplies, Toast.LENGTH_LONG).show()
                } else {
                    preview = it
                }
            }.onFailure {
                val message = if (it is IllegalArgumentException) notASettingsFile else readFailed
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LinkTile(
        icon = Icons.Default.Save,
        title = stringResource(id = R.string.about_backup_export),
        subtitle = stringResource(id = R.string.about_backup_export_subtitle),
        enabled = !busy,
        onClick = { exportLauncher.launch(ConfigBackup.suggestedFileName()) }
    )
    LinkTile(
        icon = if (restoreLocked) Icons.Default.Lock else Icons.Default.SettingsBackupRestore,
        title = stringResource(id = R.string.about_backup_restore),
        subtitle = stringResource(
            id = if (braking) {
                R.string.settings_lockedByBrake_subtitle
            } else if (restoreLocked) {
                R.string.about_backup_restore_locked_subtitle
            } else {
                R.string.about_backup_restore_subtitle
            }
        ),
        enabled = !restoreLocked && !busy,
        onClick = { importLauncher.launch(arrayOf(ConfigBackup.MIME_TYPE, "text/plain", "*/*")) }
    )

    preview?.let { pending ->
        var restoring by remember(pending) { mutableStateOf(false) }
        RestorePreviewDialog(
            preview = pending,
            restoring = restoring,
            onConfirm = {
                restoring = true
                scope.launch {
                    val written = withContext(Dispatchers.IO) {
                        runCatching { ConfigBackup.import(pending.values) }
                            .onFailure { Timber.w(it, "Could not write the settings back") }
                            .getOrDefault(0)
                    }
                    if (written > 0) {
                        ConfigBackup.restartApp(context)
                    } else {
                        restoring = false
                        preview = null
                        Toast.makeText(context, readFailed, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { if (!restoring) preview = null }
        )
    }
}

/** The name the user gave the file in the picker, so the confirmation can repeat it. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()

/**
 * What the file holds, feature by feature, and how much of it differs from the phone's current
 * configuration. The button writes it and restarts.
 */
@Composable
private fun RestorePreviewDialog(
    preview: ConfigBackup.Preview,
    restoring: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.about_backup_preview_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                preview.file.createdAt?.let { createdAt ->
                    val savedOn = dateFormatter.format(createdAt)
                    Text(
                        text = preview.file.appVersionName?.let {
                            stringResource(id = R.string.about_backup_preview_saved, savedOn, it)
                        } ?: stringResource(
                            id = R.string.about_backup_preview_savedUnknownVersion, savedOn
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (preview.isFromNewerVersion) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.about_backup_preview_newerVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                preview.features.forEach { feature ->
                    FeatureRow(feature)
                }
                if (preview.otherSettings > 0) {
                    Text(
                        text = pluralStringResource(
                            id = R.plurals.about_backup_preview_otherSettings,
                            count = preview.otherSettings,
                            preview.otherSettings
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = if (preview.changes == 0) {
                        stringResource(id = R.string.about_backup_preview_noChanges)
                    } else {
                        pluralStringResource(
                            id = R.plurals.about_backup_preview_changes,
                            count = preview.changes,
                            preview.changes
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(id = R.string.about_backup_preview_excluded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !restoring) {
                if (restoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(id = R.string.about_backup_preview_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !restoring) {
                Text(stringResource(id = R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FeatureRow(feature: ConfigBackup.FeatureSummary) {
    val status = listOfNotNull(
        feature.isActivated?.let {
            stringResource(
                id = if (it) R.string.state_on else R.string.state_off
            )
        },
        feature.appCount?.let {
            pluralStringResource(id = R.plurals.about_backup_preview_apps, count = it, it)
        }
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature.titleRes?.let { stringResource(id = it) } ?: feature.featureId,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (status.isNotEmpty()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (feature.isActivated == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
