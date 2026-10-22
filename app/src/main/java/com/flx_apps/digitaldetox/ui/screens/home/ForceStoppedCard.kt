package com.flx_apps.digitaldetox.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.ForceStopRecovery
import com.flx_apps.digitaldetox.util.BatteryOptimizationHelper
import com.flx_apps.digitaldetox.util.observeAsState

/**
 * Shown while the accessibility service is off because DetoxDroid was force-stopped rather than
 * stopped by the user ([ForceStopRecovery.wasForceStopped]). Without it the home screen only says
 * "Stopped", and the user is left to believe DetoxDroid keeps losing a permission. Goes away once
 * the service runs again, or for good when the user dismisses it and leaves DetoxDroid off.
 */
@Composable
fun ForceStoppedCard() {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    val detoxDroidState = DetoxDroidAccessibilityService.state.collectAsState().value
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycleState, detoxDroidState) {
        visible = ForceStopRecovery.wasForceStopped(context)
    }
    if (!visible) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(id = R.string.reliability_forceStopped_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.reliability_forceStopped_message),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    ForceStopRecovery.recordRunning(context, false)
                    visible = false
                }) {
                    Text(stringResource(id = R.string.reliability_forceStopped_dismiss))
                }
                TextButton(
                    onClick = { BatteryOptimizationHelper.openDontKillMyAppGuide(context) }
                ) {
                    Text(stringResource(id = R.string.reliability_forceStopped_howTo))
                }
            }
        }
    }
}
