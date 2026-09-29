package com.flx_apps.digitaldetox.ui.screens.feature.emergency_brake

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.widgets.AdvancedSettings
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SectionHeader
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.formatDurationMsCompact
import java.util.concurrent.TimeUnit

/**
 * What the screen shows of the running brake. Read in one go whenever
 * [EmergencyBrakeFeature.stateToken] moves, so the parts of the screen cannot disagree.
 */
private data class BrakeState(
    val braking: Boolean, val endsAt: String, val endRequested: Boolean, val canUndo: Boolean
)

@Composable
private fun rememberBrakeState(): BrakeState {
    val stateToken by EmergencyBrakeFeature.stateToken.collectAsState()
    return remember(stateToken) {
        val braking = EmergencyBrakeFeature.isBraking()
        BrakeState(
            braking = braking,
            endsAt = if (braking) EmergencyBrakeFeature.endsAtText() else "",
            endRequested = EmergencyBrakeFeature.isEndRequested(),
            canUndo = EmergencyBrakeFeature.canUndo()
        )
    }
}

/**
 * The settings section for the emergency brake: the brake itself on top, what it is made of below.
 */
@Composable
fun EmergencyBrakeFeatureSettingsSection(
    viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()
) {
    when (viewModel.visibleDialog.collectAsState().value) {
        EmergencyBrakeDialog.END_EARLY -> EndEarlyDialog()
        EmergencyBrakeDialog.AFFECTED_FEATURES -> AffectedFeaturesDialog()
        EmergencyBrakeDialog.AUTO_PULL_BREAKS -> AutoPullBreaksDialog()
        EmergencyBrakeDialog.AUTO_PULL_WINDOW -> AutoPullWindowDialog()
        EmergencyBrakeDialog.AUTO_PULL_DURATION -> AutoPullDurationDialog()
        EmergencyBrakeDialog.NONE -> {}
    }
    BrakeCard()
    // before Android 13 an app cannot offer its tile, it can only be dragged in by hand
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SectionHeader(title = stringResource(id = R.string.feature_brake_ways))
        SettingsGroup { PullFromQuickSettingsTile() }
    }
    SectionHeader(title = stringResource(id = R.string.feature_brake_autoPull_header))
    SettingsGroup {
        AutoPullTile()
        if (viewModel.autoPull.collectAsState().value) {
            AutoPullBreaksTile()
            AutoPullWindowTile()
            AutoPullDurationTile()
        }
    }
    AdvancedSettings(
        summary = stringResource(id = R.string.feature_pause_affectedFeatures),
        initiallyExpanded = viewModel.exemptFeatureIds.collectAsState().value.isNotEmpty()
    ) {
        AffectedFeaturesTile()
    }
}

/**
 * The brake: how long, and the button that pulls it. While one runs it says until when, and
 * offers what is left to do about it. None of this is held back by locked settings, which a
 * running brake brings with it: pulling and extending only tighten, and ending early is the wait
 * it is meant to be.
 */
@Composable
private fun BrakeCard(
    viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    val context = LocalContext.current
    val brake = rememberBrakeState()
    val activated = featureViewModel.featureIsActive.collectAsState().value
    val running =
        DetoxDroidAccessibilityService.state.collectAsState().value != DetoxDroidState.Inactive
    val duration = viewModel.duration.collectAsState().value
    IconCard(icon = Icons.Default.PanTool) {
        Text(
            text = when {
                brake.endRequested -> stringResource(R.string.feature_brake_status_ending, brake.endsAt)
                brake.braking -> stringResource(R.string.feature_brake_status_on, brake.endsAt)
                !activated -> stringResource(R.string.feature_brake_status_notActivated)
                !running -> stringResource(R.string.feature_brake_status_notRunning)
                else -> stringResource(R.string.feature_brake_status_off)
            }, style = MaterialTheme.typography.bodyMedium
        )
        ChipFlowRow(modifier = Modifier.padding(top = 12.dp)) {
            EmergencyBrakeFeature.DurationsInMinutes.forEach { minutes ->
                FilterChip(
                    selected = minutes == duration,
                    onClick = { viewModel.setDuration(minutes) },
                    enabled = activated && running,
                    label = {
                        Text(
                            formatDurationMsCompact(
                                context, TimeUnit.MINUTES.toMillis(minutes.toLong())
                            )
                        )
                    })
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                brake.canUndo -> TextButton(onClick = { viewModel.undo() }) {
                    Text(stringResource(R.string.feature_brake_undo))
                }

                brake.endRequested -> TextButton(onClick = { viewModel.withdrawEndRequest() }) {
                    Text(stringResource(R.string.feature_brake_keepOn))
                }

                brake.braking -> TextButton(onClick = {
                    viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.END_EARLY)
                }) {
                    Text(stringResource(R.string.feature_brake_endEarly))
                }
            }
            if (brake.braking) {
                OutlinedButton(onClick = { viewModel.pull() }) {
                    Text(stringResource(R.string.feature_brake_extend))
                }
            } else {
                Button(onClick = { viewModel.pull() }, enabled = activated && running) {
                    Text(stringResource(R.string.feature_brake_pull))
                }
            }
        }
    }
}

/**
 * Asks before the wait starts. Dismissing it is a plain cancel.
 */
@Composable
private fun EndEarlyDialog(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val waitMinutes = TimeUnit.MILLISECONDS.toMinutes(EmergencyBrakeFeature.EndWaitMs).toInt()
    AlertDialog(
        onDismissRequest = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) },
        title = { Text(text = stringResource(id = R.string.feature_brake_endEarly)) },
        text = {
            Text(text = stringResource(id = R.string.feature_brake_endEarly_message, waitMinutes))
        },
        confirmButton = {
            TextButton(onClick = { viewModel.requestEnd() }) {
                Text(text = stringResource(id = R.string.feature_brake_endEarly_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) }) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
        })
}

/**
 * Offers the brake tile to the Quick Settings, so nobody has to find it in the tile editor.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PullFromQuickSettingsTile(
    viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()
) {
    SimpleListTile(
        leadingIcon = Icons.Default.GridView,
        titleText = stringResource(id = R.string.feature_pause_fromQuickSettings),
        subtitleText = stringResource(id = R.string.feature_brake_fromQuickSettings_description),
        allowClickWhenLocked = true,
        onClick = { viewModel.requestAddQuickSettingsTile() })
}

/**
 * @see EmergencyBrakeFeature.autoPull
 */
@Composable
private fun AutoPullTile(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    SimpleListTile(
        leadingIcon = Icons.Default.AutoMode,
        titleText = stringResource(id = R.string.feature_brake_autoPull),
        subtitleText = stringResource(
            id = R.string.feature_brake_autoPull_description,
            viewModel.autoPullBreaks.collectAsState().value,
            viewModel.autoPullWindow.collectAsState().value,
            formatDurationMsCompact(
                context,
                TimeUnit.MINUTES.toMillis(viewModel.autoPullDuration.collectAsState().value.toLong())
            )
        ),
        checked = viewModel.autoPull.collectAsState().value,
        onCheckedChange = { viewModel.toggleAutoPull() },
        onClick = { viewModel.toggleAutoPull() })
}

@Composable
private fun AutoPullBreaksTile(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        leadingIcon = Icons.Default.Repeat,
        titleText = stringResource(id = R.string.feature_brake_autoPull_breaks),
        subtitleText = stringResource(id = R.string.feature_brake_autoPull_breaks_description),
        trailing = { Text(viewModel.autoPullBreaks.collectAsState().value.toString()) },
        onClick = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.AUTO_PULL_BREAKS) })
}

@Composable
private fun AutoPullWindowTile(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        leadingIcon = Icons.Default.Timelapse,
        titleText = stringResource(id = R.string.feature_brake_autoPull_window),
        subtitleText = stringResource(id = R.string.feature_brake_autoPull_window_description),
        trailing = {
            Text(
                stringResource(
                    id = R.string.time_minutes, viewModel.autoPullWindow.collectAsState().value
                )
            )
        },
        onClick = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.AUTO_PULL_WINDOW) })
}

@Composable
private fun AutoPullDurationTile(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    SimpleListTile(
        leadingIcon = Icons.Default.HourglassBottom,
        titleText = stringResource(id = R.string.feature_brake_autoPull_duration),
        subtitleText = stringResource(id = R.string.feature_brake_autoPull_duration_description),
        trailing = {
            Text(
                formatDurationMsCompact(
                    context,
                    TimeUnit.MINUTES.toMillis(viewModel.autoPullDuration.collectAsState().value.toLong())
                )
            )
        },
        onClick = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.AUTO_PULL_DURATION) })
}

@Composable
private fun AutoPullBreaksDialog(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    NumberPickerDialog(
        titleText = stringResource(id = R.string.feature_brake_autoPull_breaks),
        initialValue = viewModel.autoPullBreaks.collectAsState().value,
        onValueSelected = { viewModel.setAutoPullBreaks(it) },
        onDismissRequest = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) },
        range = 2..10
    )
}

@Composable
private fun AutoPullWindowDialog(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    NumberPickerDialog(
        titleText = stringResource(id = R.string.feature_brake_autoPull_window),
        initialValue = viewModel.autoPullWindow.collectAsState().value,
        onValueSelected = { viewModel.setAutoPullWindow(it) },
        onDismissRequest = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) },
        label = { context.getString(R.string.time_minutes, it) },
        range = 10..120 step 5
    )
}

@Composable
private fun AutoPullDurationDialog(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    NumberPickerDialog(
        titleText = stringResource(id = R.string.feature_brake_autoPull_duration),
        initialValue = viewModel.autoPullDuration.collectAsState().value,
        onValueSelected = { viewModel.setAutoPullDuration(it) },
        onDismissRequest = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) },
        label = { formatDurationMsCompact(context, TimeUnit.MINUTES.toMillis(it.toLong())) },
        range = 15..240 step 15
    )
}

/**
 * A tile that opens a dialog for choosing which features a brake tightens.
 * @see EmergencyBrakeFeature.brakeExemptFeatureIds
 */
@Composable
private fun AffectedFeaturesTile(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val exempt = viewModel.exemptFeatureIds.collectAsState().value
    val total = viewModel.brakableFeatures.size
    val affected = total - viewModel.brakableFeatures.count { it.id in exempt }
    SimpleListTile(
        leadingIcon = Icons.Default.Tune,
        titleText = stringResource(id = R.string.feature_pause_affectedFeatures),
        subtitleText = if (affected == total) {
            stringResource(id = R.string.feature_pause_affectedFeatures_all)
        } else {
            stringResource(id = R.string.feature_pause_affectedFeatures_count, affected, total)
        },
        onClick = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.AFFECTED_FEATURES) },
    )
}

/**
 * A dialog listing every feature a brake can tighten with a checkbox. Unchecked ones are left as
 * they are while a brake runs.
 */
@Composable
private fun AffectedFeaturesDialog(viewModel: EmergencyBrakeFeatureSettingsViewModel = viewModel()) {
    val exempt = viewModel.exemptFeatureIds.collectAsState().value
    AlertDialog(
        onDismissRequest = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) },
        title = { Text(text = stringResource(id = R.string.feature_pause_affectedFeatures)) },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.feature_brake_affectedFeatures_dialogHint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                viewModel.brakableFeatures.forEach { feature ->
                    val affected = feature.id !in exempt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setFeatureAffectedByBrake(feature.id, !affected)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = affected, onCheckedChange = {
                            viewModel.setFeatureAffectedByBrake(feature.id, it)
                        })
                        Text(
                            text = stringResource(id = feature.texts.title),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.setVisibilityOfDialog(EmergencyBrakeDialog.NONE) }) {
                Text(text = stringResource(id = R.string.action_close))
            }
        },
    )
}
