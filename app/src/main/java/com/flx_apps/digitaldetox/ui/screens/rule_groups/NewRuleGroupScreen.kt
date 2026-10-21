package com.flx_apps.digitaldetox.ui.screens.rule_groups

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.AddWebsiteField
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.theme.brandTone
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow
import com.flx_apps.digitaldetox.ui.widgets.OptionChip
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionListItem
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.websites.Websites
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The short setup of a new group, the way the onboarding sets up the first rules: pick a
 * template, say what counts, say how strict, done. Every detail can be changed on the group's own
 * screen afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRuleGroupScreen(
    viewModel: NewRuleGroupViewModel = hiltViewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
) {
    val step by viewModel.step.collectAsState()
    val draft by viewModel.draft.collectAsState()
    BackHandler(enabled = step != NewGroupStep.TEMPLATE) { viewModel.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.back()) navViewModel.onBackPress() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.action_close)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(
                            id = when (step) {
                                NewGroupStep.TEMPLATE -> R.string.ruleGroups_new
                                NewGroupStep.WHAT -> R.string.ruleGroups_setup_what
                                NewGroupStep.HOW -> R.string.ruleGroups_setup_how
                            }
                        )
                    )
                }
            )
        },
        bottomBar = {
            if (step != NewGroupStep.TEMPLATE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.back() }) {
                        Text(stringResource(id = R.string.action_back))
                    }
                    // nothing to go on with before the template's picks are in
                    if (step == NewGroupStep.WHAT) {
                        Button(onClick = { viewModel.next() }, enabled = draft != null) {
                            Text(stringResource(id = R.string.onboarding_action_next))
                        }
                    } else {
                        Button(enabled = draft != null, onClick = {
                            viewModel.create()?.let {
                                navViewModel.replaceRoute(NavigationRoutes.RuleGroup(it))
                            }
                        }) {
                            Text(stringResource(id = R.string.ruleGroups_setup_create))
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val enter = slideInHorizontally { if (forward) it else -it } + fadeIn()
                val exit = slideOutHorizontally { if (forward) -it else it } + fadeOut()
                enter togetherWith exit
            },
            label = "NewGroupStep"
        ) { current ->
            when (current) {
                NewGroupStep.TEMPLATE -> TemplateStep(onChoose = viewModel::chooseTemplate)
                NewGroupStep.WHAT -> WhatStep(viewModel = viewModel)
                NewGroupStep.HOW -> draft?.let { HowStep(draft = it, viewModel = viewModel) }
            }
        }
    }
}

@Composable
private fun TemplateStep(onChoose: (GroupTemplate) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(id = R.string.ruleGroups_template_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        TemplateSection.entries.forEach { section ->
            val templates = GroupTemplate.entries.filter { it.section == section && it != GroupTemplate.EMPTY }
            if (templates.isEmpty()) return@forEach
            Text(
                text = stringResource(id = section.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
            templates.forEach { template ->
                TemplateCard(template = template, onClick = { onChoose(template) })
            }
        }
        OutlinedButton(
            onClick = { onChoose(GroupTemplate.EMPTY) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(id = GroupTemplate.EMPTY.titleRes))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TemplateCard(template: GroupTemplate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tone = brandTone(template.colorRes)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tone.container)
            ) {
                Icon(
                    imageVector = template.icon,
                    contentDescription = null,
                    tint = tone.content,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = stringResource(id = template.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = GroupTemplates.description(LocalContext.current, template),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** What counts: a name, the apps ranked by how distracting they are, and a few websites. */
@Composable
private fun WhatStep(viewModel: NewRuleGroupViewModel) {
    val context = LocalContext.current
    val draft by viewModel.draft.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val hasUsageData by viewModel.hasUsageData.collectAsState()
    val websiteChoices by viewModel.websiteChoices.collectAsState()
    val current = draft
    if (current == null || rows == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val leavesAlone = current.listType == AppExceptionListType.NOT_LIST
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            OutlinedTextField(
                value = current.name,
                onValueChange = viewModel::setName,
                singleLine = true,
                label = { Text(stringResource(id = R.string.ruleGroups_name)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = stringResource(
                    id = if (leavesAlone) R.string.ruleGroups_setup_what_except else R.string.ruleGroups_setup_what_these
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        items(rows.orEmpty(), key = { it.packageName }) { row ->
            AppSelectionListItem(
                packageName = row.packageName,
                appName = row.appName,
                appCategory = row.appCategory,
                isBackgroundApp = false,
                checked = row.packageName in current.apps,
                supportingText = if (hasUsageData && row.avgDailyUsageMs > 0) {
                    stringResource(
                        id = R.string.onboarding_apps_avgUsage,
                        row.avgDailyUsageMs.milliseconds.toHrMinString(context)
                    )
                } else null,
                onCheckedChange = { viewModel.toggleApp(row.packageName) }
            )
        }
        item {
            Text(
                text = stringResource(id = R.string.appList_tab_websites),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ChipFlowRow(modifier = Modifier.padding(horizontal = 16.dp)) {
                websiteChoices.forEach { entry ->
                    FilterChip(
                        selected = entry in current.websites,
                        onClick = { viewModel.toggleWebsite(entry) },
                        label = { Text(Websites.displayName(context, entry)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
            AddWebsiteField(enabled = true, onAdd = viewModel::addWebsite)
            Text(
                text = stringResource(id = R.string.ruleGroups_setup_what_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

/** How strict: the budget, the steps and the schedule, each a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowStep(draft: GroupDraft, viewModel: NewRuleGroupViewModel) {
    val context = LocalContext.current
    val budgetMinutes = if (draft.budgetMs > 0L) TimeUnit.MILLISECONDS.toMinutes(draft.budgetMs).toInt() else 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GroupCard {
            Text(
                text = stringResource(id = R.string.ruleGroups_budget_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.ruleGroups_setup_budget_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            ChipFlowRow {
                GroupBudgetViewModel.Presets.forEach { preset ->
                    OptionChip(
                        label = GroupTexts.duration(context, preset * 60_000L),
                        selected = budgetMinutes == preset,
                        onClick = { viewModel.setBudgetMinutes(preset) }
                    )
                }
                OptionChip(
                    label = stringResource(id = R.string.ruleGroups_budget_noLimit),
                    selected = budgetMinutes == 0,
                    onClick = { viewModel.setBudgetMinutes(0) }
                )
            }
            if (budgetMinutes > 0) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    BudgetPeriod.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == draft.period,
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
            }
        }
        GroupCard(contentPadding = PaddingValues(vertical = 16.dp)) {
            Text(
                text = stringResource(id = R.string.ruleGroups_steps_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
            )
            val canHoldBack = draft.listType == AppExceptionListType.ONLY_LIST || draft.websites.isNotEmpty()
            Step.entries.forEach { step ->
                val available = (step != Step.LOCK && step != Step.WAIT) || canHoldBack
                val on = when (step) {
                    Step.WAIT -> draft.waitMs > 0L
                    Step.GRAY -> draft.grayAfterMs >= 0L
                    Step.SCROLL_BREAK -> draft.scrollBreakAfterMs > 0L
                    Step.LOCK -> draft.locks
                }
                val subtitle = when {
                    !available -> stringResource(id = R.string.ruleGroups_step_onlySelectedApps)
                    step == Step.GRAY && on && draft.budgetMs > 0L && draft.grayAfterMs > 0L ->
                        stringResource(id = R.string.ruleGroups_step_gray_on, GroupTexts.minutes(context, draft.grayAfterMs))

                    step == Step.LOCK && on && draft.budgetMs > 0L -> GroupTexts.lockOn(
                        context, draft.budgetMs, draft.period, draft.schedule != SchedulePreset.ALWAYS
                    )

                    step == Step.WAIT && on ->
                        stringResource(id = R.string.ruleGroups_step_wait_on, GroupTexts.seconds(context, draft.waitMs))

                    step == Step.SCROLL_BREAK && on -> stringResource(
                        id = R.string.ruleGroups_step_scrollBreak_on, GroupTexts.minutes(context, draft.scrollBreakAfterMs)
                    )

                    else -> stringResource(
                        id = when (step) {
                            Step.WAIT -> R.string.ruleGroups_step_wait_off
                            Step.GRAY -> if (on) R.string.ruleGroups_step_gray_always else R.string.ruleGroups_step_gray_off
                            Step.SCROLL_BREAK -> R.string.ruleGroups_step_scrollBreak_off
                            Step.LOCK -> if (on) R.string.ruleGroups_step_lock_always else R.string.ruleGroups_step_lock_off
                        }
                    )
                }
                StepRow(
                    step = step,
                    on = on && available,
                    enabled = available,
                    subtitle = subtitle,
                    hasValue = false,
                    onToggle = { viewModel.setStep(step, it) },
                    onClick = {},
                )
            }
        }
        GroupCard {
            Text(
                text = stringResource(id = R.string.ruleGroups_when_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ChipFlowRow {
                SchedulePreset.entries.forEach { preset ->
                    OptionChip(
                        label = stringResource(id = preset.titleRes),
                        selected = draft.schedule == preset,
                        onClick = { viewModel.setSchedule(preset) }
                    )
                }
            }
            Text(
                text = stringResource(id = R.string.ruleGroups_setup_schedule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
