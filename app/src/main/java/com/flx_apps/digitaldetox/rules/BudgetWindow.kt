package com.flx_apps.digitaldetox.rules

import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.feature_types.nextTransitionAfter
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The stretch of time a group's budget is counted over. It is not the calendar day: a budget of
 * "30 minutes between 10:00 and 22:00" starts counting at 10:00, so what was used at breakfast,
 * when the group was not even on, does not count against the evening. Pure arithmetic on the
 * rules, so it can be checked without a phone.
 */
object BudgetWindow {
    /**
     * When the window that [now] falls in began.
     *
     * [BudgetPeriod.HOUR] begins at the full hour. [BudgetPeriod.DAY] begins when the group's
     * schedule opened: the earliest start among the rules active at [now], where a rule that spans
     * midnight and is in its early-morning half started yesterday. Without rules, or outside
     * every rule (a brake has the group running anyway), it is midnight.
     */
    fun startOf(
        rules: Collection<FeatureScheduleRule>, period: BudgetPeriod, now: LocalDateTime
    ): LocalDateTime {
        if (period == BudgetPeriod.HOUR) return now.truncatedTo(ChronoUnit.HOURS)
        return rules.mapNotNull { it.startOfActiveStretch(now) }.minOrNull()
            ?: now.toLocalDate().atStartOfDay()
    }

    /**
     * The next moment after [now] at which the window changes, or null when it never does within
     * a week, which no rule can express.
     */
    fun nextChangeAfter(
        rules: Collection<FeatureScheduleRule>, period: BudgetPeriod, now: LocalDateTime
    ): LocalDateTime? {
        if (period == BudgetPeriod.HOUR) return now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return listOfNotNull(rules.nextTransitionAfter(now), midnight).min()
    }

    /**
     * When this rule's current stretch of activity began, or null while it is not active. An
     * all-day rule (equal times) began at midnight.
     */
    private fun FeatureScheduleRule.startOfActiveStretch(now: LocalDateTime): LocalDateTime? {
        if (!isActive(now)) return null
        val today = now.toLocalDate()
        if (start == end) return today.atStartOfDay()
        val spansMidnight = end.isBefore(start)
        return if (spansMidnight && now.toLocalTime().isBefore(end)) {
            today.minusDays(1).atTime(start)
        } else {
            today.atTime(start)
        }
    }
}
