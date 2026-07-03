package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flx_apps.digitaldetox.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How far back the calendar reaches at most (history retention is shorter anyway). */
private const val MAX_MONTHS_BACK = 23L

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsDateRangePicker(
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    earliestAvailableDate: LocalDate?
) {
    val today = remember { LocalDate.now() }
    var selectedStart by remember { mutableStateOf<LocalDate?>(null) }
    var selectedEnd by remember { mutableStateOf<LocalDate?>(null) }
    var isInputMode by remember { mutableStateOf(false) }

    val fmt = remember { DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM) }
    val inputFmt = remember { DateTimeFormatter.ofPattern("d/M/yyyy") }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }

    fun parseInputDate(text: String): LocalDate? =
        listOf("d/M/yyyy", "dd/MM/yyyy", "d.M.yyyy", "d-M-yyyy", "yyyy-M-d")
            .firstNotNullOfOrNull { pattern ->
                runCatching { LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
            }

    val parsedStart = if (isInputMode) parseInputDate(startText) else selectedStart
    val parsedEnd = if (isInputMode) parseInputDate(endText) else selectedEnd
    val confirmEnabled = parsedStart != null && parsedEnd != null && !parsedEnd.isBefore(parsedStart)

    val selectStart = stringResource(R.string.usage_stats_timeframe_custom_start)
    val selectEnd = stringResource(R.string.usage_stats_timeframe_custom_end)
    val inputFormat = stringResource(R.string.usage_stats_timeframe_custom_input_format)
    val inputModeContentDescription = stringResource(
        if (isInputMode) {
            R.string.usage_stats_timeframe_custom_switch_to_calendar
        } else {
            R.string.usage_stats_timeframe_custom_switch_to_input
        }
    )

    val headline: String = when {
        parsedStart != null && parsedEnd != null ->
            "${parsedStart.format(fmt)} – ${parsedEnd.format(fmt)}"
        parsedStart != null -> "${parsedStart.format(fmt)} – $selectEnd"
        else -> selectStart
    }

    // only offer months that can contain selectable days
    val months = remember(earliestAvailableDate) {
        val first = maxOf(
            earliestAvailableDate ?: today.minusMonths(MAX_MONTHS_BACK),
            today.minusMonths(MAX_MONTHS_BACK)
        ).withDayOfMonth(1)
        generateSequence(first) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(today.withDayOfMonth(1)) }
            .toList()
    }
    val listState = rememberLazyListState()
    val targetIndex = remember(months) {
        val target = today.withDayOfMonth(1)
        months.indexOfFirst { it == target }.takeIf { it >= 0 } ?: (months.size - 1)
    }
    LaunchedEffect(Unit) { listState.scrollToItem(targetIndex) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.usage_stats_timeframe_custom_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (!isInputMode) {
                            startText = selectedStart?.format(inputFmt) ?: ""
                            endText = selectedEnd?.format(inputFmt) ?: ""
                        } else {
                            parseInputDate(startText)?.let { selectedStart = it }
                            parseInputDate(endText)?.let { selectedEnd = it }
                            val startDate = selectedStart
                            val endDate = selectedEnd
                            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                                selectedEnd = null
                            }
                        }
                        isInputMode = !isInputMode
                    }) {
                        Icon(
                            imageVector = if (isInputMode) Icons.Default.DateRange else Icons.Default.Edit,
                            contentDescription = inputModeContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                )
                HorizontalDivider()

                if (isInputMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            label = { Text(selectStart) },
                            isError = startText.isNotBlank() && parseInputDate(startText) == null,
                            supportingText = { Text(inputFormat) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it },
                            label = { Text(selectEnd) },
                            isError = endText.isNotBlank() && parseInputDate(endText) == null,
                            supportingText = { Text(inputFormat) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 4.dp)
                    ) {
                        items(months) { month ->
                            RangePickerMonthGrid(
                                month = month,
                                today = today,
                                selectedStart = selectedStart,
                                selectedEnd = selectedEnd,
                                earliestAvailableDate = earliestAvailableDate,
                                onDayClick = { date ->
                                    if (selectedStart == null || selectedEnd != null || date.isBefore(selectedStart)) {
                                        selectedStart = date
                                        selectedEnd = null
                                    } else {
                                        selectedEnd = date
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(enabled = confirmEnabled, onClick = {
                        if (parsedStart != null && parsedEnd != null) {
                            onConfirm(parsedStart, parsedEnd)
                        }
                    }) {
                        Text(stringResource(R.string.usage_stats_timeframe_custom_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun RangePickerMonthGrid(
    month: LocalDate,
    today: LocalDate,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    earliestAvailableDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit
) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayColumn = month.withDayOfMonth(1).dayOfWeek.value - 1
    val rangeColor = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            month.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        val dayNames = remember {
            DayOfWeek.values().map { dow ->
                dow.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            dayNames.forEach { name ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        val totalCells = firstDayColumn + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - firstDayColumn + 1
                    if (dayNumber < 1 || dayNumber > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.withDayOfMonth(dayNumber)
                        val isFuture = date.isAfter(today)
                        val isBeforeEarliest = earliestAvailableDate != null && date.isBefore(earliestAvailableDate)
                        val isDisabled = isFuture || isBeforeEarliest
                        val isStart = date == selectedStart
                        val isEnd = date == selectedEnd
                        val isInRange = selectedStart != null && selectedEnd != null &&
                                !date.isBefore(selectedStart) && !date.isAfter(selectedEnd)
                        val isToday = date == today

                        val circleColor: Color = when {
                            isStart || isEnd -> primaryColor
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            isStart || isEnd -> onPrimaryColor
                            isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                            if (isInRange) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.65f)
                                        .align(Alignment.Center)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (!isStart) rangeColor else Color.Transparent)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (!isEnd) rangeColor else Color.Transparent)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(circleColor)
                                    .then(if (!isDisabled) Modifier.clickable { onDayClick(date) } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dayNumber.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor,
                                    fontWeight = if (isToday || isStart || isEnd) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isToday && !isStart && !isEnd) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, primaryColor, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
