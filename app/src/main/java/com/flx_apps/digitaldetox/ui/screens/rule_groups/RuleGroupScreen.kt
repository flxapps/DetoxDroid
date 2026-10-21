package com.flx_apps.digitaldetox.ui.screens.rule_groups

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
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
import com.flx_apps.digitaldetox.ui.theme.brandTone
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.util.appLabel
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.websites.Websites
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import kotlin.math.abs
import java.time.format.TextStyle
import java.util.Locale

/** The waits on offer, in seconds. */
private val WaitChoicesSeconds = listOf(5, 10, 15, 20, 30, 45, 60)

/** The index of the steps card in the list, where the sentence's steps link scrolls to. */
private const val STEPS_ITEM_INDEX = 5

/** How many time windows the schedule card lists before it counts the rest. */
private const val SCHEDULE_LINES = 4

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
    // re-read when the screen comes back, from the system settings the note sends the user to
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    val missingPermissions = remember(revision, lifecycleState) {
        Rules.stepsMissingPermission(context, group ?: return@remember emptySet())
    }
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
    // the step whose value is being picked, from a row or from a value in the sentence
    var picking by remember { mutableStateOf<Step?>(null) }

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
                if (missingPermissions.isNotEmpty()) {
                    item {
                        key(revision) {
                            PermissionNotes(steps = missingPermissions, navViewModel = navViewModel)
                        }
                    }
                }
                if (group.idleSteps.isNotEmpty()) {
                    item {
                        key(revision) {
                            IdleStepsNote(
                                group = group,
                                onSetBudget = { navViewModel.openRoute(NavigationRoutes.GroupBudget(groupId)) },
                                onPickGray = { picking = Step.GRAY }
                            )
                        }
                    }
                }
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

                            SentencePart.WAIT -> picking = Step.WAIT
                            SentencePart.GRAY -> picking = Step.GRAY
                            SentencePart.SCROLL -> picking = Step.SCROLL_BREAK
                        }
                    }) }
                }
                item { key(revision) { OverlapNote(group = group) } }
                item {
                    key(revision) { TodayCard(
                        group = group,
                        onClick = { navViewModel.openRoute(NavigationRoutes.GroupBudget(groupId)) }
                    ) }
                }
                item {
                    key(revision) { StepsCard(
                        group = group,
                        picking = picking,
                        onPickingChange = { picking = it },
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
                        onOpenFeature = { feature ->
                            navViewModel.openRoute(NavigationRoutes.ManageFeature(feature.id))
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
                                GroupTexts.duration(context, group.budgetMs)
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
 * One note per missing permission, naming the steps that wait for it, with the way to grant it.
 * Per permission rather than per step: the wait, the lock and the scroll breaks all want the same
 * one, and three cards saying so would push the rule off the screen.
 */
@Composable
private fun PermissionNotes(steps: Set<Step>, navViewModel: NavViewModel) {
    val context = LocalContext.current
    val byPermission = remember(steps) {
        steps.groupBy { (it.feature as NeedsPermissionsFeature).activationBlockedMessage(context) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        byPermission.forEach { (messageRes, waiting) ->
            val feature = waiting.first().feature as NeedsPermissionsFeature
            val hasAction = feature.activationBlockedHasAction(context)
            val names = GroupTexts.joinNatural(waiting.map { stringResource(id = it.titleRes) })
            PermissionNote(
                title = pluralStringResource(
                    id = R.plurals.ruleGroups_permission_steps, count = waiting.size, names
                ),
                message = stringResource(id = messageRes),
                actionLabel = stringResource(
                    id = if (hasAction) R.string.action_grantPermission else R.string.ruleGroups_step_tune
                ),
                onAction = {
                    if (hasAction) {
                        feature.requestPermissions(context, navViewModel)
                    } else {
                        navViewModel.openRoute(NavigationRoutes.ManageFeature(waiting.first().feature.id))
                    }
                }
            )
        }
    }
}

/**
 * Says when a step is on that the lock never lets run, and offers the one change that would:
 * a budget for the lock to wait for, or an earlier start for the gray.
 */
@Composable
private fun IdleStepsNote(group: RuleGroup, onSetBudget: () -> Unit, onPickGray: () -> Unit) {
    val idle = group.idleSteps
    if (group.hasBudget) {
        Note(
            icon = Icons.Outlined.Info,
            title = stringResource(id = R.string.ruleGroups_idle_grayAtLock_title),
            message = stringResource(id = R.string.ruleGroups_idle_grayAtLock_message),
            actionLabel = stringResource(id = R.string.ruleGroups_idle_grayAtLock_action),
            onAction = onPickGray
        )
    } else {
        val names = GroupTexts.joinNatural(idle.map { stringResource(id = it.titleRes) })
        Note(
            icon = Icons.Outlined.Info,
            title = stringResource(id = R.string.ruleGroups_idle_noBudget_title),
            message = pluralStringResource(id = R.plurals.ruleGroups_idle_noBudget_message, count = idle.size, names),
            actionLabel = stringResource(id = R.string.ruleGroups_idle_setBudget),
            onAction = onSetBudget
        )
    }
}

@Composable
private fun PermissionNote(title: String, message: String, actionLabel: String, onAction: () -> Unit) =
    Note(Icons.Outlined.WarningAmber, title, message, actionLabel, onAction)

/** A quiet line with a small sign and the one action it asks for. */
@Composable
private fun Note(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAction) { Text(text = actionLabel) }
        }
    }
}

/**
 * The rule as a sentence whose changeable parts are links, the group's apps as icons above it,
 * and below it what happens as the budget runs down.
 */
@Composable
private fun SentenceCard(group: RuleGroup, onPart: (SentencePart) -> Unit) {
    val context = LocalContext.current
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )
    val sentence = remember(group, linkStyle) { GroupSentence.build(context, group, linkStyle, onPart) }
    val apps = remember(group.appExceptions) { Websites.appsOf(group.appExceptions).sorted() }
    val websites = remember(group.appExceptions) { Websites.websitesOf(group.appExceptions).sorted() }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            if (apps.isNotEmpty() || websites.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPart(SentencePart.SCOPE) }
                ) {
                    ScopeIcons(apps = apps, websites = websites, allApps = !group.canHoldBackApps)
                }
            }
            Text(
                text = sentence.main,
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 34.sp),
                fontWeight = FontWeight.Medium
            )
            sentence.steps?.let {
                // what happens as the budget runs down, or that there is none, a step down in
                // size: the values in it are links, the words around them are not
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
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

/** Today's use against the budget, with the big number of what is left. Opens the budget. */
@Composable
private fun TodayCard(group: RuleGroup, onClick: () -> Unit) {
    if (!group.hasBudget) return
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    val active = remember(group, now, EmergencyBrakeFeature.stateToken.collectAsState().value) {
        group.isActiveAt(now)
    }
    val usedMs = remember(group, now) { GroupBudgets.usedMs(group) }
    val leftMs = (group.budgetMs - usedMs).coerceAtLeast(0L)
    val graysInside = group.grays && !group.graysFromStart && group.grayAfterMs < group.budgetMs
    GroupCard(onClick = onClick) {
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
                    GroupTexts.duration(context, usedMs.coerceAtMost(group.budgetMs)),
                    GroupTexts.duration(context, group.budgetMs)
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
                        text = GroupTexts.duration(context, leftMinutes * 60_000L),
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
        val grayTone = brandTone(Step.GRAY.colorRes)
        val lockTone = brandTone(Step.LOCK.colorRes)
        BudgetShapeBar(group = group, usedMs = usedMs)
        // each caption in the color of its step
        val captions = listOfNotNull(
            if (graysInside) {
                stringResource(id = R.string.ruleGroups_today_grayFrom, GroupTexts.minutes(context, group.grayAfterMs)) to grayTone.content
            } else null,
            when {
                group.locks && group.canHoldBackApps ->
                    stringResource(id = R.string.ruleGroups_today_lockedAt, GroupTexts.minutes(context, group.budgetMs)) to lockTone.content

                group.grays && !group.graysFromStart && !graysInside ->
                    stringResource(id = R.string.ruleGroups_today_grayAt, GroupTexts.minutes(context, group.budgetMs)) to grayTone.content

                else -> null
            }
        )
        if (captions.isNotEmpty()) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                captions.forEachIndexed { index, (caption, color) ->
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
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

/**
 * The budget as a bar, with how much of it is used: the theme's color until the gray step
 * starts, the gray step's color from there to the end, and a block in the lock's where that takes
 * hold. The used stretch is drawn in full strength, the rest faintly, so one bar says both what
 * the budget looks like and where today stands in it.
 */
@Composable
private fun BudgetShapeBar(group: RuleGroup, usedMs: Long) {
    val colors = MaterialTheme.colorScheme
    val colorPart = colors.primary
    val grayPart = brandTone(Step.GRAY.colorRes).content
    val lockPart = brandTone(Step.LOCK.colorRes).content
    val locks = group.locks && group.canHoldBackApps
    val grayFromMs = if (group.grays) group.grayAfterMs.coerceIn(0L, group.budgetMs) else group.budgetMs
    val grayAt = grayFromMs.toFloat() / group.budgetMs
    val fraction = (usedMs.toFloat() / group.budgetMs).coerceIn(0f, 1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
    ) {
        val gap = 2.dp.toPx()
        val lockWidth = if (locks) size.height else 0f
        val trackWidth = size.width - lockWidth - (if (locks) gap else 0f)
        val grayStart = trackWidth * grayAt
        val radius = CornerRadius(size.height / 2f)
        fun drawShape(alpha: Float) {
            if (grayStart > 0f) {
                drawRoundRect(
                    color = colorPart.copy(alpha = alpha),
                    size = Size(grayStart, size.height),
                    cornerRadius = radius
                )
            }
            if (grayStart < trackWidth) {
                val from = if (grayStart > 0f) grayStart + gap else 0f
                drawRoundRect(
                    color = grayPart.copy(alpha = alpha),
                    topLeft = Offset(from, 0f),
                    size = Size((trackWidth - from).coerceAtLeast(0f), size.height),
                    cornerRadius = radius
                )
            }
        }
        drawShape(alpha = 0.35f)
        if (fraction > 0f) clipRect(right = trackWidth * fraction) { drawShape(alpha = 1f) }
        if (locks) {
            drawRoundRect(
                color = lockPart,
                topLeft = Offset(size.width - lockWidth, 0f),
                size = Size(lockWidth, size.height),
                cornerRadius = radius
            )
        }
    }
}

/**
 * The steps, in the order they take hold and only ever stricter: a row per step with its switch
 * and its value, and at the bottom the way to the screens that say how the steps work on this
 * phone, which is the same for every group.
 */
@Composable
private fun StepsCard(
    group: RuleGroup,
    onToggle: (Step, Boolean) -> Unit,
    onWait: (Long) -> Unit,
    onGrayAfter: (Long) -> Unit,
    onScrollBreakAfter: (Long) -> Unit,
    picking: Step?,
    onPickingChange: (Step?) -> Unit,
    onOpenFeature: (Feature) -> Unit,
) {
    val context = LocalContext.current
    val locked = LocalSettingsLocked.current
    var showingHowItWorks by remember { mutableStateOf(false) }
    GroupCard(contentPadding = PaddingValues(vertical = 16.dp)) {
        Text(
            text = stringResource(id = R.string.ruleGroups_steps_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
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
                onClick = { if (hasValue) onPickingChange(step) },
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
        )
        HowItWorksRow(onClick = { showingHowItWorks = true })
    }
    if (showingHowItWorks) {
        StepFeaturesSheet(
            onOpen = { showingHowItWorks = false; onOpenFeature(it) },
            onDismiss = { showingHowItWorks = false }
        )
    }
    when (picking) {
        Step.WAIT -> NumberPickerDialog(
            titleText = stringResource(id = Step.WAIT.titleRes),
            initialValue = (group.waitBeforeOpeningMs / 1000L).toInt(),
            range = WaitChoicesSeconds,
            label = { context.getString(R.string.duration_seconds_short, it) },
            onValueSelected = { onWait(it * 1000L) },
            onDismissRequest = { onPickingChange(null) }
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
                onDismissRequest = { onPickingChange(null) }
            )
        }

        Step.SCROLL_BREAK -> NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_doomScrolling_timeUntilWarning),
            initialValue = (group.scrollBreakAfterMs / 60_000L).toInt().coerceAtLeast(1),
            range = 1..60,
            label = { context.getString(R.string.duration_minutes, it) },
            onValueSelected = { onScrollBreakAfter(it * 60_000L) },
            onDismissRequest = { onPickingChange(null) }
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
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled && hasValue, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        val tone = brandTone(step.colorRes)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (on) tone.container else colors.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = if (on) tone.content else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(text = stringResource(id = step.titleRes), style = MaterialTheme.typography.bodyLarge)
            // a value that can be changed is underlined like the parts of the sentence above
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasValue && enabled) colors.primary else colors.onSurfaceVariant,
                textDecoration = if (hasValue && enabled) TextDecoration.Underline else null
            )
        }
        Switch(checked = on, onCheckedChange = onToggle, enabled = enabled)
    }
}

/** The footer of the steps card: the way to the settings of the features behind the steps. */
@Composable
private fun HowItWorksRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = stringResource(id = R.string.ruleGroups_steps_featureSettings),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(id = R.string.ruleGroups_steps_featureSettings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant
        )
    }
}

/**
 * The three features behind the four steps, each leading to its settings: how gray looks, how
 * a lock keeps apps away, how scrolling is judged. Those are about the phone and shared by every
 * group, which is why they are not on the rows above.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepFeaturesSheet(onOpen: (Feature) -> Unit, onDismiss: () -> Unit) {
    val features = remember { Step.entries.map { it.feature }.distinct() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(id = R.string.ruleGroups_steps_featureSettings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = stringResource(id = R.string.ruleGroups_steps_featureSettings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 8.dp)
            )
            features.forEach { feature ->
                ListItem(
                    headlineContent = { Text(stringResource(id = feature.texts.title)) },
                    supportingContent = { Text(stringResource(id = feature.texts.subtitle)) },
                    leadingContent = {
                        Icon(painter = painterResource(id = feature.iconRes), contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(feature) }
                )
            }
        }
    }
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
        if (rules.size > 1) {
            // windows on different days do not fit one bar and one row of dots: list them
            val lines = remember(rules) {
                rules.sortedWith(compareBy({ it.daysOfWeek.minOfOrNull { day -> day.value } ?: 0 }, { it.start }))
                    .map { GroupTexts.scheduleShort(context, listOf(it)) }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                lines.take(SCHEDULE_LINES).forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
                val more = lines.size - SCHEDULE_LINES
                if (more > 0) {
                    Text(
                        text = pluralStringResource(id = R.plurals.ruleGroups_schedule_moreWindows, count = more, more),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 26.dp)
                    )
                }
            }
            return@GroupCard
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
