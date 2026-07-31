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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.system_integration.ReliabilitySettings
import com.flx_apps.digitaldetox.util.BatteryOptimizationHelper
import com.flx_apps.digitaldetox.util.observeAsState

/**
 * Follow-up nudge to also grant the battery-optimization exemption. Only shown to users who opted
 * into keeping DetoxDroid alive ([ReliabilitySettings.keepServiceAliveEnabled]) but are not yet
 * exempt - the keepalive alone does not defeat aggressive background limits. Dismissible for the
 * session; disappears once the exemption is granted.
 */
@Composable
fun BatteryOptimizationCard() {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycleState, dismissed) {
        visible =
            !dismissed && ReliabilitySettings.keepServiceAliveEnabled && AccessibilityServiceController.isEnabledInSettings(
                context
            ) && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
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
                        text = stringResource(id = R.string.home_batteryOptimization_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.home_batteryOptimization_message),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { BatteryOptimizationHelper.openDontKillMyAppGuide(context) }) {
                    Text(stringResource(id = R.string.home_batteryOptimization_why))
                }
                TextButton(onClick = { dismissed = true }) {
                    Text(stringResource(id = R.string.home_batteryOptimization_dismiss))
                }
                TextButton(onClick = {
                    BatteryOptimizationHelper.openBatteryOptimizationSettings(
                        context
                    )
                }) {
                    Text(stringResource(id = R.string.home_batteryOptimization_action))
                }
            }
        }
    }
}
