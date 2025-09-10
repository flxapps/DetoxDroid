package com.flx_apps.digitaldetox.ui.screens.permissions_required

import HyperlinkText
import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.util.ShizukuUtils
import com.stericson.RootShell.RootShell
import kotlinx.parcelize.Parcelize

@Parcelize
data class GrantPermissionsCommand(val command: String, val supportsShizuku: Boolean) : Parcelable

/**
 * The screen that is shown when specific permissions need to be granted from the computer (and
 * cannot be granted from the user within the Android OS).
 *
 * This screen now supports three methods of granting permissions:
 * 1. Manual ADB command from computer (always shown)
 * 2. Root shell (if device is rooted)
 * 3. Shizuku service (if Shizuku is installed and running)
 *
 * @param grantPermissionsCommand The command that needs to be executed on the computer to grant
 * the required permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsRequiredScreen(
    grantPermissionsCommand: GrantPermissionsCommand,
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    Scaffold(topBar = {
        TopAppBar(title = {
            Text(text = stringResource(R.string.noPermissions))
        }, navigationIcon = {
            IconButton(onClick = {
                navViewModel.onBackPress()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }
        })
    }) {
        Box(
            modifier = Modifier
                .padding(it)
                .padding(16.dp)
        ) {
            PermissionsRequiredScreenContent(grantPermissionsCommand)
        }
    }
}

/**
 * The content of the [PermissionsRequiredScreen].
 * It contains a short explanation of why the permissions are required and how to grant them using
 * adb and a command that is provided as a parameter.
 * If the device is rooted, it also offers a button to grant the permissions using root shell.
 * If Shizuku is available, it offers a button to grant permissions via Shizuku.
 */
@Composable
fun PermissionsRequiredScreenContent(
    grantPermissionsCommand: GrantPermissionsCommand,
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val isRootAvailable = RootShell.isRootAvailable()
    val isShizukuAvailable = ShizukuUtils.isShizukuAvailable()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Info",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(128.dp)
        )
        Spacer(modifier = Modifier.weight(0.25f))
        Text(
            text = stringResource(id = R.string.noPermissions_text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            text = stringResource(
                id = R.string.noPermissions_text_command, grantPermissionsCommand.command
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        HyperlinkText(
            fullTextResId = R.string.noPermissions_text_visitGitHub,
            linksActions = listOf("GITHUB"),
            hyperLinks = listOf("https://github.com/flxapps/DetoxDroid"),
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        // Show buttons for available privilege methods
        if (isRootAvailable) {
            GrantPermissionsCard(
                description = stringResource(id = R.string.noPermissions_text_rooted),
                buttonText = stringResource(id = R.string.noPermissions_text_rooted_go),
                onGrantPermissions = {
                    // try grant permissions using root
                    ShizukuUtils.executeCommand(grantPermissionsCommand.command) { success, _ ->
                        if (success) {
                            navViewModel.onBackPress()
                        }
                    }
                })
        }

        if (isShizukuAvailable && grantPermissionsCommand.supportsShizuku) {
            GrantPermissionsCard(
                description = stringResource(id = R.string.noPermissions_text_shizuku),
                buttonText = stringResource(id = R.string.noPermissions_text_shizuku_go),
                onGrantPermissions = {
                    // try grant permissions using Shizuku
                    ShizukuUtils.executeCommand(grantPermissionsCommand.command) { success, _ ->
                        if (success) {
                            navViewModel.onBackPress()
                        }
                    }
                })
        }

        Spacer(modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun GrantPermissionsCard(
    description: String, buttonText: String, onGrantPermissions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = Icons.Default.PermDeviceInformation,
                    contentDescription = "Info",
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            OutlinedButton(onClick = onGrantPermissions) {
                Text(text = buttonText)
            }
        }
    }
}