package com.flx_apps.digitaldetox.ui.screens.logs

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.util.LogEntry
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lines of a message shown before an entry is expanded. */
private const val COLLAPSED_LINES = 4

private val entryTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/**
 * The [com.flx_apps.digitaldetox.util.DebugLog], newest entry first and grouped by the run of the
 * app that wrote it. Entries can be searched, narrowed down by level and tag, expanded to their
 * full message (stack traces included), copied one at a time, or shared as a file.
 *
 * The list follows new entries as long as it is scrolled to the top, and holds still otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogViewerScreen(
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
    viewModel: LogViewerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // kept here rather than read back from the view model, so typing never waits for a round trip
    var query by rememberSaveable { mutableStateOf(filter.query) }
    var searchOpen by rememberSaveable { mutableStateOf(filter.query.isNotEmpty()) }
    val closeSearch = {
        searchOpen = false
        query = ""
    }
    val expandedIds = remember { mutableStateMapOf<Long, Boolean>() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var followNewest by remember { mutableStateOf(true) }
    val copiedText = stringResource(id = R.string.debugLog_copied)

    LaunchedEffect(query) { viewModel.setQuery(query) }
    BackHandler(enabled = searchOpen, onBack = closeSearch)
    // only the user's own scrolling decides whether the list follows new entries: an entry coming
    // in above moves the list away from the top as well
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect {
            followNewest =
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val newestId = state?.runs?.firstOrNull()?.entries?.firstOrNull()?.id
    LaunchedEffect(newestId, filter) {
        if (followNewest) listState.scrollToItem(0)
    }
    val showJumpToNewest by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        SearchField(
                            query = query, onQueryChange = { query = it }, onClose = closeSearch
                        )
                    } else {
                        Column {
                            Text(text = stringResource(id = R.string.debugLog_title))
                            state?.let { EntryCount(it) }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navViewModel.onBackPress() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(id = R.string.action_search)
                            )
                        }
                    }
                    IconButton(
                        enabled = (state?.shown ?: 0) > 0,
                        onClick = {
                            state?.let { current ->
                                scope.launch {
                                    runCatching { shareDebugLog(context, current, filter) }
                                        .onFailure { Timber.w(it, "Could not share the debug log") }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(id = R.string.debugLog_share)
                        )
                    }
                    // leaves the search field room, and clearing is not what searching is for
                    if (!searchOpen) {
                        IconButton(
                            enabled = (state?.total ?: 0) > 0, onClick = { confirmClear = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(id = R.string.debugLog_clear)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showJumpToNewest,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        followNewest = true
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(id = R.string.debugLog_newest)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            val current = state ?: return@Column
            // the chips scroll away with the entries, so a small screen keeps room for the log
            val filterChips = @Composable {
                FilterChips(
                    filter = filter,
                    levelCounts = current.levelCounts,
                    onLevelSelected = viewModel::setLevel,
                    onTagRemoved = { viewModel.setTag(null) }
                )
            }
            when {
                current.total == 0 -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = stringResource(id = R.string.debugLog_empty_title),
                    message = stringResource(id = R.string.debugLog_empty_message)
                )

                current.shown == 0 -> {
                    filterChips()
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = stringResource(id = R.string.debugLog_noMatch_title)
                    ) {
                        TextButton(
                            onClick = {
                                closeSearch()
                                viewModel.resetFilter()
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(text = stringResource(id = R.string.debugLog_noMatch_reset))
                        }
                    }
                }

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom = paddingValues.calculateBottomPadding() + 72.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "filters", contentType = "filters") { filterChips() }
                    current.runs.forEach { run ->
                        stickyHeader(key = "run-${run.pid}-${run.key}", contentType = "run") {
                            RunHeader(run)
                        }
                        items(run.entries, key = { it.id }, contentType = { "entry" }) { entry ->
                            LogEntryRow(
                                entry = entry,
                                query = filter.query.trim(),
                                expanded = expandedIds[entry.id] == true,
                                onToggle = {
                                    if (expandedIds.remove(entry.id) == null) {
                                        expandedIds[entry.id] = true
                                    }
                                },
                                onCopy = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    clipboard.setText(AnnotatedString(entry.toLogcatLine()))
                                    // Android 13 and later confirm a copy on their own
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        scope.launch { snackbarHostState.showSnackbar(copiedText) }
                                    }
                                },
                                onTagClick = viewModel::setTag
                            )
                        }
                    }
                    item(key = "hint", contentType = "hint") {
                        Text(
                            text = stringResource(id = R.string.debugLog_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(text = stringResource(id = R.string.debugLog_clear_title)) },
            text = { Text(text = stringResource(id = R.string.debugLog_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    expandedIds.clear()
                    viewModel.clearLog()
                }) {
                    Text(text = stringResource(id = R.string.debugLog_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(text = stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun EntryCount(state: LogViewerState) {
    val numbers = NumberFormat.getIntegerInstance()
    Text(
        text = if (state.shown == state.total) {
            pluralStringResource(
                id = R.plurals.debugLog_entries, count = state.total, numbers.format(state.total)
            )
        } else {
            pluralStringResource(
                id = R.plurals.debugLog_entriesShown,
                count = state.total,
                numbers.format(state.shown),
                numbers.format(state.total)
            )
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Takes the title's place while searching, like the search in the app lists. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(id = R.string.action_search),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.action_search_close)
                )
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp)
            .focusRequester(focusRequester)
    )
}

/** The tag being filtered by, if any, then one chip per level, each with what it would show. */
@Composable
private fun FilterChips(
    filter: LogFilter,
    levelCounts: Map<LogLevelFilter, Int>?,
    onLevelSelected: (LogLevelFilter) -> Unit,
    onTagRemoved: () -> Unit,
) {
    val numbers = NumberFormat.getIntegerInstance()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        filter.tag?.let { tag ->
            InputChip(
                selected = true,
                onClick = onTagRemoved,
                label = { Text(text = tag, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(id = R.string.debugLog_tag_remove),
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            )
        }
        LogLevelFilter.entries.forEach { level ->
            val dotColor = levelColor(level.minPriority)
            FilterChip(
                selected = filter.level == level,
                onClick = { onLevelSelected(level) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(id = level.labelRes))
                        levelCounts?.get(level)?.let {
                            Text(
                                text = numbers.format(it),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                },
                leadingIcon = if (level == LogLevelFilter.ALL) null else {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            )
        }
    }
}

private val LogLevelFilter.labelRes: Int
    get() = when (this) {
        LogLevelFilter.ALL -> R.string.debugLog_level_all
        LogLevelFilter.INFO -> R.string.debugLog_level_info
        LogLevelFilter.WARNINGS -> R.string.debugLog_level_warnings
        LogLevelFilter.ERRORS -> R.string.debugLog_level_errors
    }

/** Stays at the top of the list while the run's entries scroll by below it. */
@Composable
private fun RunHeader(run: LogRun) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val start = formatRunTime(context, run.startedAt, now)
    val end = formatRunTime(context, run.endedAt, run.startedAt)
    val label = when {
        run.isCurrent -> stringResource(id = R.string.debugLog_run_current, start)
        // a run that did not last a minute
        end == formatRunTime(context, run.startedAt, run.startedAt) -> start
        else -> stringResource(id = R.string.debugLog_run_past, start, end)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (run.isCurrent) Icons.Default.Sync else Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Text(
            text = "PID ${run.pid}",
            style = MaterialTheme.typography.labelSmall.monospace(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A time of day, with the date only when it is not the same day as [reference]. */
private fun formatRunTime(context: Context, timestamp: Long, reference: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(timestamp).atZone(zone)
    val sameDay = Instant.ofEpochMilli(reference).atZone(zone).toLocalDate() == time.toLocalDate()
    val hours = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(
        Locale.getDefault(), if (sameDay) hours else "dMMM$hours"
    )
    return DateTimeFormatter.ofPattern(pattern).format(time)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogEntryRow(
    entry: LogEntry,
    query: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val color = levelColor(entry.priority)
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer
    )
    // a stack frame's indent and "at" stay on the line of the frame they belong to, instead of
    // wrapping onto one of their own ahead of a name too long to fit next to them
    val text = remember(entry.message) { entry.message.replace("\tat ", "\u00A0\u00A0at\u00A0") }
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val message = remember(text, query, highlight) { highlightMatches(text, query, highlight) }
    val time = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(entryTimeFormat)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (entry.priority >= Log.ERROR) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
            // only entries worth noticing get a stripe, so they stand out among the chatter
            .drawBehind {
                if (entry.priority >= Log.INFO) {
                    drawRect(color, size = Size(3.dp.toPx(), size.height))
                }
            }
            .combinedClickable(onClick = onToggle, onLongClick = onCopy)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.16f))
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = priorityLetter(entry.priority).toString(),
                    style = MaterialTheme.typography.labelMedium.monospace()
                        .copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.monospace(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
            entry.tag?.let { tag ->
                Text(
                    text = highlightMatches(tag, query, highlight),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onTagClick(tag) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.monospace().copy(lineHeight = 16.sp),
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!expanded && lineCount > COLLAPSED_LINES) {
            val hidden = lineCount - COLLAPSED_LINES
            Text(
                text = pluralStringResource(id = R.plurals.debugLog_moreLines, count = hidden, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    action: @Composable () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        action()
    }
}

/** Errors in the theme's error color, warnings in amber, info in the primary color. */
@Composable
private fun levelColor(priority: Int): Color {
    val colors = MaterialTheme.colorScheme
    return when {
        priority >= Log.ERROR -> colors.error
        priority == Log.WARN -> if (colors.surface.luminance() < 0.5f) {
            Color(0xFFFFB74D)
        } else {
            Color(0xFFB26A00)
        }
        priority == Log.INFO -> colors.primary
        priority == Log.DEBUG -> colors.onSurfaceVariant
        else -> colors.outline
    }
}

/** The type's own letter spacing spreads monospaced text too far apart. */
private fun TextStyle.monospace() = copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp)

private fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)
    // the non-breaking spaces set into stack frames are still spaces to someone searching
    val searchable = text.replace('\u00A0', ' ')
    return buildAnnotatedString {
        append(text)
        var start = searchable.indexOf(query, ignoreCase = true)
        while (start >= 0) {
            addStyle(style, start, start + query.length)
            start = searchable.indexOf(query, start + query.length, ignoreCase = true)
        }
    }
}
