package com.flx_apps.digitaldetox.ui.screens.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.PasswordLockGate
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.SettingsLockBannerIfNeeded
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The colors rules are told apart by, each with the color that goes on top of it, handed out in
 * the order the rules are listed.
 */
@Composable
private fun ruleAccents(): List<Pair<Color, Color>> = MaterialTheme.colorScheme.run {
    listOf(primary to onPrimary, tertiary to onTertiary, secondary to onSecondary)
}

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
    PasswordLockGate(featureId = featureId, showBanner = false) {
        val rules = scheduleViewModel.rules.collectAsState().value
        // listed by the time they start, which also keeps each rule on the same color
        val orderedRules = remember(rules) {
            rules.toList().sortedWith(
                compareBy({ it.second.start }, { it.second.daysOfWeek.minOrNull() })
            )
        }
        val accents = ruleAccents()
        val accentOf = { index: Int -> accents[index % accents.size] }
        val editedRule = scheduleViewModel.dialogRule.collectAsState(null).value
        val settingsLocked = LocalSettingsLocked.current
        Scaffold(topBar = {
            TopAppBar(navigationIcon = { AppBarBackButton() }, title = {
                Text(text = stringResource(id = R.string.feature_settings_schedule))
            })
        }) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.padding(padding)
            ) {
                item {
                    SettingsLockBannerIfNeeded(featureId = featureId)
                    WeekScheduleOverview(
                        rules = orderedRules.mapIndexed { index, (_, rule) ->
                            rule to accentOf(index).first
                        },
                        showStatus = scheduleViewModel.featureIsActive.collectAsState().value,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    if (orderedRules.isEmpty()) {
                        Text(
                            text = stringResource(
                                id = R.string.feature_settings_schedule_description
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                    } else {
                        SettingsGroup {
                            orderedRules.forEachIndexed { index, ruleItem ->
                                ScheduleRuleRow(
                                    rule = ruleItem.second,
                                    accent = accentOf(index).first,
                                    enabled = !settingsLocked,
                                    onClick = { scheduleViewModel.showBottomSheet(ruleItem) }
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        enabled = !settingsLocked,
                        onClick = { scheduleViewModel.showBottomSheet() },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(id = R.string.feature_settings_schedule_add))
                    }
                }
            }
            editedRule?.let { ruleItem ->
                val index = orderedRules.indexOfFirst { it.first == ruleItem.first }
                ScheduleRuleSheet(
                    ruleItem = ruleItem,
                    accent = accentOf(if (index >= 0) index else orderedRules.size),
                    viewModel = scheduleViewModel
                )
            }
        }
    }
}

/**
 * A rule in the list: its times, and its days as letters in its color, next to the color it has
 * in the week above.
 */
@Composable
private fun ScheduleRuleRow(
    rule: FeatureScheduleRule, accent: Color, enabled: Boolean, onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 40.dp)
                .background(accent, CircleShape)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = timeSpanText(context, rule.start, rule.end)
                    .replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.titleMedium
            )
            WeekDayLetters(
                days = rule.daysOfWeek,
                accent = accent,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clearAndSetSemantics {
                        contentDescription = weekDaysText(context, rule.daysOfWeek)
                    }
            )
        }
    }
}

/** The week as a row of initials, the given [days] picked out in [accent]. */
@Composable
private fun WeekDayLetters(days: List<DayOfWeek>, accent: Color, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    val shownDays = days.ifEmpty { DayOfWeek.entries }
    Row(modifier = modifier) {
        orderedWeekDays(locale).forEach { day ->
            val included = day in shownDays
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (included) FontWeight.Bold else FontWeight.Normal,
                color = if (included) {
                    accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.width(18.dp)
            )
        }
    }
}

/** The given weekdays as text: "Every day" for all of them, or none, which means the same. */
fun weekDaysText(context: Context, daysOfWeek: List<DayOfWeek>): String {
    if (daysOfWeek.isEmpty() || daysOfWeek.toSet().size == 7) {
        return context.getString(R.string.feature_settings_schedule_everyDay)
    }
    return dayRangesText(daysOfWeek, Locale.getDefault())
}

/**
 * The given days as short names in the order the [locale]'s week runs in, with three or more days
 * in a row shortened to a range like "Mon-Fri". The week goes round, so Saturday and Sunday stay
 * together even where the week starts on Sunday.
 */
internal fun dayRangesText(days: Collection<DayOfWeek>, locale: Locale): String {
    val runs = mutableListOf<List<DayOfWeek>>()
    orderedWeekDays(locale).filter { it in days }.forEach { day ->
        val run = runs.lastOrNull()
        if (run != null && run.last().plus(1) == day) {
            runs[runs.lastIndex] = run + day
        } else {
            runs += listOf(day)
        }
    }
    // a run up to the end of the week carries on into one from its start
    if (runs.size > 1 && runs.last().last().plus(1) == runs.first().first()) {
        runs[0] = runs.removeAt(runs.lastIndex) + runs.first()
    }
    val name = { day: DayOfWeek -> day.getDisplayName(TextStyle.SHORT, locale) }
    return runs.joinToString(separator = ", ") { run ->
        if (run.size >= 3) {
            "${name(run.first())}-${name(run.last())}"
        } else {
            run.joinToString(separator = ", ") { name(it) }
        }
    }
}

/**
 * Returns a string that contains the given time range. If the start and end time are equal, the
 * string "all day" is returned. Otherwise, the start and end time are formatted and separated by a
 * dash. If the start time is after the end time, the string "next day" is appended.
 */
fun timeSpanText(context: Context, start: LocalTime, end: LocalTime): String {
    if (start == end) return context.getString(R.string.feature_settings_schedule_allDay)
    val dtf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val span = "${dtf.format(start)} - ${dtf.format(end)}"
    return if (start.isAfter(end)) {
        "$span ${context.getString(R.string.feature_settings_schedule_nextDay)}"
    } else {
        span
    }
}
