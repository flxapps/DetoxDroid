package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.content.Context
import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.ui.graphics.vector.ImageVector
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.ui.screens.schedule.dayRangesText
import com.flx_apps.digitaldetox.util.appLabel
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.websites.Websites
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** How a step is named and drawn wherever the groups show it. */
val Step.titleRes: Int
    @StringRes get() = when (this) {
        Step.WAIT -> R.string.ruleGroups_step_wait
        Step.GRAY -> R.string.ruleGroups_step_gray
        Step.SCROLL_BREAK -> R.string.ruleGroups_step_scrollBreak
        Step.LOCK -> R.string.ruleGroups_step_lock
    }

val Step.icon: ImageVector
    get() = when (this) {
        Step.WAIT -> Icons.Default.HourglassTop
        Step.GRAY -> Icons.Default.Contrast
        Step.SCROLL_BREAK -> Icons.Default.SwipeVertical
        Step.LOCK -> Icons.Default.Lock
    }

/**
 * The words the groups are described in: what a group covers, how much it allows, when, and what
 * happens, in the form each screen needs. Everything here reads the group as it is, so the
 * screens stay in step with each other.
 */
object GroupTexts {
    /** How many named apps are spelled out before the rest become "and 3 more". */
    private const val NAMED_APPS = 3

    /** A group without a name, which the setup allows. */
    fun name(context: Context, group: RuleGroup): String =
        group.name.ifBlank { context.getString(R.string.ruleGroups_unnamed) }

    /**
     * What the group covers, as the subject of a sentence: "Instagram, TikTok and 4 websites",
     * "All apps except Camera and Maps", "Every app and website". Null when the group is empty.
     * @return the text and how many things it names, for the verb that follows it: a group of
     * everything-but counts as many
     */
    fun scope(context: Context, group: RuleGroup): Pair<String, Int>? {
        val apps = Websites.appsOf(group.appExceptions).filter { context.isInstalled(it) }
        val websites = Websites.websitesOf(group.appExceptions)
        val appNames = apps.map { context.appLabel(it) }.sortedBy { it.lowercase(Locale.getDefault()) }
        val listed = buildList {
            if (appNames.isNotEmpty()) add(namedList(context, appNames))
            if (websites.isNotEmpty()) add(websitesPhrase(context, websites))
        }
        val listedText = joinNatural(listed)
        val allApps = group.appExceptionListType == AppExceptionListType.NOT_LIST
        val allWebsites = group.websiteListType == AppExceptionListType.NOT_LIST
        return when {
            allApps && listed.isEmpty() -> context.getString(
                if (allWebsites) R.string.ruleGroups_scope_everything else R.string.ruleGroups_scope_allApps
            ) to 2

            allApps -> context.getString(R.string.ruleGroups_scope_allExcept, listedText) to 2
            listed.isEmpty() && allWebsites -> context.getString(R.string.ruleGroups_scope_allWebsites) to 2
            listed.isEmpty() -> null
            allWebsites -> context.getString(
                R.string.ruleGroups_scope_andAllWebsites, listedText
            ) to 2

            else -> listedText to apps.size + websites.size
        }
    }

    /** "Instagram, TikTok and 3 more": the first few names, then a count. */
    private fun namedList(context: Context, names: List<String>): String {
        if (names.size <= NAMED_APPS) return joinNatural(names)
        val shown = names.take(NAMED_APPS - 1)
        val more = names.size - shown.size
        return context.resources.getQuantityString(
            R.plurals.ruleGroups_scope_andMore, more, joinNatural(shown), more
        )
    }

    /** "reddit.com", "youtube.com/shorts and x.com", "4 websites", "Social media". */
    private fun websitesPhrase(context: Context, websites: Set<String>): String {
        val names = websites.map { Websites.displayName(context, it) }.sorted()
        return if (names.size <= 2) {
            joinNatural(names)
        } else {
            context.resources.getQuantityString(
                R.plurals.websites_count_sites, names.size, names.size
            )
        }
    }

    /** "30 minutes per day", null without a budget. */
    fun budget(context: Context, group: RuleGroup): String? {
        if (!group.hasBudget) return null
        val amount = group.budgetMs.milliseconds.toHrMinString(context)
        return context.getString(
            when (group.budgetPeriod) {
                BudgetPeriod.DAY -> R.string.ruleGroups_budget_perDay
                BudgetPeriod.HOUR -> R.string.ruleGroups_budget_perHour
            }, amount
        )
    }

    /**
     * When the group counts, as the tail of a sentence: "on weekdays from 10:00 to 22:00", "every
     * day from 22:00 to 07:00", "on weekends". Null without a schedule.
     */
    fun schedule(context: Context, rules: Collection<FeatureScheduleRule>): String? {
        if (rules.isEmpty()) return null
        if (rules.size > 1) {
            return context.resources.getQuantityString(
                R.plurals.ruleGroups_schedule_windows, rules.size, rules.size
            )
        }
        val rule = rules.first()
        val days = daysPhrase(context, rule.daysOfWeek)
        if (rule.start == rule.end) return days
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        return context.getString(
            R.string.ruleGroups_schedule_window,
            days, formatter.format(rule.start), formatter.format(rule.end)
        )
    }

    /** "every day", "on weekdays", "on weekends", "on Mon, Wed and Fri". */
    fun daysPhrase(context: Context, days: List<DayOfWeek>): String {
        val set = days.toSet()
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        return when {
            set.isEmpty() || set.size == 7 -> context.getString(R.string.ruleGroups_schedule_everyDay)
            set == DayOfWeek.entries.toSet() - weekend ->
                context.getString(R.string.ruleGroups_schedule_weekdays)

            set == weekend -> context.getString(R.string.ruleGroups_schedule_weekends)
            else -> context.getString(
                R.string.ruleGroups_schedule_onDays, dayRangesText(set, Locale.getDefault())
            )
        }
    }

    /** "Weekdays, 10:00 to 22:00" for a card line, "Always" without a schedule. */
    fun scheduleShort(context: Context, rules: Collection<FeatureScheduleRule>): String {
        if (rules.isEmpty()) return context.getString(R.string.ruleGroups_schedule_always)
        if (rules.size > 1) {
            return context.resources.getQuantityString(
                R.plurals.ruleGroups_schedule_windows, rules.size, rules.size
            )
        }
        val rule = rules.first()
        val days = daysPhrase(context, rule.daysOfWeek).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        if (rule.start == rule.end) return days
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        return context.getString(
            R.string.ruleGroups_schedule_windowShort,
            days, formatter.format(rule.start), formatter.format(rule.end)
        )
    }

    /** "10 s", "5 min": a step's value as a short amount. */
    fun seconds(context: Context, ms: Long): String =
        context.getString(R.string.duration_seconds_short, (ms / 1000L).toInt())

    fun minutes(context: Context, ms: Long): String = ms.milliseconds.toHrMinString(context)

    /** The time of day [time] as the user reads it. */
    fun time(time: LocalTime): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time)

    /** "A, B and C" in the user's language. */
    fun joinNatural(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.first()
        else -> ListFormatter.getInstance(Locale.getDefault()).format(items)
    }

    /** Whether [packageName] is still installed; the lists outlive the apps they name. */
    private fun Context.isInstalled(packageName: String): Boolean =
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.isSuccess
}
