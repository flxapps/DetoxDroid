package com.flx_apps.digitaldetox.ui.screens.rule_groups

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.BudgetWindow
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.PasswordLockGate
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.SettingsLockBannerIfNeeded
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow
import com.flx_apps.digitaldetox.ui.widgets.OptionChip
import com.flx_apps.digitaldetox.util.toHrMinString
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * How much a group gets: the amount with presets and a stepper, per day or per hour, read against
 * the last seven days of the group's apps. Saved with the button at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBudgetScreen(
    groupId: GroupId,
    viewModel: GroupBudgetViewModel = GroupBudgetViewModel.withGroupId(groupId),
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
) {
    val context = LocalContext.current
    val minutes by viewModel.minutes.collectAsState()
    val period by viewModel.period.collectAsState()
    val lastDays by viewModel.lastDays.collectAsState()
    val amountText = if (minutes > 0) {
        GroupTexts.duration(context, minutes * 60_000L)
    } else {
        stringResource(id = R.string.ruleGroups_budget_noLimit)
    }
    PasswordLockGate(featureId = groupId, showBanner = false) {
        val locked = LocalSettingsLocked.current
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { AppBarBackButton() },
                    title = { Text(stringResource(id = R.string.ruleGroups_budget_title)) }
                )
            },
            bottomBar = {
                Button(
                    enabled = !locked,
                    onClick = {
                        viewModel.save()
                        navViewModel.onBackPress()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                ) {
                    Text(text = stringResource(id = R.string.ruleGroups_budget_use, amountText))
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsLockBannerIfNeeded(featureId = groupId)
                GroupCard {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        BudgetPeriod.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = option == period,
                                enabled = !locked,
                                onClick = { viewModel.setPeriod(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, BudgetPeriod.entries.size)
                            ) {
                                Text(
                                    stringResource(
                                        id = when (option) {
                                            BudgetPeriod.DAY -> R.string.ruleGroups_budget_period_day
                                            BudgetPeriod.HOUR -> R.string.ruleGroups_budget_period_hour
                                        }
                                    )
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { viewModel.step(-1) },
                            enabled = !locked && minutes > 0,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = stringResource(id = R.string.ruleGroups_budget_less)
                            )
                        }
                        Text(
                            text = amountText,
                            style = if (minutes > 0) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { viewModel.step(1) },
                            enabled = !locked,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.ruleGroups_budget_more)
                            )
                        }
                    }
                    ChipFlowRow(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        GroupBudgetViewModel.Presets.forEach { preset ->
                            OptionChip(
                                label = GroupTexts.duration(context, preset * 60_000L),
                                selected = minutes == preset,
                                enabled = !locked,
                                onClick = { viewModel.setMinutes(preset) }
                            )
                        }
                        OptionChip(
                            label = stringResource(id = R.string.ruleGroups_budget_noLimit),
                            selected = minutes == 0,
                            enabled = !locked,
                            onClick = { viewModel.setMinutes(0) }
                        )
                    }
                    val pastMidnight = remember {
                        BudgetWindow.runsPastMidnight(viewModel.group?.scheduleRules.orEmpty())
                    }
                    if (period == BudgetPeriod.HOUR || minutes > 0 && pastMidnight) {
                        Text(
                            text = stringResource(
                                id = if (period == BudgetPeriod.HOUR) {
                                    R.string.ruleGroups_budget_perHour_hint
                                } else {
                                    R.string.ruleGroups_budget_perDay_pastMidnight_hint
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                LastDaysCard(days = lastDays, budgetMinutes = minutes, period = period)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** The group's last seven days as bars, the budget drawn across them as a dashed line. */
@Composable
private fun LastDaysCard(days: List<DayUsage>?, budgetMinutes: Int, period: BudgetPeriod) {
    val context = LocalContext.current
    if (days.isNullOrEmpty()) return
    val locale = Locale.getDefault()
    val colors = MaterialTheme.colorScheme
    val averageMs = if (days.isNotEmpty()) days.sumOf { it.ms } / days.size else 0L
    val budgetMs = budgetMinutes * 60_000L
    val maxMs = maxOf(days.maxOfOrNull { it.ms } ?: 0L, budgetMs, 60_000L).toFloat()
    val barColor = colors.outlineVariant
    val lineColor = colors.primary
    GroupCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(id = R.string.ruleGroups_budget_lastDays),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    id = R.string.ruleGroups_budget_lastDays_average,
                    GroupTexts.duration(context, averageMs)
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gap = 10.dp.toPx()
                val barWidth = (size.width - gap * (days.size - 1)) / days.size
                days.forEachIndexed { index, day ->
                    val barHeight = (day.ms / maxMs * size.height).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
                if (budgetMs > 0L && period == BudgetPeriod.DAY) {
                    val y = size.height - budgetMs / maxMs * size.height
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                }
            }
        }
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)) {
            days.forEach { day ->
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (period == BudgetPeriod.DAY && budgetMs > 0L && days.any { it.ms > 0L }) {
            val over = days.count { it.ms > budgetMs }
            val comparison = when {
                averageMs > budgetMs -> stringResource(
                    id = R.string.ruleGroups_budget_compare_less,
                    budgetMs.milliseconds.toHrMinString(context),
                    (averageMs - budgetMs).milliseconds.toHrMinString(context)
                )

                averageMs < budgetMs -> stringResource(
                    id = R.string.ruleGroups_budget_compare_more,
                    budgetMs.milliseconds.toHrMinString(context),
                    (budgetMs - averageMs).milliseconds.toHrMinString(context)
                )

                else -> stringResource(id = R.string.ruleGroups_budget_compare_same)
            }
            val overText = when (over) {
                0 -> stringResource(id = R.string.ruleGroups_budget_compare_noDayOver)
                days.size -> stringResource(id = R.string.ruleGroups_budget_compare_everyDayOver)
                else -> pluralStringResource(
                    id = R.plurals.ruleGroups_budget_compare_daysOver, count = over, over, days.size
                )
            }
            Text(
                text = "$comparison $overText",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else if (days.all { it.ms == 0L }) {
            Text(
                text = stringResource(id = R.string.ruleGroups_budget_lastDays_empty),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
