package com.flx_apps.digitaldetox.ui.screens.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.feature_types.isScheduled
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val MinutesPerDay = 24 * 60

/** Any Monday will do: [activeMinutes] only needs dates that fall on the right day of the week. */
private val ReferenceMonday: LocalDate = LocalDate.of(2024, 1, 1)

/** The hours the axis below the bars is labelled with. */
private val AxisHours = listOf(0, 6, 12, 18, 24)

private val DayLabelWidth = 44.dp

/**
 * The minutes of [day] during which [rules] have a feature active, as runs of minutes since
 * midnight. Read off [isScheduled] minute by minute, so the picture cannot disagree with when the
 * feature actually runs, rules across midnight and "all day" included.
 */
internal fun activeMinutes(rules: Collection<FeatureScheduleRule>, day: DayOfWeek): List<IntRange> {
    val date = ReferenceMonday.plusDays(day.value - 1L)
    val runs = mutableListOf<IntRange>()
    var runStart = -1
    for (minute in 0..MinutesPerDay) {
        val active = minute < MinutesPerDay &&
                rules.isScheduled(LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60)))
        if (active && runStart < 0) runStart = minute
        if (!active && runStart >= 0) {
            runs += runStart until minute
            runStart = -1
        }
    }
    return runs
}

/** The days of the week in the order the [locale] lists them, i.e. starting on Monday or Sunday. */
internal fun orderedWeekDays(locale: Locale): List<DayOfWeek> {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { firstDay.plus(it) }
}

/**
 * The week at a glance: a bar per day, filled where the feature is active, with the current
 * moment marked on today's bar.
 */
@Composable
fun WeekScheduleOverview(rules: Collection<FeatureScheduleRule>, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    val days = remember(locale) { orderedWeekDays(locale) }
    val activeMinutesByDay = remember(rules) {
        DayOfWeek.entries.associateWith { activeMinutes(rules, it) }
    }
    val now = remember { LocalDateTime.now() }
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.surfaceVariant
    val nowColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier) {
        days.forEach { day ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(DayLabelWidth)
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(percent = 50))
                ) {
                    drawRect(idleColor)
                    activeMinutesByDay.getValue(day).forEach { run ->
                        drawRect(
                            activeColor,
                            topLeft = Offset(size.width * run.first / MinutesPerDay, 0f),
                            size = Size(
                                size.width * (run.last + 1 - run.first) / MinutesPerDay,
                                size.height
                            )
                        )
                    }
                    if (day == now.dayOfWeek) {
                        val x = size.width * (now.hour * 60 + now.minute) / MinutesPerDay
                        drawLine(
                            nowColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }
        HourAxis(modifier = Modifier.padding(start = DayLabelWidth, top = 2.dp))
    }
}

/** The hour labels under the bars, each centered on the moment it names. */
@Composable
private fun HourAxis(modifier: Modifier = Modifier) {
    Layout(
        content = {
            AxisHours.forEach {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val labels = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        layout(width, labels.maxOf { it.height }) {
            labels.forEachIndexed { index, label ->
                val center = width * AxisHours[index] / 24
                label.placeRelative((center - label.width / 2).coerceIn(0, width - label.width), 0)
            }
        }
    }
}
