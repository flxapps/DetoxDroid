package com.flx_apps.digitaldetox.ui.screens.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.feature_types.isScheduled
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

internal const val MinutesPerDay = 24 * 60

/** Any Monday will do: [activeMinutes] only needs dates that fall on the right day of the week. */
private val ReferenceMonday: LocalDate = LocalDate.of(2024, 1, 1)

/** The hours the axis below the bars is labelled with. */
private val AxisHours = listOf(0, 6, 12, 18, 24)

private val DayLabelWidth = 40.dp
private val BarHeight = 14.dp

/** How far the mark for the current moment reaches past today's bar, above and below. */
private val NowMarkOvershoot = 3.dp

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

/** An hour of the day, 0 to 24, as a short label, "18" or "6 PM" as the [locale] tells time. */
internal fun hourLabel(hour: Int, locale: Locale): String {
    val usesAmPm = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale
    ).contains('a')
    return if (usesAmPm) {
        DateTimeFormatter.ofPattern("h a", locale).format(LocalTime.of(hour % 24, 0))
    } else {
        hour.toString()
    }
}

/** Whether a schedule has its feature active right now, and until when. */
internal sealed interface ScheduleStatus {
    data object AlwaysActive : ScheduleStatus
    data class ActiveUntil(val until: LocalDateTime) : ScheduleStatus
    data class InactiveUntil(val until: LocalDateTime) : ScheduleStatus
}

/**
 * Where [rules] stand at [now]: whether the feature runs, and the next minute that changes it,
 * read off [isScheduled] like [activeMinutes]. A week ahead is far enough, the schedule repeats
 * after that. Null only for rules that never have the feature active, which rules cannot express.
 */
internal fun scheduleStatus(
    rules: Collection<FeatureScheduleRule>, now: LocalDateTime
): ScheduleStatus? {
    val active = rules.isScheduled(now)
    val minute = now.truncatedTo(ChronoUnit.MINUTES)
    for (step in 1L..7L * MinutesPerDay) {
        val at = minute.plusMinutes(step)
        if (rules.isScheduled(at) != active) {
            return if (active) ScheduleStatus.ActiveUntil(at) else ScheduleStatus.InactiveUntil(at)
        }
    }
    return if (active) ScheduleStatus.AlwaysActive else null
}

/** The current time, updated at every full minute. */
@Composable
internal fun rememberCurrentMinute(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalDateTime.now()
        }
    }
    return now
}

/**
 * The week at a glance: a bar per day, filled in each rule's color where it has the feature
 * active, with the current moment marked on today's bar. Without any rules the feature runs all
 * the time, and the bars are full. Above the bars, [showStatus] says what the schedule is doing
 * right now; it is left out while the feature is off, when the schedule is not doing anything.
 *
 * @param rules the rules, each with the color it is told apart by
 */
@Composable
fun WeekScheduleOverview(
    rules: List<Pair<FeatureScheduleRule, Color>>,
    showStatus: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val days = remember(locale) { orderedWeekDays(locale) }
    val alwaysColor = MaterialTheme.colorScheme.primary
    val layers = remember(rules, alwaysColor) {
        rules.ifEmpty { listOf(null to alwaysColor) }.map { (rule, color) ->
            color to DayOfWeek.entries.associateWith { activeMinutes(listOfNotNull(rule), it) }
        }
    }
    val now = rememberCurrentMinute()
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val nowColor = MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showStatus) {
                val allRules = rules.map { it.first }
                val status = remember(allRules, now) { scheduleStatus(allRules, now) }
                status?.let {
                    ScheduleStatusLine(status = it, now = now)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            days.forEach { day ->
                val isToday = day == now.dayOfWeek
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday) FontWeight.Bold else null,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(DayLabelWidth)
                    )
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(BarHeight + NowMarkOvershoot * 2)
                    ) {
                        val barHeight = BarHeight.toPx()
                        val barTop = (size.height - barHeight) / 2
                        val corners = CornerRadius(barHeight / 2)
                        drawRoundRect(
                            trackColor,
                            topLeft = Offset(0f, barTop),
                            size = Size(size.width, barHeight),
                            cornerRadius = corners
                        )
                        layers.forEach { (color, runsByDay) ->
                            runsByDay.getValue(day).forEach { run ->
                                drawRoundRect(
                                    color,
                                    topLeft = Offset(
                                        size.width * run.first / MinutesPerDay, barTop
                                    ),
                                    size = Size(
                                        size.width * (run.last + 1 - run.first) / MinutesPerDay,
                                        barHeight
                                    ),
                                    cornerRadius = corners
                                )
                            }
                        }
                        if (isToday) {
                            val x = size.width * (now.hour * 60 + now.minute) / MinutesPerDay
                            drawLine(
                                nowColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
            HourAxis(
                locale = locale,
                modifier = Modifier.padding(start = DayLabelWidth, top = 4.dp)
            )
        }
    }
}

/** What the schedule is doing right now, behind a dot that is filled while the feature runs. */
@Composable
private fun ScheduleStatusLine(status: ScheduleStatus, now: LocalDateTime) {
    val locale = Locale.getDefault()
    val text = when (status) {
        ScheduleStatus.AlwaysActive -> stringResource(
            id = R.string.feature_settings_schedule_hint_activeAllTheTime
        )

        is ScheduleStatus.ActiveUntil -> stringResource(
            id = R.string.feature_settings_schedule_status_activeUntil,
            momentText(status.until, now, locale)
        )

        is ScheduleStatus.InactiveUntil -> stringResource(
            id = R.string.feature_settings_schedule_status_inactiveUntil,
            momentText(status.until, now, locale)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (status is ScheduleStatus.InactiveUntil) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    shape = CircleShape
                )
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** A moment within the coming week: its time, and its day as well unless that is today. */
private fun momentText(at: LocalDateTime, now: LocalDateTime, locale: Locale): String {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at)
    return if (at.toLocalDate() == now.toLocalDate()) {
        time
    } else {
        "${at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $time"
    }
}

/** The hour labels under the bars, each centered on the moment it names. */
@Composable
private fun HourAxis(locale: Locale, modifier: Modifier = Modifier) {
    Layout(
        content = {
            AxisHours.forEach {
                Text(
                    text = hourLabel(it, locale),
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
