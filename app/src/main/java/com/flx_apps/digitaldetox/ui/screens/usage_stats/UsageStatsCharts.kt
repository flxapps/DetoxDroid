package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.util.formatDurationMsCompact
import com.flx_apps.digitaldetox.util.formatDurationMsShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

private val ChartColorResources = listOf(
    R.color.pink, R.color.orange, R.color.yellow,
    R.color.green, R.color.blue, R.color.purple
)

@Composable
fun chartColor(index: Int): Color =
    colorResource(id = ChartColorResources[index % ChartColorResources.size])

/**
 * Draws [textLayout] in a rounded tooltip bubble whose bottom edge sits just above [anchorTopY],
 * horizontally centered on [anchorX] but clamped to the canvas bounds.
 */
private fun DrawScope.drawTooltip(
    textLayout: TextLayoutResult, anchorX: Float, anchorTopY: Float, backgroundColor: Color
) {
    val bgPadding = 4.dp.toPx()
    val x = (anchorX - textLayout.size.width / 2f)
        .coerceIn(bgPadding, (size.width - textLayout.size.width - bgPadding).coerceAtLeast(bgPadding))
    val y = anchorTopY - textLayout.size.height - 4.dp.toPx()
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(x - bgPadding, y - bgPadding),
        size = Size(
            textLayout.size.width + bgPadding * 2, textLayout.size.height + bgPadding * 2
        ),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawText(textLayout, topLeft = Offset(x, y))
}

/** Returns the bar slot index at [tapX], or -1 if the tap is outside the chart area. */
private fun barIndexAt(tapX: Float, chartLeft: Float, slotWidth: Float, barCount: Int): Int {
    if (slotWidth <= 0f) return -1
    val index = floor((tapX - chartLeft) / slotWidth).toInt()
    return if (index in 0 until barCount) index else -1
}

@Composable
fun ByDayBarChart(
    dailyUsage: List<DailyUsage>,
    selectedDay: LocalDate?,
    onDaySelected: (LocalDate?) -> Unit = {}
) {
    val context = LocalContext.current
    if (dailyUsage.size < 2) return

    val maxValue = dailyUsage.maxOf { it.totalTimeMs }.toFloat().coerceAtLeast(1f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val barColor = colorResource(id = R.color.blue)
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.inverseOnSurface
    )
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface

    // Every n-th bar gets an axis label so they don't overlap on long ranges.
    val labelStep = if (dailyUsage.size > 14) dailyUsage.size / 7 else 1
    val dayFormatter = remember(dailyUsage.size) {
        DateTimeFormatter.ofPattern(if (dailyUsage.size > 14) "d" else "E", Locale.getDefault())
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(dailyUsage, selectedDay) {
                detectTapGestures { tapOffset ->
                    val chartLeft = BY_DAY_GUTTER.toPx()
                    val chartWidth = size.width - chartLeft - 8.dp.toPx()
                    val index = barIndexAt(
                        tapOffset.x, chartLeft, chartWidth / dailyUsage.size, dailyUsage.size
                    )
                    val tappedDay = dailyUsage.getOrNull(index)?.date
                    onDaySelected(if (tappedDay == selectedDay) null else tappedDay)
                }
            }
    ) {
        val chartLeft = BY_DAY_GUTTER.toPx()
        val chartBottom = size.height - 32.dp.toPx()
        val chartTop = 12.dp.toPx()
        val chartRight = size.width - 8.dp.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val horizontalGridLines = 4
        for (i in 0..horizontalGridLines) {
            val y = chartTop + (chartHeight * i / horizontalGridLines)
            drawLine(
                color = surfaceVariant,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1f
            )
            val gridLabel = formatDurationMsShort(
                context, (maxValue - maxValue * i / horizontalGridLines).toLong()
            )
            drawText(
                textMeasurer = textMeasurer,
                text = gridLabel,
                topLeft = Offset(0f, y - 6.dp.toPx()),
                style = labelStyle
            )
        }

        val barCount = dailyUsage.size
        val slotWidth = chartWidth / barCount
        val barWidth = (slotWidth - 2.dp.toPx()).coerceAtMost(24.dp.toPx()).coerceAtLeast(1f)

        var selectedBarTop: Offset? = null
        for ((index, du) in dailyUsage.withIndex()) {
            val fraction = du.totalTimeMs.toFloat() / maxValue
            val barHeight = fraction * chartHeight
            val x = chartLeft + index * slotWidth + (slotWidth - barWidth) / 2f
            val y = chartBottom - barHeight

            val isSelected = du.date == selectedDay
            if (isSelected) selectedBarTop = Offset(x + barWidth / 2f, y)

            drawRoundRect(
                color = if (isSelected) accentColor else barColor.copy(alpha = 0.6f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            if (labelStep > 1 && index % labelStep != 0 && index != barCount - 1) continue
            val textLayoutResult = textMeasurer.measure(du.date.format(dayFormatter), style = labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x + barWidth / 2 - textLayoutResult.size.width / 2f,
                    chartBottom + 6.dp.toPx()
                ),
            )
        }

        selectedBarTop?.let { barTop ->
            val selected = dailyUsage.first { it.date == selectedDay }
            val tooltipLayout = textMeasurer.measure(
                formatDurationMsCompact(context, selected.totalTimeMs), style = tooltipStyle
            )
            drawTooltip(tooltipLayout, barTop.x, barTop.y, tooltipBgColor)
        }
    }
}

private val BY_DAY_GUTTER = 44.dp

@Composable
fun TimeOfDayBarChart(hourBuckets: IntArray) {
    val context = LocalContext.current
    HourBucketsChart(
        buckets = hourBuckets,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        tooltipTextFor = { hour, count ->
            context.getString(R.string.chart_tooltip_opens, hour, count)
        }
    )
}

/**
 * A 24-slot bar chart of per-hour counts with axis labels every six hours and a tap tooltip.
 * Shared by the app-opens and screen-unlocks cards.
 */
@Composable
private fun HourBucketsChart(
    buckets: IntArray,
    modifier: Modifier,
    tooltipTextFor: (hour: Int, count: Int) -> String
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val barColor = colorResource(id = R.color.blue)
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.inverseOnSurface
    )
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface

    var selectedIndex by remember { mutableIntStateOf(-1) }
    val maxValue = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)

    Canvas(
        modifier = modifier.pointerInput(buckets) {
            detectTapGestures { tapOffset ->
                val chartLeft = 8f
                val chartWidth = size.width - chartLeft * 2
                val index = barIndexAt(tapOffset.x, chartLeft, chartWidth / 24f, 24)
                selectedIndex = if (index == selectedIndex) -1 else index
            }
        }
    ) {
        val labelHeight = 20.dp.toPx()
        val chartLeft = 8f
        val chartBottom = size.height - labelHeight
        val chartTop = 8f
        val chartWidth = size.width - chartLeft * 2
        val chartHeight = chartBottom - chartTop

        val slotWidth = chartWidth / 24f
        val barWidth = slotWidth * 0.7f
        val gap = slotWidth * 0.3f

        for (i in 0..23) {
            val x = chartLeft + i * slotWidth + gap / 2
            val fraction = buckets[i].toFloat() / maxValue
            val barHeight = fraction * chartHeight

            drawRoundRect(
                color = barColor.copy(alpha = 0.3f + 0.7f * fraction),
                topLeft = Offset(x, chartBottom - barHeight),
                size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            if (i % 6 == 0) {
                val labelText = String.format(Locale.getDefault(), "%02d", i)
                val textLayoutResult = textMeasurer.measure(labelText, style = labelStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x + barWidth / 2 - textLayoutResult.size.width / 2f,
                        chartBottom + 2.dp.toPx()
                    ),
                )
            }
        }

        if (selectedIndex >= 0) {
            val tooltipLayout = textMeasurer.measure(
                tooltipTextFor(selectedIndex, buckets[selectedIndex]), style = tooltipStyle
            )
            val x = chartLeft + selectedIndex * slotWidth + gap / 2
            val barHeight = (buckets[selectedIndex].toFloat() / maxValue) * chartHeight
            drawTooltip(tooltipLayout, x + barWidth / 2, chartBottom - barHeight, tooltipBgColor)
        }
    }
}

@Composable
fun WeekdayAveragesChart(weekdayAverages: FloatArray) {
    val context = LocalContext.current
    val dayLabels = DayOfWeek.values().map {
        it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    val maxValue = (weekdayAverages.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val colors = weekdayAverages.indices.map { chartColor(it) }
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.inverseOnSurface
    )
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface

    var selectedIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(weekdayAverages) {
                detectTapGestures { tapOffset ->
                    val slotWidth = size.width / weekdayAverages.size.toFloat()
                    val index = barIndexAt(tapOffset.x, 0f, slotWidth, weekdayAverages.size)
                    selectedIndex = if (index == selectedIndex) -1 else index
                }
            }
    ) {
        val barCount = weekdayAverages.size
        val labelHeight = 28.dp.toPx()
        val chartHeight = size.height - 12.dp.toPx() - labelHeight
        val slotWidth = size.width / barCount
        val barWidth = slotWidth / 2

        for (index in weekdayAverages.indices) {
            val avg = weekdayAverages[index]
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            val barHeight = (avg / maxValue) * chartHeight
            val y = chartHeight - barHeight

            drawRoundRect(
                color = colors[index],
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            val dayLayoutResult = textMeasurer.measure(dayLabels[index], style = labelStyle)
            drawText(
                textLayoutResult = dayLayoutResult,
                topLeft = Offset(
                    x + barWidth / 2 - dayLayoutResult.size.width / 2f,
                    chartHeight + 8.dp.toPx()
                ),
            )
        }

        if (selectedIndex >= 0) {
            val avg = weekdayAverages[selectedIndex]
            val tooltipLayout = textMeasurer.measure(
                formatDurationMsCompact(context, avg.toLong()), style = tooltipStyle
            )
            val x = selectedIndex * slotWidth + slotWidth / 2f
            drawTooltip(tooltipLayout, x, chartHeight - (avg / maxValue) * chartHeight, tooltipBgColor)
        }
    }
}

@Composable
fun AppTrendSparkline(
    dailyUsage: List<Pair<LocalDate, Long>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (dailyUsage.size < 2) return

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        textAlign = TextAlign.Center,
        color = labelColor
    )
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(color = tooltipTextColor)

    var selectedIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .height(70.dp)
            .fillMaxWidth()
            .pointerInput(dailyUsage) {
                detectTapGestures { tapOffset ->
                    val padding = 4.dp.toPx()
                    val stepX = (size.width - padding * 2) / (dailyUsage.size - 1)
                    val index = ((tapOffset.x - padding) / stepX + 0.5f).toInt()
                    selectedIndex = if (index == selectedIndex || index !in dailyUsage.indices) {
                        -1
                    } else {
                        index
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val padding = 4.dp.toPx()
        val labelHeight = 14.dp.toPx()
        val stepX = (w - padding * 2) / (dailyUsage.size - 1)
        val maxMs = dailyUsage.maxOf { it.second }.coerceAtLeast(1)

        val points = dailyUsage.mapIndexed { i, (_, ms) ->
            Offset(
                x = padding + i * stepX,
                y = (h - padding) - (ms.toFloat() / maxMs) * (h - padding * 2 - labelHeight)
            )
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val controlX = (prev.x + curr.x) / 2f
                cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }
        drawPath(fillPath, fillColor, style = Fill)
        drawPath(
            path,
            lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        for ((i, pt) in points.withIndex()) {
            val isSelected = i == selectedIndex
            drawCircle(
                color = if (isSelected) tertiaryColor else lineColor,
                radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(),
                center = pt
            )
            val dayLabel =
                dailyUsage[i].first.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            val textLayout = textMeasurer.measure(dayLabel, style = labelStyle)
            drawText(textLayout, topLeft = Offset(pt.x - textLayout.size.width / 2f, 0f))

            if (isSelected) {
                val tooltipLayout = textMeasurer.measure(
                    formatDurationMsCompact(context, dailyUsage[i].second), style = tooltipStyle
                )
                drawTooltip(tooltipLayout, pt.x, pt.y, tooltipBgColor)
            }
        }
    }
}

@Composable
fun ScreenUnlocksChart(unlockCount: Int, unlockDelta: Int?, unlockHourBuckets: IntArray) {
    val context = LocalContext.current
    val subtitleStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = unlockCount.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.usage_stats_unlocks_short),
                style = subtitleStyle
            )
            if (unlockDelta != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val isUp = unlockDelta >= 0
                val deltaColor =
                    if (isUp) MaterialTheme.colorScheme.error else colorResource(id = R.color.green)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = deltaColor.copy(alpha = if (isSystemInDarkTheme()) 0.25f else 0.15f)
                ) {
                    Text(
                        text = (if (isUp) "+" else "") + unlockDelta.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = deltaColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (unlockHourBuckets.sum() > 0) {
            HourBucketsChart(
                buckets = unlockHourBuckets,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 8.dp),
                tooltipTextFor = { hour, count ->
                    context.getString(R.string.chart_tooltip_unlocks, hour, count)
                }
            )
        }
    }
}

@Composable
fun CategoryDonutChart(categoryDistribution: Map<String, Long>) {
    val context = LocalContext.current
    if (categoryDistribution.isEmpty()) return

    val totalMs = categoryDistribution.values.sum().coerceAtLeast(1L)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val entries = categoryDistribution.entries.toList().take(8)
    val colors = entries.indices.map { chartColor(it) }
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.inverseOnSurface
    )
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface

    var selectedIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(categoryDistribution) {
                detectTapGestures { tapOffset ->
                    val centerX = size.width * DONUT_CENTER_X_FRACTION
                    val centerY = size.height / 2f
                    val radius = size.width * DONUT_RADIUS_FRACTION
                    val dx = tapOffset.x - centerX
                    val dy = tapOffset.y - centerY
                    val distance = sqrt(dx * dx + dy * dy)

                    if (distance > radius || distance < radius * DONUT_HOLE_FRACTION) {
                        selectedIndex = -1
                        return@detectTapGestures
                    }

                    // arc angles start at 12 o'clock (-90°), so shift the tap angle to match
                    val tapAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    val arcAngle = (tapAngle + 90f + 360f) % 360f

                    var segmentStart = 0f
                    for ((index, entry) in entries.withIndex()) {
                        val sweepAngle = (entry.value.toFloat() / totalMs.toFloat()) * 360f
                        if (arcAngle - segmentStart < sweepAngle) {
                            selectedIndex = if (selectedIndex == index) -1 else index
                            break
                        }
                        segmentStart += sweepAngle
                    }
                }
            }
    ) {
        val centerX = size.width * DONUT_CENTER_X_FRACTION
        val centerY = size.height / 2f
        val radius = size.width * DONUT_RADIUS_FRACTION
        var startAngle = -90f

        for ((index, entry) in entries.withIndex()) {
            val sweepAngle = (entry.value.toFloat() / totalMs.toFloat()) * 360f
            drawArc(
                color = colors[index],
                startAngle = startAngle,
                sweepAngle = sweepAngle.coerceAtLeast(1f),
                useCenter = true,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2)
            )
            startAngle += sweepAngle
        }

        drawCircle(
            color = surfaceColor,
            radius = radius * DONUT_HOLE_FRACTION,
            center = Offset(centerX, centerY)
        )

        val totalLayout = textMeasurer.measure(
            formatDurationMsCompact(context, totalMs), style = labelStyle
        )
        drawText(
            totalLayout,
            topLeft = Offset(
                centerX - totalLayout.size.width / 2f, centerY - totalLayout.size.height / 2f
            )
        )

        // legend, vertically centered next to the donut
        val legendX = size.width * 0.72f
        val legendLayouts = entries.map { entry ->
            val pct = (entry.value.toFloat() / totalMs.toFloat() * 100).toInt()
            textMeasurer.measure(
                context.getString(R.string.chart_percent, entry.key, pct), style = labelStyle
            )
        }
        val totalLegendHeight = legendLayouts.sumOf { it.size.height } +
            (legendLayouts.size - 1) * 6.dp.toPx().toInt()
        var legendY = (centerY - totalLegendHeight / 2f).coerceAtLeast(8f)
        for (i in entries.indices) {
            drawCircle(
                colors[i], radius = 4.dp.toPx(),
                center = Offset(legendX - 12.dp.toPx(), legendY + 5.dp.toPx())
            )
            drawText(legendLayouts[i], topLeft = Offset(legendX, legendY))
            legendY += legendLayouts[i].size.height.toFloat() + 6.dp.toPx()
        }

        if (selectedIndex >= 0) {
            val entry = entries[selectedIndex]
            val pct = (entry.value.toFloat() / totalMs.toFloat() * 100).toInt()
            val tooltipLayout = textMeasurer.measure(
                context.getString(R.string.chart_percent, entry.key, pct) +
                    " — " + formatDurationMsCompact(context, entry.value),
                style = tooltipStyle
            )
            drawTooltip(tooltipLayout, size.width / 2f, size.height / 2f, tooltipBgColor)
        }
    }
}

private const val DONUT_CENTER_X_FRACTION = 0.35f
private const val DONUT_RADIUS_FRACTION = 0.28f
private const val DONUT_HOLE_FRACTION = 0.55f

@Composable
fun ScrollIntensityChart(topApps: List<AppUsageStat>) {
    val sorted = topApps
        .filter { it.scrollIntensityScore > 0f }
        .sortedByDescending { it.scrollIntensityScore }
        .take(5)
    if (sorted.isEmpty()) return

    val context = LocalContext.current
    var tappedIndex by remember { mutableIntStateOf(-1) }

    val maxScore = sorted.maxOf { it.scrollIntensityScore }.coerceAtLeast(1f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
    )
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    val colors = sorted.indices.map { chartColor(it) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tooltipBgColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .pointerInput(sorted) {
                detectTapGestures(onTap = { offset ->
                    val index = (offset.y / 28.dp.toPx()).toInt()
                    tappedIndex = if (index in sorted.indices) index else -1
                })
            }
    ) {
        val rowHeight = 28.dp.toPx()
        val barHeight = 14.dp.toPx()
        val labelWidth = 120.dp.toPx()
        val gap = 4.dp.toPx()
        val chartLeft = labelWidth + gap
        val chartRight = size.width - gap
        val chartWidth = chartRight - chartLeft

        for ((index, app) in sorted.withIndex()) {
            val y = index * rowHeight
            val fraction = app.scrollIntensityScore / maxScore

            // Pre-measure value text so we can reserve space and avoid cropping at the right edge.
            val valueText = app.scrollIntensityScore.toInt().toString()
            val valueLayout = textMeasurer.measure(valueText, style = valueStyle)
            val valueSlot = valueLayout.size.width + gap * 2
            val maxBarWidth = (chartWidth - valueSlot).coerceAtLeast(barHeight)
            val barWidth = (fraction * chartWidth).coerceAtMost(maxBarWidth)

            val labelLayout = textMeasurer.measure(app.label, style = labelStyle)
            drawText(labelLayout, topLeft = Offset(0f, y + (rowHeight - labelLayout.size.height) / 2f))

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(chartLeft, y + (rowHeight - barHeight) / 2f),
                size = Size(chartWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )

            drawRoundRect(
                color = colors[index],
                topLeft = Offset(chartLeft, y + (rowHeight - barHeight) / 2f),
                size = Size(barWidth.coerceAtLeast(barHeight), barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )

            drawText(
                valueLayout,
                topLeft = Offset(
                    (chartLeft + barWidth + gap).coerceAtMost(chartRight - valueLayout.size.width - gap),
                    y + (rowHeight - valueLayout.size.height) / 2f
                )
            )
        }

        if (tappedIndex in sorted.indices) {
            val app = sorted[tappedIndex]
            val tooltipLayout = textMeasurer.measure(
                context.getString(
                    R.string.usage_stats_scroll_intensity_detail,
                    app.scrollEventCount,
                    app.scrollsPerMinute
                ),
                style = tooltipStyle
            )
            // below the tapped row, so the finger doesn't cover it
            val anchorY = (tappedIndex + 1) * rowHeight + tooltipLayout.size.height + 6.dp.toPx()
            drawTooltip(tooltipLayout, size.width / 2f, anchorY, tooltipBgColor)
        }
    }
}
