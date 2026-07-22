package com.flx_apps.digitaldetox.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.observeAsState

/**
 * Reminder card shown while onboarding configured features that are still waiting for a permission
 * (grayscale for WRITE_SECURE_SETTINGS, or scroll breaks / app blocking for the overlay
 * permission). Re-evaluated whenever the user returns to the home screen — once the permission
 * shows up (Shizuku wizard, adb or the system settings), the pending activation is completed and
 * the card disappears.
 */
@Composable
fun FinishSetupCard(
    homeViewModel: HomeViewModel = viewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    var visible by remember { mutableStateOf(false) }
    // grayscale (WRITE_SECURE_SETTINGS) needs the guided wizard; the overlay permission does not
    var needsGrayscalePermission by remember { mutableStateOf(true) }
    LaunchedEffect(lifecycleState) {
        visible = homeViewModel.resolvePendingOnboardingSetup()
        needsGrayscalePermission = homeViewModel.pendingSetupNeedsGrayscalePermission()
    }
    if (!visible) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (needsGrayscalePermission) {
                    Icons.Default.InvertColors
                } else {
                    Icons.Default.Layers
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = stringResource(id = R.string.home_finishSetup_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        id = if (needsGrayscalePermission) {
                            R.string.home_finishSetup_message
                        } else {
                            R.string.home_finishSetup_message_overlay
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = {
                if (needsGrayscalePermission) {
                    navViewModel.openRoute(NavigationRoutes.ShizukuSetup)
                } else {
                    NavigationUtil.openOverlayPermissionsSettings(context)
                }
            }) {
                Text(stringResource(id = R.string.home_finishSetup_action))
            }
        }
    }
}
