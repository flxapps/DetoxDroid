package com.flx_apps.digitaldetox.ui.screens.rule_groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.GroupBudgets
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.Rules
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.PasswordLockGate
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.SettingsLockBannerIfNeeded
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.schedule.ScheduleStatus
import com.flx_apps.digitaldetox.ui.screens.schedule.activeMinutes
import com.flx_apps.digitaldetox.ui.screens.schedule.hourLabel
import com.flx_apps.digitaldetox.ui.screens.schedule.orderedWeekDays
import com.flx_apps.digitaldetox.ui.screens.schedule.rememberCurrentMinute
import com.flx_apps.digitaldetox.ui.screens.schedule.scheduleStatus
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.util.appLabel
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.websites.Websites
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import kotlin.math.abs
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** The waits on offer, in seconds. */
private val WaitChoicesSeconds = listOf(5, 10, 15, 20, 30, 45, 60)

/** The index of the steps card in the list, where the sentence's steps link scrolls to. */
private const val STEPS_ITEM_INDEX = 5

/**
 * A group's screen. The whole rule is one sentence whose parts open their editors; below it,
 * today's use against the budget, the steps that happen as the budget runs down, and when the
 * group counts. However much the group expresses, it stays this one screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleGroupScreen(
    groupId: GroupId,
    viewModel: RuleGroupViewModel = RuleGroupViewModel.withGroupId(groupId),
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
) {
    val context = LocalContext.current
    val version by viewModel.version.collectAsState()
    val groupsChanged by RuleGroups.changes.collectAsState()
    val group = remember(version, groupsChanged) { viewModel.group }
    // the group is one instance whose fields change in place, so nothing below would be told of
    // an edit by itself: every card is keyed on this and redrawn after each
    val revision = version.toLong() * 1_000_003L + groupsChanged
    if (group == null) {
        // deleted, from here or from a restored settings file
        LaunchedEffect(Unit) { navViewModel.onBackPress() }
        return
    }
    val lockToken by CommitmentPasswordFeature.stateToken.collectAsState()
    val brakeToken by EmergencyBrakeFeature.stateToken.collectAsState()
    val locked = remember(lockToken, brakeToken) { FeaturesProvider.areSettingsLocked(groupId) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                navigationIcon = { AppBarBackButton() },
                title = {
                    Text(
                        text = GroupTexts.name(context, group),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Switch(
                        checked = group.isEnabled,
                        enabled = !locked,
                        onCheckedChange = { viewModel.setEnabled(it) }
                    )
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(id = R.string.ruleGroups_menu)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.ruleGroups_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            enabled = !locked,
                            onClick = { menuOpen = false; renaming = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.ruleGroups_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = !locked,
                            onClick = { menuOpen = false; deleting = true }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PasswordLockGate(featureId = groupId, showBanner = false) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding)
            ) {
                item { SettingsLockBannerIfNeeded(featureId = groupId) }
                item { key(revision) { PermissionBanners(group = group, navViewModel = navViewModel) } }
                item {
                    key(revision) { SentenceCard(group = group, onPart = { part ->
                        when (part) {
                            SentencePart.SCOPE ->
                                navViewModel.openRoute(NavigationRoutes.AppExceptions(groupId))

                            SentencePart.BUDGET ->
                                navViewModel.openRoute(NavigationRoutes.GroupBudget(groupId))

                            SentencePart.SCHEDULE ->
                                navViewModel.openRoute(NavigationRoutes.FeatureSchedule(groupId))

                            SentencePart.STEPS ->
                                scope.launch { listState.animateScrollToItem(STEPS_ITEM_INDEX) }
                        }
                    }) }
                }
                item { key(revision) { OverlapNote(group = group) } }
                item { key(revision) { TodayCard(group = group) } }
                item {
                    key(revision) { StepsCard(
                        group = group,
                        onToggle = { step, on ->
                            val missing = viewModel.setStep(step, on)
                            if (missing != null) {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(missing.activationBlockedMessage(context)),
                                        actionLabel = if (missing.activationBlockedHasAction(context)) {
                                            context.getString(R.string.action_go)
                                        } else null,
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        missing.requestPermissions(context, navViewModel)
                                    }
                                }
                            }
                        },
                        onWait = viewModel::setWaitMs,
                        onGrayAfter = viewModel::setGrayAfterMs,
                        onScrollBreakAfter = viewModel::setScrollBreakAfterMs,
                        onTune = { step ->
                            navViewModel.openRoute(NavigationRoutes.ManageFeature(step.feature.id))
                        }
                    ) }
                }
                item {
                    key(revision) { ScheduleCard(
                        group = group,
                        onClick = { navViewModel.openRoute(NavigationRoutes.FeatureSchedule(groupId)) }
                    ) }
                }
                item {
                    key(revision) { Text(
                        text = if (group.hasBudget && group.scheduleRules.isNotEmpty()) {
                            stringResource(
                                id = R.string.ruleGroups_hint_countsFromWindow,
                                group.budgetMs.milliseconds.toHrMinString(context)
                            )
                        } else {
                            stringResource(id = R.string.ruleGroups_hint_tapToChange)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    ) }
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            name = group.name,
            onRename = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(id = R.string.ruleGroups_delete_title, GroupTexts.name(context, group))) },
            text = { Text(stringResource(id = R.string.ruleGroups_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete()
                }) { Text(stringResource(id = R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RenameDialog(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.ruleGroups_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(id = R.string.ruleGroups_name)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.action_cancel)) }
        }
    )
}

/**
 * One card per step that is on but cannot do anything yet for lack of a permission, with the way
 * to grant it.
 */
@Composable
private fun PermissionBanners(group: RuleGroup, navViewModel: NavViewModel) {
    val context = LocalContext.current
    val missing = remember(group.steps) { Rules.stepsMissingPermission(context, group) }
    if (missing.isEmpty()) return
    Column {
        missing.forEach { step ->
            val feature = step.feature as com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
            IconCard(icon = Icons.Default.Warning) {
                Text(
                    text = stringResource(
                        id = R.string.ruleGroups_permission_missing,
                        stringResource(id = step.titleRes),
                        stringResource(id = feature.activationBlockedMessage(context))
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        if (feature.activationBlockedHasAction(context)) {
                            feature.requestPermissions(context, navViewModel)
                        } else {
                            navViewModel.openRoute(NavigationRoutes.ManageFeature(step.feature.id))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(id = R.string.action_go))
                }
            }
        }
    }
}

/** The rule as one sentence of links. */
@Composable
private fun SentenceCard(group: RuleGroup, onPart: (SentencePart) -> Unit) {
    val context = LocalContext.current
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )
    val sentence = remember(group, linkStyle) { GroupSentence.build(context, group, linkStyle, onPart) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = sentence,
            style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 38.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
        )
    }
}

/**
 * Names the other groups that cover apps of this one. Every group that covers an app applies to
 * it, so an overlap is not a mistake, but it is worth seeing.
 */
@Composable
private fun OverlapNote(group: RuleGroup) {
    val context = LocalContext.current
    val overlaps = remember(group, RuleGroups.changes.collectAsState().value) {
        if (!group.canHoldBackApps) return@remember emptyList()
        val apps = Websites.appsOf(group.appExceptions)
        RuleGroups.enabled().filter { it.id != group.id }.mapNotNull { other ->
            val shared = apps.filter { other.appliesTo(it) }.map { context.appLabel(it) }.sorted()
            if (shared.isEmpty()) null else other to shared
        }
    }
    if (overlaps.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 28.dp)) {
        overlaps.forEach { (other, apps) ->
            val named = if (apps.size <= 3) {
                GroupTexts.joinNatural(apps)
            } else {
                pluralStringResource(
                    id = R.plurals.ruleGroups_scope_andMore, count = apps.size - 2,
                    GroupTexts.joinNatural(apps.take(2)), apps.size - 2
                )
            }
            Text(
                text = stringResource(
                    id = R.string.ruleGroups_overlap, GroupTexts.name(context, other), named
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Today's use against the budget, with the big number of what is left. */
@Composable
private fun TodayCard(group: RuleGroup) {
    if (!group.hasBudget) return
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    val active = remember(group, now, EmergencyBrakeFeature.stateToken.collectAsState().value) {
        group.isActiveAt(now)
    }
    val usedMs = remember(group, now) { GroupBudgets.usedMs(group) }
    val leftMs = (group.budgetMs - usedMs).coerceAtLeast(0L)
    val graysInside = group.grays && !group.graysFromStart && group.grayAfterMs < group.budgetMs
    GroupCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    id = when (group.budgetPeriod) {
                        BudgetPeriod.DAY -> R.string.ruleGroups_today
                        BudgetPeriod.HOUR -> R.string.ruleGroups_thisHour
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    id = R.string.ruleGroups_card_usedOf,
                    usedMs.coerceAtMost(group.budgetMs).milliseconds.toHrMinString(context),
                    group.budgetMs.milliseconds.toHrMinString(context)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!active && !group.isEnabled) {
            Text(
                text = stringResource(id = R.string.ruleGroups_today_groupOff),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else if (!active) {
            val until = (scheduleStatus(group.scheduleRules, now) as? ScheduleStatus.InactiveUntil)?.until
            Text(
                text = if (until != null) {
                    stringResource(id = R.string.ruleGroups_status_offUntil, GroupTexts.time(until.toLocalTime()))
                } else {
                    stringResource(id = R.string.state_off)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = stringResource(id = R.string.ruleGroups_today_freshBudget),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 12.dp)) {
                if (leftMs > 0L) {
                    val leftMinutes = (leftMs + 59_999L) / 60_000L
                    Text(
                        text = leftMinutes.minutes.toHrMinString(context),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.ruleGroups_today_left),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                    )
                } else {
                    Text(
                        text = stringResource(
                            id = when {
                                group.locks && group.canHoldBackApps -> R.string.ruleGroups_status_locked
                                group.grays -> R.string.ruleGroups_status_gray
                                else -> R.string.today_budget_usedUp
                            }
                        ).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        ThresholdBar(
            fraction = (usedMs.toFloat() / group.budgetMs).coerceIn(0f, 1f),
            marker = if (graysInside) group.grayAfterMs.toFloat() / group.budgetMs else null
        )
        val captions = listOfNotNull(
            if (graysInside) {
                stringResource(id = R.string.ruleGroups_today_grayFrom, GroupTexts.minutes(context, group.grayAfterMs))
            } else null,
            when {
                group.locks && group.canHoldBackApps ->
                    stringResource(id = R.string.ruleGroups_today_lockedAt, GroupTexts.minutes(context, group.budgetMs))

                group.grays && !group.graysFromStart && !graysInside ->
                    stringResource(id = R.string.ruleGroups_today_grayAt, GroupTexts.minutes(context, group.budgetMs))

                else -> null
            }
        )
        if (captions.isNotEmpty()) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                captions.forEachIndexed { index, caption ->
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = if (index == captions.lastIndex && captions.size > 1) {
                            androidx.compose.ui.text.style.TextAlign.End
                        } else {
                            androidx.compose.ui.text.style.TextAlign.Start
                        }
                    )
                }
            }
        }
    }
}

/** A budget bar with an optional mark where the gray step starts. */
@Composable
private fun ThresholdBar(fraction: Float, marker: Float?) {
    Box(modifier = Modifier.fillMaxWidth()) {
        BudgetBar(fraction = fraction, height = 10.dp)
        marker?.let {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (it > 0f) Spacer(modifier = Modifier.weight(it))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.onSurface)
                )
                if (it < 1f) Spacer(modifier = Modifier.weight(1f - it))
            }
        }
    }
}

/**
 * The steps, in the order they take hold and only ever stricter: a timeline of the budget on
 * top, then a row per step with its switch and its value.
 */
@Composable
private fun StepsCard(
    group: RuleGroup,
    onToggle: (Step, Boolean) -> Unit,
    onWait: (Long) -> Unit,
    onGrayAfter: (Long) -> Unit,
    onScrollBreakAfter: (Long) -> Unit,
    onTune: (Step) -> Unit,
) {
    val context = LocalContext.current
    val locked = LocalSettingsLocked.current
    var picking by remember { mutableStateOf<Step?>(null) }
    GroupCard(contentPadding = PaddingValues(vertical = 16.dp)) {
        Text(
            text = stringResource(id = R.string.ruleGroups_steps_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        StepsTimeline(group = group, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        Step.entries.forEach { step ->
            val available = step != Step.LOCK && step != Step.WAIT || group.canHoldBackApps
            val on = group.hasStep(step)
            // the lock has no value of its own, and gray only has one inside a budget
            val hasValue = on && when (step) {
                Step.WAIT, Step.SCROLL_BREAK -> true
                Step.GRAY -> group.hasBudget
                Step.LOCK -> false
            }
            StepRow(
                step = step,
                on = on,
                enabled = available && !locked,
                subtitle = stepSubtitle(group, step, available),
                hasValue = hasValue,
                onToggle = { onToggle(step, it) },
                onClick = { if (hasValue) picking = step },
                onTune = { onTune(step) }
            )
        }
    }
    when (picking) {
        Step.WAIT -> NumberPickerDialog(
            titleText = stringResource(id = Step.WAIT.titleRes),
            initialValue = (group.waitBeforeOpeningMs / 1000L).toInt(),
            range = WaitChoicesSeconds,
            label = { context.getString(R.string.duration_seconds_short, it) },
            onValueSelected = { onWait(it * 1000L) },
            onDismissRequest = { picking = null }
        )

        Step.GRAY -> {
            val budgetMinutes = (group.budgetMs / 60_000L).toInt()
            val choices = (0..budgetMinutes step 5).toList()
            val current = (group.grayAfterMs / 60_000L).toInt()
            NumberPickerDialog(
                titleText = stringResource(id = R.string.ruleGroups_step_gray_from),
                // a threshold off the grid (a migrated budget can be any minute) starts out on
                // the nearest choice
                initialValue = choices.minBy { abs(it - current) },
                range = choices,
                label = {
                    if (it == 0) context.getString(R.string.ruleGroups_step_gray_fromStart)
                    else context.getString(R.string.ruleGroups_step_gray_fromMinutes, it)
                },
                onValueSelected = { onGrayAfter(it * 60_000L) },
                onDismissRequest = { picking = null }
            )
        }

        Step.SCROLL_BREAK -> NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_doomScrolling_timeUntilWarning),
            initialValue = (group.scrollBreakAfterMs / 60_000L).toInt().coerceAtLeast(1),
            range = 1..60,
            label = { context.getString(R.string.duration_minutes, it) },
            onValueSelected = { onScrollBreakAfter(it * 60_000L) },
            onDismissRequest = { picking = null }
        )

        else -> {}
    }
}

@Composable
private fun stepSubtitle(group: RuleGroup, step: Step, available: Boolean): String {
    val context = LocalContext.current
    if (!available) return stringResource(id = R.string.ruleGroups_step_onlySelectedApps)
    return when (step) {
        Step.WAIT -> if (group.waits) {
            stringResource(id = R.string.ruleGroups_step_wait_on, GroupTexts.seconds(context, group.waitBeforeOpeningMs))
        } else {
            stringResource(id = R.string.ruleGroups_step_wait_off)
        }

        Step.GRAY -> when {
            !group.grays -> stringResource(id = R.string.ruleGroups_step_gray_off)
            !group.hasBudget || group.graysFromStart -> stringResource(id = R.string.ruleGroups_step_gray_always)
            else -> stringResource(id = R.string.ruleGroups_step_gray_on, GroupTexts.minutes(context, group.grayAfterMs))
        }

        Step.SCROLL_BREAK -> if (group.breaksScrolling) {
            stringResource(id = R.string.ruleGroups_step_scrollBreak_on, GroupTexts.minutes(context, group.scrollBreakAfterMs))
        } else {
            stringResource(id = R.string.ruleGroups_step_scrollBreak_off)
        }

        Step.LOCK -> when {
            !group.locks -> stringResource(id = R.string.ruleGroups_step_lock_off)
            group.hasBudget -> stringResource(id = R.string.ruleGroups_step_lock_on, GroupTexts.minutes(context, group.budgetMs))
            else -> stringResource(id = R.string.ruleGroups_step_lock_always)
        }
    }
}

@Composable
private fun StepRow(
    step: Step,
    on: Boolean,
    enabled: Boolean,
    subtitle: String,
    hasValue: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onTune: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled && hasValue, onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (on) colors.primaryContainer else colors.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(text = stringResource(id = step.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Switch(checked = on, onCheckedChange = onToggle, enabled = enabled)
        IconButton(onClick = onTune) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(id = R.string.ruleGroups_step_tune),
                tint = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * The budget as a bar: in color until gray starts, gray until the budget ends, a dark block where
 * the lock takes hold. Without a budget, one block in the color of what the group does.
 */
@Composable
private fun StepsTimeline(group: RuleGroup, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val grayColor = colors.outline
    val lockColor = colors.onSurface
    val locks = group.locks && group.canHoldBackApps
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (group.hasBudget) {
                val grayFrom = when {
                    !group.grays -> group.budgetMs
                    else -> group.grayAfterMs.coerceIn(0L, group.budgetMs)
                }
                if (grayFrom > 0L) Segment(colors.primary, grayFrom.toFloat())
                if (group.budgetMs - grayFrom > 0L) Segment(grayColor, (group.budgetMs - grayFrom).toFloat())
                if (locks) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight()
                            .background(lockColor)
                    )
                }
            } else {
                Segment(
                    when {
                        locks -> lockColor
                        group.grays -> grayColor
                        else -> colors.primary
                    }, 1f
                )
            }
        }
        val labels = listOfNotNull(
            if (group.waits) {
                stringResource(id = R.string.ruleGroups_timeline_wait, GroupTexts.seconds(context, group.waitBeforeOpeningMs))
            } else null,
            when {
                !group.grays -> null
                group.hasBudget && !group.graysFromStart ->
                    stringResource(id = R.string.ruleGroups_timeline_grayFrom, GroupTexts.minutes(context, group.grayAfterMs))

                else -> stringResource(id = R.string.ruleGroups_timeline_grayAlways)
            },
            when {
                !locks -> null
                group.hasBudget ->
                    stringResource(id = R.string.ruleGroups_timeline_lockedAt, GroupTexts.minutes(context, group.budgetMs))

                else -> stringResource(id = R.string.ruleGroups_timeline_locked)
            }
        )
        if (labels.isNotEmpty()) {
            Text(
                text = labels.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(color: Color, weight: Float) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(color)
    )
}

/** When the group counts: today's hours, the days of the week, and the schedule in words. */
@Composable
private fun ScheduleCard(group: RuleGroup, onClick: () -> Unit) {
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    val locale = Locale.getDefault()
    val rules = group.scheduleRules
    val colors = MaterialTheme.colorScheme
    val todayRanges = remember(rules, now.dayOfWeek) { activeMinutesOrAll(rules, now.dayOfWeek) }
    GroupCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.ruleGroups_when_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = GroupTexts.scheduleShort(context, rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(24.dp)
        ) {
            (0 until 24).forEach { hour ->
                val active = todayRanges.any { it.first < (hour + 1) * 60 && it.last >= hour * 60 }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) colors.primary else colors.surfaceContainerHighest)
                )
            }
        }
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)) {
            listOf(0, 12, 24).forEachIndexed { index, hour ->
                Text(
                    text = hourLabel(hour % 24, locale).let { if (hour == 24) "24" else it },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = when (index) {
                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                        1 -> androidx.compose.ui.text.style.TextAlign.Center
                        else -> androidx.compose.ui.text.style.TextAlign.End
                    }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            orderedWeekDays(locale).forEach { day ->
                val active = rules.isEmpty() || activeMinutes(rules, day).isNotEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.primary else colors.surfaceContainerHighest)
                ) {
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) colors.onPrimary else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The minutes of [day] the group is on: all of them without rules. */
private fun activeMinutesOrAll(rules: Collection<FeatureScheduleRule>, day: DayOfWeek): List<IntRange> =
    if (rules.isEmpty()) listOf(0..24 * 60 - 1) else activeMinutes(rules, day)

/** The card the group screen is built from. */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
