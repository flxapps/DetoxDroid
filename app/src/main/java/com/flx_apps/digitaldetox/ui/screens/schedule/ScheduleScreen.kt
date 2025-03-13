package com.flx_apps.digitaldetox.ui.screens.schedule

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.Center
import com.flx_apps.digitaldetox.ui.widgets.InfoCard
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The schedule screen for a feature. Allows the user to set rules for when the feature should be
 * active or inactive. A rule consists of a time range and a day of the week. The feature will be
 * active during the time range on the specified day of the week. If multiple rules apply, the
 * feature will be active if at least one of them is active.
 * @param featureId The ID of the feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScheduleScreen(
    featureId: FeatureId,
    scheduleViewModel: ScheduleViewModel = ScheduleViewModel.withFeatureId(featureId)
) {
    val bottomSheetVisible = scheduleViewModel.dialogRule.collectAsState(null).value != null
    Scaffold(topBar = {
        TopAppBar(navigationIcon = { AppBarBackButton() }, title = {
            Text(text = stringResource(id = R.string.feature_settings_schedule))
        })
    }, floatingActionButton = {
        FloatingActionButton(onClick = {
            scheduleViewModel.showBottomSheet()
        }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
        }
    }) {
        Box(modifier = Modifier.padding(paddingValues = it)) {
            ScheduleRulesList(rules = scheduleViewModel.rules.collectAsState().value)
        }
        if (bottomSheetVisible) {
            FeatureScheduleRuleBottomSheet()
        }
    }
}

/**
 * The list of schedule rules for a feature.
 */
@Composable
fun ScheduleRulesList(rules: Map<ScheduleRuleId, FeatureScheduleRule>) {
    InfoCard(infoText = stringResource(id = R.string.feature_settings_schedule_description))
    if (rules.isEmpty()) {
        Center {
            Text(
                text = stringResource(id = R.string.feature_settings_schedule_empty),
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn {
            item {
                InfoCard(infoText = stringResource(id = R.string.feature_settings_schedule_description))
            }
            items(items = rules.toList()) { ruleItem ->
                ScheduleRuleListItem(ruleItem)
            }
        }
    }
}

/**
 * The UI for a single [ScheduleRuleItem] in the list of rules.
 */
@Composable
fun ScheduleRuleListItem(
    ruleItem: ScheduleRuleItem, scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val rule = ruleItem.second
    SimpleListTile(titleText = weekDaysText(LocalContext.current, rule.daysOfWeek),
        subtitleText = timeSpanText(LocalContext.current, rule.start, rule.end),
        onClick = {
            scheduleViewModel.showBottomSheet(ruleItem)
        })
}

/**
 * The bottom sheet that is shown when the user wants to add or edit a rule. It allows the user to
 * select the days of the week and the time range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScheduleRuleBottomSheet(
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val ruleItem = viewModel.dialogRule.collectAsState(null).value ?: return
    val rule = ruleItem.second
    val dtf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val is24HourFormat = android.text.format.DateFormat.is24HourFormat(context)

    ModalBottomSheet(onDismissRequest = {
        viewModel.hideBottomSheet()
    }, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(bottom = 64.dp)) {
            SimpleListTile(titleText = stringResource(id = R.string.feature_settings_schedule_weekdays),
                subtitleText = weekDaysText(context, rule.daysOfWeek),
                onClick = {
                    // show dialog to select weekdays
                    val choices =
                        DayOfWeek.values().map { rule.daysOfWeek.contains(it) }.toBooleanArray()
                    showWeekDayPickerDialog(context, choices) {
                        viewModel.updateBottomSheet(daysOfWeek = DayOfWeek.values()
                            .filterIndexed { index, _ ->
                                choices[index]
                            })
                    }
                })
            SimpleListTile(titleText = stringResource(id = R.string.feature_settings_schedule_from),
                subtitleText = dtf.format(rule.start),
                onClick = {
                    // show time picker dialog to select start time
                    TimePickerDialog(context, { _, hour, minute ->
                        viewModel.updateBottomSheet(start = LocalTime.of(hour, minute))
                    }, rule.start.hour, rule.start.minute, is24HourFormat).show()
                })
            SimpleListTile(titleText = stringResource(id = R.string.feature_settings_schedule_to),
                subtitleText = dtf.format(rule.end),
                onClick = {
                    // show time picker dialog to select end time
                    TimePickerDialog(context, { _, hour, minute ->
                        viewModel.updateBottomSheet(end = LocalTime.of(hour, minute))
                    }, rule.end.hour, rule.end.minute, is24HourFormat).show()
                })
            Row {
                if (ruleItem.first != -1) {
                    TextButton(modifier = Modifier.weight(1f), onClick = {
                        viewModel.onDeleteClick()
                    }) {
                        Text(text = stringResource(id = R.string.action_delete))
                    }
                }
                TextButton(modifier = Modifier.weight(1f), onClick = {
                    viewModel.onSaveClick()
                }) {
                    Text(text = stringResource(id = R.string.action_save))
                }
            }
        }
    }
}

/**
 * Shows a dialog that allows the user to select the days of the week.
 * @param context The context.
 * @param choices The currently selected days of the week. The index of the boolean array
 * corresponds to the index of the day of the week in [DayOfWeek.values].
 * @param onSaveClick A callback that is called when the user clicks on the save button. It
 * receives the updated choices as a parameter.
 */
private fun showWeekDayPickerDialog(
    context: Context, choices: BooleanArray, onSaveClick: (BooleanArray) -> Unit
) {
    AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.feature_settings_schedule_weekdays))
        .setMultiChoiceItems(
            DayOfWeek.values().map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
                .toTypedArray(), choices
        ) { _, which, isChecked ->
            choices[which] = isChecked
        }.setPositiveButton(context.getString(R.string.action_save)) { _, _ ->
            onSaveClick(choices)
        }.setNegativeButton(context.getString(R.string.action_cancel), null).show()
}

/**
 * Returns a string that contains the names of the given weekdays. If all weekdays are given, the
 * string "Every day" is returned. Otherwise, the names of the weekdays are returned, separated by
 * commas.
 */
fun weekDaysText(context: Context, daysOfWeek: List<DayOfWeek>): String {
    return if (daysOfWeek.isEmpty() || daysOfWeek.size == 7) {
        context.getString(R.string.feature_settings_schedule_everyDay)
    } else {
        daysOfWeek.joinToString(separator = ", ") {
            it.getDisplayName(
                TextStyle.SHORT, Locale.getDefault()
            )
        }
    }
}

/**
 * Returns a string that contains the given time range. If the start and end time are equal, the
 * string "All day" is returned. Otherwise, the start and end time are formatted and separated by a
 * dash. If the start time is after the end time, the string "next day" is appended.
 */
fun timeSpanText(context: Context, start: LocalTime, end: LocalTime): String {
    if (start == end) return context.getString(R.string.feature_settings_schedule_allDay)
    val dtf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val nextDayString = if (start.isAfter(end)) {
        context.getString(R.string.feature_settings_schedule__nextDay)
    } else {
        ""
    }
    return "${dtf.format(start)} - ${dtf.format(end)} $nextDayString"
}