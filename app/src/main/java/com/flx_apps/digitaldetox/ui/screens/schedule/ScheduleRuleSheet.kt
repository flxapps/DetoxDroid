package com.flx_apps.digitaldetox.ui.screens.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The sheet to add or edit a rule: the time window on a dial, whether it takes the whole day, and
 * the days it applies to. Saving is the one filled button; deleting, offered for existing rules
 * only, sits apart from it in the error color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleRuleSheet(
    ruleItem: ScheduleRuleItem, accent: Pair<Color, Color>, viewModel: ScheduleViewModel
) {
    val rule = ruleItem.second
    val isNewRule = ruleItem.first == ScheduleViewModel.NEW_RULE_ID
    // equal times mean the whole day, see FeatureScheduleRule.isActive
    val allDay = rule.start == rule.end
    // what switching "all day" off goes back to
    var lastWindow by remember(ruleItem.first) {
        mutableStateOf(if (allDay) ScheduleViewModel.NewRuleWindow else rule.start to rule.end)
    }
    var pickingTime by remember { mutableStateOf<TimeEnd?>(null) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.hideBottomSheet() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            Text(
                text = stringResource(
                    id = if (isNewRule) {
                        R.string.feature_settings_schedule_newRule
                    } else {
                        R.string.feature_settings_schedule_editRule
                    }
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            AnimatedVisibility(visible = !allDay) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    TimeField(
                        label = stringResource(id = R.string.feature_settings_schedule_start),
                        time = rule.start,
                        icon = Icons.Rounded.PlayArrow,
                        accent = accent.first,
                        onClick = { pickingTime = TimeEnd.Start },
                        modifier = Modifier.weight(1f)
                    )
                    TimeField(
                        label = stringResource(id = R.string.feature_settings_schedule_end),
                        time = rule.end,
                        icon = Icons.Rounded.Stop,
                        accent = accent.first,
                        caption = if (rule.end < rule.start) {
                            stringResource(id = R.string.feature_settings_schedule_nextDay)
                        } else {
                            null
                        },
                        onClick = { pickingTime = TimeEnd.End },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TimeRangeDial(
                start = rule.start,
                end = rule.end,
                allDay = allDay,
                accent = accent.first,
                onAccent = accent.second,
                onChange = { start, end ->
                    lastWindow = start to end
                    viewModel.updateBottomSheet(start = start, end = end)
                },
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .toggleable(value = allDay, role = Role.Switch) { toAllDay ->
                        val (start, end) = if (toAllDay) {
                            LocalTime.MIDNIGHT to LocalTime.MIDNIGHT
                        } else {
                            lastWindow
                        }
                        viewModel.updateBottomSheet(start = start, end = end)
                    }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.feature_settings_schedule_allDay)
                        .replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = allDay, onCheckedChange = null)
            }
            Text(
                text = stringResource(id = R.string.feature_settings_schedule_weekdays),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
            DayToggles(
                selectedDays = rule.daysOfWeek,
                accent = accent,
                onSelectedDaysChange = { viewModel.updateBottomSheet(daysOfWeek = it) }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            ) {
                if (!isNewRule) {
                    TextButton(
                        onClick = { viewModel.onDeleteClick() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(id = R.string.action_delete))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.onSaveClick() }) {
                    Text(text = stringResource(id = R.string.action_save))
                }
            }
        }
    }

    pickingTime?.let { timeEnd ->
        TimePickerDialog(
            title = stringResource(
                id = if (timeEnd == TimeEnd.Start) {
                    R.string.feature_settings_schedule_start
                } else {
                    R.string.feature_settings_schedule_end
                }
            ),
            initialTime = if (timeEnd == TimeEnd.Start) rule.start else rule.end,
            onTimePicked = { time ->
                val (start, end) = if (timeEnd == TimeEnd.Start) {
                    time to rule.end
                } else {
                    rule.start to time
                }
                // picking the same time for both ends would make the rule all day, which is what
                // the switch is for; the times stay as they were instead
                if (start != end) {
                    lastWindow = start to end
                    viewModel.updateBottomSheet(start = start, end = end)
                }
            },
            onDismissRequest = { pickingTime = null }
        )
    }
}

private enum class TimeEnd { Start, End }

/**
 * One end of the time window, large, on a tonal field that opens a time picker for the exact
 * minute. The [caption] goes next to the label.
 */
@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f)
            )
            caption?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
        Text(
            text = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialTime: LocalTime,
    onTimePicked: (LocalTime) -> Unit,
    onDismissRequest: () -> Unit
) {
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onTimePicked(LocalTime.of(state.hour, state.minute))
                onDismissRequest()
            }) {
                Text(text = stringResource(id = R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
        }
    )
}

/**
 * A round toggle per day of the week, the way alarm clocks offer them. No days at all stands for
 * every day (see [FeatureScheduleRule.isActive]), so that is shown as all of them, and the last
 * selected day cannot be switched off.
 */
@Composable
private fun DayToggles(
    selectedDays: List<DayOfWeek>,
    accent: Pair<Color, Color>,
    onSelectedDaysChange: (List<DayOfWeek>) -> Unit
) {
    val locale = Locale.getDefault()
    val effectiveDays = selectedDays.ifEmpty { DayOfWeek.entries }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        orderedWeekDays(locale).forEach { day ->
            val selected = day in effectiveDays
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent.first else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            accent.first
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
                    .toggleable(value = selected, role = Role.Checkbox) {
                        val days = if (selected) effectiveDays - day else effectiveDays + day
                        if (days.isNotEmpty()) onSelectedDaysChange(days.sorted())
                    }
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, locale) }
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) accent.second else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
