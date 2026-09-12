package com.flx_apps.digitaldetox.ui.screens.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import java.time.LocalTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val DialSize = 264.dp
private val RingWidth = 40.dp
private val HandleRadius = 15.dp
private val HandleIconSize = 16.dp

/** How far past a handle a touch still grabs it. */
private val HandleSlack = 8.dp

/** Dragging moves the times in quarter hours; exact minutes are for the time fields. */
private const val SnapMinutes = 15

/** The hours labelled inside the ring. */
private val LabelledHours = listOf(0, 6, 12, 18)

private val LocalTime.minuteOfDay get() = hour * 60 + minute

private fun timeOf(minuteOfDay: Int) = minuteOfDay.mod(MinutesPerDay).let {
    LocalTime.of(it / 60, it % 60)
}

/** The minutes from [from] to [to] around the clock, i.e. the length of the window between them. */
private fun minutesBetween(from: Int, to: Int) = (to - from).mod(MinutesPerDay)

/** The shorter way around the clock between two minutes of the day. */
private fun distanceAround(a: Int, b: Int) = min(minutesBetween(a, b), minutesBetween(b, a))

private enum class DialGrip { Start, End, Window }

/**
 * A 24-hour dial for the time window of a schedule rule: the ring is the day, midnight at the top,
 * and the arc is when the feature runs. Either end can be dragged, or the arc as a whole, in
 * quarter-hour steps. Touches anywhere else are left alone, so they still move the sheet the dial
 * sits in. With [allDay] the ring is full and there is nothing to drag.
 *
 * The ends never meet while dragging: equal times mean all day, which is a switch of its own.
 */
@Composable
internal fun TimeRangeDial(
    start: LocalTime,
    end: LocalTime,
    allDay: Boolean,
    accent: Color,
    onAccent: Color,
    onChange: (start: LocalTime, end: LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val haptics = LocalHapticFeedback.current
    val currentStart by rememberUpdatedState(start.minuteOfDay)
    val currentEnd by rememberUpdatedState(end.minuteOfDay)
    val currentOnChange by rememberUpdatedState(onChange)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val labels = remember(locale) { LabelledHours.map { it to hourLabel(it, locale) } }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val startIcon = rememberVectorPainter(Icons.Rounded.PlayArrow)
    val endIcon = rememberVectorPainter(Icons.Rounded.Stop)
    // how long the window is: equal times make it the whole day
    val length = if (allDay) MinutesPerDay else minutesBetween(start.minuteOfDay, end.minuteOfDay)
    val lengthText = if (length % 60 == 0) {
        context.getString(R.string.duration_hours_short, length / 60)
    } else {
        context.getString(R.string.duration_hoursMinutes_short, length / 60, length % 60)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(DialSize)
            .clearAndSetSemantics {
                contentDescription = timeSpanText(context, start, end)
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(allDay) {
                    if (!allDay) {
                        detectDialDrags(
                            currentStart = { currentStart },
                            currentEnd = { currentEnd },
                            onChange = { newStart, newEnd ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnChange(timeOf(newStart), timeOf(newEnd))
                            }
                        )
                    }
                }
        ) {
            val ringWidth = RingWidth.toPx()
            val radius = (size.minDimension - ringWidth) / 2
            drawCircle(trackColor, radius = radius, style = Stroke(ringWidth))
            drawHourTicks(radius - ringWidth / 2 - 6.dp.toPx(), tickColor)
            if (allDay) {
                drawCircle(accent, radius = radius, style = Stroke(ringWidth))
            } else {
                drawArc(
                    accent,
                    startAngle = angleOf(start.minuteOfDay),
                    sweepAngle = minutesBetween(start.minuteOfDay, end.minuteOfDay) * 360f /
                            MinutesPerDay,
                    useCenter = false,
                    topLeft = center - Offset(radius, radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(ringWidth, cap = StrokeCap.Round)
                )
                drawHandle(pointAt(start.minuteOfDay, radius), startIcon, onAccent, accent)
                drawHandle(pointAt(end.minuteOfDay, radius), endIcon, onAccent, accent)
            }
            val labelRim = radius - ringWidth / 2 - 12.dp.toPx()
            labels.forEach { (hour, text) ->
                val label = textMeasurer.measure(text, labelStyle)
                // a label on the side of the dial needs room for its width, one at the top or
                // bottom only for its height
                val halfExtent = (if (hour % 12 == 0) label.size.height else label.size.width) / 2f
                val position = pointAt(hour * 60, labelRim - halfExtent)
                drawText(
                    label,
                    color = labelColor,
                    topLeft = position - Offset(label.size.width / 2f, label.size.height / 2f)
                )
            }
        }
        Text(
            text = lengthText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 80.dp)
        )
    }
}

/** The canvas angle of a minute of the day, midnight at the top and going clockwise. */
private fun angleOf(minuteOfDay: Int) = minuteOfDay * 360f / MinutesPerDay - 90f

private fun DrawScope.pointAt(minuteOfDay: Int, radius: Float): Offset {
    val radians = angleOf(minuteOfDay) * PI / 180
    return center + Offset((cos(radians) * radius).toFloat(), (sin(radians) * radius).toFloat())
}

/** A dot for every hour just inside the ring, except where a label names it. */
private fun DrawScope.drawHourTicks(radius: Float, color: Color) {
    for (hour in 0 until 24) {
        if (hour in LabelledHours) continue
        drawCircle(color, radius = 1.5.dp.toPx(), center = pointAt(hour * 60, radius))
    }
}

private fun DrawScope.drawHandle(at: Offset, icon: VectorPainter, fill: Color, iconColor: Color) {
    drawCircle(fill, radius = HandleRadius.toPx(), center = at)
    val iconSize = HandleIconSize.toPx()
    translate(at.x - iconSize / 2, at.y - iconSize / 2) {
        with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(iconColor)) }
    }
}

/**
 * Follows drags on the dial. A touch on or next to an end moves that end, a touch on the arc
 * moves the whole window; either way the times follow in [SnapMinutes] steps and [onChange] only
 * hears about actual changes. The touch is consumed from the moment it lands on something to
 * move, so the sheet around the dial does not take it over.
 */
private suspend fun PointerInputScope.detectDialDrags(
    currentStart: () -> Int,
    currentEnd: () -> Int,
    onChange: (start: Int, end: Int) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringWidth = RingWidth.toPx()
        val radius = (min(size.width, size.height) - ringWidth) / 2
        val offRing = abs((down.position - center).getDistance() - radius) - ringWidth / 2
        if (offRing > HandleSlack.toPx()) return@awaitEachGesture
        val grabbedStart = currentStart()
        val grabbedEnd = currentEnd()
        val touched = minuteAt(down.position, center)
        // the angle a handle and the slack around it cover, in minutes of the day
        val handleReach = (HandleRadius + HandleSlack).toPx()
        val reach = (handleReach / radius / (2 * PI) * MinutesPerDay).toInt()
        val toStart = distanceAround(touched, grabbedStart)
        val toEnd = distanceAround(touched, grabbedEnd)
        val grip = when {
            minOf(toStart, toEnd) <= reach -> if (toStart <= toEnd) DialGrip.Start else DialGrip.End
            minutesBetween(grabbedStart, touched) <= minutesBetween(grabbedStart, grabbedEnd) ->
                DialGrip.Window

            else -> return@awaitEachGesture
        }
        down.consume()
        var lastTouched = touched
        var moved = 0
        drag(down.id) { change ->
            change.consume()
            val minute = minuteAt(change.position, center)
            val start = currentStart()
            val end = currentEnd()
            val (newStart, newEnd) = when (grip) {
                DialGrip.Start -> snap(minute) to end
                DialGrip.End -> start to snap(minute)
                DialGrip.Window -> {
                    // added up step by step, so a drag across midnight keeps its direction
                    val step = minutesBetween(lastTouched, minute)
                    moved += if (step > MinutesPerDay / 2) step - MinutesPerDay else step
                    lastTouched = minute
                    val shift = (moved.toFloat() / SnapMinutes).roundToInt() * SnapMinutes
                    (grabbedStart + shift).mod(MinutesPerDay) to
                            (grabbedEnd + shift).mod(MinutesPerDay)
                }
            }
            val changed = newStart != start || newEnd != end
            if (changed && newStart != newEnd) onChange(newStart, newEnd)
        }
    }
}

/** The minute of the day a point on the dial stands for. */
private fun minuteAt(position: Offset, center: Offset): Int {
    val radians = atan2(position.x - center.x, center.y - position.y)
    return (radians / (2 * PI) * MinutesPerDay).roundToInt().mod(MinutesPerDay)
}

private fun snap(minuteOfDay: Int) =
    ((minuteOfDay.toFloat() / SnapMinutes).roundToInt() * SnapMinutes).mod(MinutesPerDay)
