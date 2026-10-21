package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.util.DistractingAppsHeuristic
import com.flx_apps.digitaldetox.util.RankedAppRow
import com.flx_apps.digitaldetox.util.RankedApps
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.websites.Websites
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The three screens of the setup, in order. */
enum class NewGroupStep { TEMPLATE, WHAT, HOW }

/**
 * Drives the setup of a new group: a template, what counts, how strict. Nothing is written until
 * [create]; leaving the setup leaves the groups as they were.
 */
@HiltViewModel
class NewRuleGroupViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    private val _step = MutableStateFlow(NewGroupStep.TEMPLATE)
    val step: StateFlow<NewGroupStep> = _step

    /** Every installed app that could be worth a rule, most distracting first. */
    private var installed: List<RankedAppRow>? = null

    /**
     * The apps the list shows: the template's picks first, then the most distracting of the
     * rest; null while the apps are read. Fixed once the template is applied, so unticking a
     * pick does not make it vanish.
     */
    private val _rows = MutableStateFlow<List<RankedAppRow>?>(null)
    val rows: StateFlow<List<RankedAppRow>?> = _rows

    /** Whether the ranking has real usage behind it, so the rows can show it. */
    private val _hasUsageData = MutableStateFlow(false)
    val hasUsageData: StateFlow<Boolean> = _hasUsageData

    private val _draft = MutableStateFlow<GroupDraft?>(null)
    val draft: StateFlow<GroupDraft?> = _draft

    /** The website entries offered as chips: the template's suggestions plus whatever was typed. */
    private val _websiteChoices = MutableStateFlow<List<String>>(emptyList())
    val websiteChoices: StateFlow<List<String>> = _websiteChoices

    /** A template chosen before the apps were in, applied once they are. */
    private var pendingTemplate: GroupTemplate? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // all of them, not the onboarding's top thirty: a template picks by name
            val (rows, hasUsageData) = RankedApps.load(
                application, UsageAccessUtil.hasUsageAccess(application), limit = Int.MAX_VALUE
            )
            _hasUsageData.value = hasUsageData
            installed = rows
            pendingTemplate?.let { applyTemplate(it, rows) }
        }
    }

    /** Starts from [template] and moves on to what counts. */
    fun chooseTemplate(template: GroupTemplate) {
        val rows = installed
        if (rows == null) pendingTemplate = template else applyTemplate(template, rows)
        _step.value = NewGroupStep.WHAT
    }

    private fun applyTemplate(template: GroupTemplate, installed: List<RankedAppRow>) {
        pendingTemplate = null
        val draft = GroupTemplates.draft(application, template, installed)
        _draft.value = draft
        _websiteChoices.value =
            (GroupTemplates.websiteSuggestions(template) + draft.websites).distinct()
        val (picked, rest) = installed.partition { it.packageName in draft.apps }
        _rows.value = picked + rest.take(DistractingAppsHeuristic.MAX_DISPLAYED_APPS)
    }

    fun next() {
        _step.value = when (_step.value) {
            NewGroupStep.TEMPLATE -> NewGroupStep.WHAT
            else -> NewGroupStep.HOW
        }
    }

    /** @return false when the setup is on its first screen and there is nothing to go back to */
    fun back(): Boolean {
        _step.value = when (_step.value) {
            NewGroupStep.TEMPLATE -> return false
            NewGroupStep.WHAT -> NewGroupStep.TEMPLATE
            NewGroupStep.HOW -> NewGroupStep.WHAT
        }
        return true
    }

    private fun edit(change: GroupDraft.() -> GroupDraft) {
        _draft.update { it?.change() }
    }

    fun setName(name: String) = edit { copy(name = name) }

    fun toggleApp(packageName: String) = edit {
        copy(apps = if (packageName in apps) apps - packageName else apps + packageName)
    }

    fun toggleWebsite(entry: String) = edit {
        copy(websites = if (entry in websites) websites - entry else websites + entry)
    }

    /** Adds the site the user typed as a chip, selected. False when it names no site. */
    fun addWebsite(input: String): Boolean {
        val entry = Websites.siteEntry(input) ?: return false
        _websiteChoices.update { if (entry in it) it else it + entry }
        edit { copy(websites = websites + entry) }
        return true
    }

    fun setBudgetMinutes(minutes: Int) = edit {
        val budgetMs = if (minutes > 0) TimeUnit.MINUTES.toMillis(minutes.toLong()) else RuleGroup.NO_BUDGET
        copy(
            budgetMs = budgetMs,
            // the gray threshold follows the budget: two thirds in, or from the start without one
            grayAfterMs = if (grayAfterMs == RuleGroup.STEP_OFF) grayAfterMs else defaultGrayAfter(budgetMs)
        )
    }

    fun setPeriod(period: BudgetPeriod) = edit { copy(period = period) }

    fun setStep(step: Step, on: Boolean) = edit {
        when (step) {
            Step.WAIT -> copy(waitMs = if (on) DefaultWaitMs else 0L)
            Step.GRAY -> copy(grayAfterMs = if (on) defaultGrayAfter(budgetMs) else RuleGroup.STEP_OFF)
            Step.SCROLL_BREAK -> copy(
                scrollBreakAfterMs = if (on) BreakDoomScrollingFeature.DefaultTimeUntilWarningMs else RuleGroup.STEP_OFF
            )

            Step.LOCK -> copy(locks = on)
        }
    }

    fun setSchedule(schedule: SchedulePreset) = edit { copy(schedule = schedule) }

    private fun defaultGrayAfter(budgetMs: Long): Long = if (budgetMs > 0L) {
        val fiveMinutes = TimeUnit.MINUTES.toMillis(5)
        (budgetMs * 2 / 3 / fiveMinutes * fiveMinutes).coerceIn(0L, budgetMs)
    } else {
        0L
    }

    /** Writes the draft as a group and returns its id, or null when there is no draft. */
    fun create(): GroupId? {
        val draft = _draft.value ?: return null
        val fallbackName = application.getString(R.string.ruleGroups_unnamed)
        val group = RuleGroups.create {
            name = RuleGroups.uniqueName(draft.name.trim().ifBlank { fallbackName })
            appExceptions = draft.apps + draft.websites
            appExceptionListType = draft.listType
            websiteListType = AppExceptionListType.ONLY_LIST
            scheduleRules = draft.schedule.rules()
            budgetMs = draft.budgetMs
            budgetPeriod = draft.period
            waitBeforeOpeningMs = draft.waitMs
            grayAfterMs = draft.grayAfterMs
            scrollBreakAfterMs = draft.scrollBreakAfterMs
            locks = draft.locks
        }
        RuleGroups.onChanged()
        return group.id
    }
}
