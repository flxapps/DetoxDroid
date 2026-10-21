package com.flx_apps.digitaldetox.system_integration

import android.content.Context
import androidx.annotation.StringRes
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.rules.GroupBudgets
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageExclusions
import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageStatsSettings
import com.flx_apps.digitaldetox.util.DistancePerspective
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.util.formatDurationMsCompact
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.util.startOfDayMillis
import java.time.LocalDate
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Today so far, as the screen time widget and the service notification show it. The numbers follow
 * the usage screen: apps the user left out there are left out here too, so a sleep tracker that
 * ran all night does not turn into nine hours of screen time on the home screen.
 */
data class TodaySummary(
    val date: LocalDate,
    val screenTimeMs: Long,
    /** Most used first, at most [TOP_APP_LIMIT]. */
    val topApps: List<AppScreenTime>,
    /** Null where the system's event log, which is where unlocks come from, is out of reach. */
    val unlocks: Int?,
    val scrolledMeters: Double,
    /** The budgets of the switched-on groups that have one, in the groups' order. */
    val budgets: List<Budget>,
    /**
     * The screen time of the apps left out of the numbers. Tells "nothing used yet" apart from
     * "everything used is left out", which has to offer the way back to those settings.
     */
    val leftOutMs: Long,
    /** Null where it is not [read] in detail, or there is not enough of a usual day to go by. */
    val vsUsual: VsUsual? = null,
    /** The screen time of every app that is counted, for the launcher widget's lines. */
    val screenTimeByPackage: Map<String, Long> = emptyMap(),
) {
    data class AppScreenTime(val packageName: String, val screenTimeMs: Long)

    /** Today's screen time so far, next to what the days before had typically reached by now. */
    data class VsUsual(val todayMs: Long, val usualMs: Long) {
        /** "35 min less than usual by now", or about as usual within a few minutes. */
        fun text(context: Context): String = worded(
            context,
            R.string.widget_today_vsUsual_less,
            R.string.widget_today_vsUsual_more,
            R.string.widget_today_vsUsual_same
        ) { it.milliseconds.toHrMinString(context) }

        /** "−35 min", "+1h5m" or "±0", for a number among numbers. */
        fun shortText(context: Context): String = worded(
            context,
            R.string.widget_today_vsUsual_less_short,
            R.string.widget_today_vsUsual_more_short,
            R.string.widget_today_vsUsual_same_short
        ) { formatDurationMsCompact(context, it) }

        private fun worded(
            context: Context,
            @StringRes less: Int,
            @StringRes more: Int,
            @StringRes same: Int,
            format: (Long) -> String,
        ): String {
            val differenceMs = todayMs - usualMs
            if (abs(differenceMs) < maxOf(CLOSE_TO_USUAL_MS, usualMs / 10)) {
                return context.getString(same)
            }
            // in whole minutes, like everything else the widget shows
            val difference = format(abs(differenceMs) / MINUTE_MS * MINUTE_MS)
            return context.getString(if (differenceMs < 0) less else more, difference)
        }
    }

    /**
     * What is left of one group's budget.
     * @param locks whether the group locks its apps once the budget is used, as opposed to only
     * turning them gray
     * @param colorLeftMs how long the apps stay in color, for a group that turns gray at a point
     * inside its budget; null where the group does not turn gray, or is gray from the start
     */
    data class Budget(
        val groupId: GroupId,
        val name: String,
        val allowedMs: Long,
        val leftMs: Long,
        val locks: Boolean,
        val colorLeftMs: Long? = null,
    ) {
        /** "12 min left", rounded up so the last seconds don't read as nothing left. */
        fun leftText(context: Context): String = if (leftMs > 0L) {
            val leftMinutes = (leftMs + MINUTE_MS - 1) / MINUTE_MS
            context.getString(
                R.string.today_budget_left, leftMinutes.minutes.toHrMinString(context)
            )
        } else {
            context.getString(R.string.today_budget_usedUp)
        }
    }

    companion object {
        const val TOP_APP_LIMIT = 5

        private const val MINUTE_MS = 60_000L

        /** Closer to the usual than this, or than a tenth of it, reads as about usual. */
        private const val CLOSE_TO_USUAL_MS = 5 * MINUTE_MS

        /**
         * Reads today's numbers. Blocking: the system's usage records sit behind a binder call. A
         * [detailed] reading also has the unlocks and the comparison with a usual day, which only
         * the widget shows. They walk the event log: today's, and once a day the week before.
         */
        fun read(context: Context, detailed: Boolean): TodaySummary {
            val today = LocalDate.now()
            val exclusions = UsageExclusions(context)
            val used = TodayScreenTime.byPackage(context).filterValues { it > 0L }
            val byPackage = used.filterKeys { !exclusions.excludes(it) }
            var unlocks: Int? = null
            var vsUsual: VsUsual? = null
            if (detailed && UsageAccessUtil.hasUsageAccess(context)) {
                val startMs = today.startOfDayMillis()
                val nowMs = System.currentTimeMillis()
                val events = UsageStatsProvider.queryEventCounts(startMs, nowMs) {
                    !exclusions.excludes(it)
                }
                unlocks = events.unlockCount
                vsUsual = UsualScreenTime.byNow(exclusions, today, nowMs - startMs)?.let {
                    VsUsual(todayMs = events.sessions.sumOf { session -> session.totalTimeMs }, it)
                }
            }
            // the apps the time is counted for, as on the usage screen, so both arrive at the
            // same distance
            val scrolledPx = byPackage.keys.sumOf {
                UsageStatsTracker.scrollDistanceCounter.countFor(it).toLong()
            }

            return TodaySummary(
                date = today,
                screenTimeMs = byPackage.values.sum(),
                topApps = byPackage.entries.sortedByDescending { it.value }.take(TOP_APP_LIMIT)
                    .map { AppScreenTime(it.key, it.value) },
                unlocks = unlocks,
                scrolledMeters = DistancePerspective.pixelsToMeters(
                    scrolledPx, context.resources.displayMetrics
                ),
                budgets = budgets(),
                leftOutMs = used.values.sum() - byPackage.values.sum(),
                vsUsual = vsUsual,
                screenTimeByPackage = byPackage,
            )
        }

        /** What is left of every switched-on group's budget, in the groups' order. */
        fun budgets(): List<Budget> =
            RuleGroups.enabled().filter { it.hasBudget }.map { budgetOf(it) }

        private fun budgetOf(group: RuleGroup): Budget {
            val usedMs = GroupBudgets.usedMs(group)
            return Budget(
                groupId = group.id,
                name = group.name,
                allowedMs = group.budgetMs,
                leftMs = (group.budgetMs - usedMs).coerceAtLeast(0L),
                locks = group.locks,
                colorLeftMs = if (group.grays && !group.graysFromStart) {
                    (group.grayAfterMs - usedMs).coerceAtLeast(0L)
                } else null,
            )
        }
    }

    /**
     * The settings [read] counts by, as opposed to the usage records. A summary read under other
     * settings leaves out other apps or measures against other budgets.
     */
    data class Settings(
        val excludedApps: Set<String>,
        val excludedCategories: Set<Int>,
        /** The budgets as configured: one line per group with the parts a reading depends on. */
        val budgets: List<String>,
    ) {
        companion object {
            fun current() = Settings(
                UsageStatsSettings.excludedApps,
                UsageStatsSettings.excludedCategories,
                RuleGroups.all.map {
                    "${it.id}|${it.name}|${it.isEnabled}|${it.budgetMs}|${it.budgetPeriod}|" +
                            "${it.grayAfterMs}|${it.locks}"
                },
            )
        }
    }
}
