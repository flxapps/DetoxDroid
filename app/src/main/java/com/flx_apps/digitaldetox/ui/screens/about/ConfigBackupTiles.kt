package com.flx_apps.digitaldetox.ui.screens.about

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.ConfigBackup
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Saves the configuration to a file of the user's choosing and reads it back.
 *
 * Restoring is out of reach while the commitment password holds any feature, because a file
 * exported before the password went on would otherwise put that configuration back over it.
 * Saving stays available: the file carries neither the password nor anything it protects.
 */
@Composable
fun ConfigBackupTiles() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
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
    val importFailed = stringResource(id = R.string.about_backup_restore_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigBackup.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val content = ConfigBackup.export()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray())
                    } ?: error("no output stream for $uri")
                }.onFailure { Timber.w(it, "Could not write the settings file") }.isSuccess
            }
            if (!written) Toast.makeText(context, exportFailed, Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    LinkTile(
        icon = Icons.Default.Save,
        title = stringResource(id = R.string.about_backup_export),
        subtitle = stringResource(id = R.string.about_backup_export_subtitle),
        onClick = { exportLauncher.launch(ConfigBackup.suggestedFileName()) }
    )
    LinkTile(
        icon = if (restoreLocked) Icons.Default.Lock else Icons.Default.SettingsBackupRestore,
        title = stringResource(id = R.string.about_backup_restore),
        subtitle = stringResource(
            id = if (braking) {
                R.string.about_backup_restore_braking_subtitle
            } else if (restoreLocked) {
                R.string.about_backup_restore_locked_subtitle
            } else {
                R.string.about_backup_restore_subtitle
            }
        ),
        enabled = !restoreLocked,
        onClick = { importLauncher.launch(arrayOf(ConfigBackup.MIME_TYPE, "text/plain", "*/*")) }
    )

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(id = R.string.about_backup_restore)) },
            text = { Text(stringResource(id = R.string.about_backup_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val restored = withContext(Dispatchers.IO) {
                            runCatching {
                                val content = context.contentResolver.openInputStream(uri)
                                    ?.use { stream -> stream.readBytes().decodeToString() }
                                    ?: error("no input stream for $uri")
                                ConfigBackup.import(content)
                            }.onFailure { Timber.w(it, "Could not read the settings file") }
                                .getOrDefault(0)
                        }
                        if (restored > 0) {
                            ConfigBackup.restartApp(context)
                        } else {
                            Toast.makeText(context, importFailed, Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text(stringResource(id = R.string.about_backup_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}
