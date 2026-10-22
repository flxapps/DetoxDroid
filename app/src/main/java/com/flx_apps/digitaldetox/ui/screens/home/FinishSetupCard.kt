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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeatureId
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.Rules
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.shizuku_setup.ShizukuSetupGoal
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.hasWriteSecureSettings
import com.flx_apps.digitaldetox.util.observeAsState

/**
 * Reminder card shown while a switched-on group has a step that is waiting for a permission: gray
 * for WRITE_SECURE_SETTINGS or the screen filter, the wait, the lock and the scroll breaks for the
 * overlay permission. Re-evaluated whenever the user returns to the home screen; the step takes
 * hold by itself once the permission is there, and the card goes.
 */
@Composable
fun FinishSetupCard(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    val groupsChanged by RuleGroups.changes.collectAsState()
    val missing = remember(lifecycleState, groupsChanged) { Rules.stepsMissingPermission(context) }
    if (missing.isEmpty()) return
    // gray with the permission in hand only waits for one of its effects to be switched on
    val canWriteSecureSettings = remember(lifecycleState) { context.hasWriteSecureSettings() }
    val waitsForGray = Step.GRAY in missing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (waitsForGray) Icons.Default.InvertColors else Icons.Default.Layers,
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
                        id = when {
                            waitsForGray && canWriteSecureSettings -> R.string.home_finishSetup_message_grayEffects
                            waitsForGray -> R.string.home_finishSetup_message
                            else -> R.string.home_finishSetup_message_overlay
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = {
                when {
                    waitsForGray && canWriteSecureSettings ->
                        navViewModel.openRoute(NavigationRoutes.ManageFeature(GrayscaleAppsFeatureId))

                    waitsForGray -> navViewModel.openRoute(
                        NavigationRoutes.ShizukuSetup(ShizukuSetupGoal.GRAYSCALE)
                    )
                    else -> NavigationUtil.openOverlayPermissionsSettings(context)
                }
            }) {
                Text(stringResource(id = R.string.home_finishSetup_action))
            }
        }
    }
}
