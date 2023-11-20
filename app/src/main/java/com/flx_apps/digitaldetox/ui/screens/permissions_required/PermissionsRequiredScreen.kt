package com.flx_apps.digitaldetox.ui.screens.permissions_required

import HyperlinkText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/**
 * The screen that is shown when specific permissions need to be granted from the computer (and
 * cannot be granted from the user within the Android OS).
 * @param grantPermissionsCommand The command that needs to be executed on the computer to grant
 * the required permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsRequiredScreen(
    grantPermissionsCommand: String, navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    Scaffold(topBar = {
        TopAppBar(title = {}, navigationIcon = {
            IconButton(onClick = {
                navViewModel.onBackPress()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
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
 */
@Composable
fun PermissionsRequiredScreenContent(grantPermissionsCommand: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp), verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Info",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(128.dp)
                .weight(1f)
        )
        Spacer(modifier = Modifier.weight(0.25f))
        Text(
            text = stringResource(id = R.string.noPermissions_text),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(
                id = R.string.noPermissions_text_command, grantPermissionsCommand
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(vertical = 32.dp)
        )
        HyperlinkText(
            fullTextResId = R.string.noPermissions_text_notRooted,
            linksActions = listOf("LINK"),
            hyperLinks = listOf("https://google.com"),
            fontSize = MaterialTheme.typography.bodyMedium.fontSize
        )
        Spacer(modifier = Modifier.weight(0.5f))
    }
}