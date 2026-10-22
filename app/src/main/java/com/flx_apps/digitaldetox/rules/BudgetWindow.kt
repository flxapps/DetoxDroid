package com.flx_apps.digitaldetox.rules

import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.feature_types.nextTransitionAfter
import java.time.LocalDate
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
     * schedule opened: when the rules were last all off before [now]. A rule that opens while
     * another is still on continues the same stretch, and a rule that spans midnight and is in its
     * early-morning half started yesterday. Without rules, or outside every rule (a brake has the
     * group running anyway), it is midnight; so is a stretch that never ends, which is counted per
     * calendar day.
     */
    fun startOf(
        rules: Collection<FeatureScheduleRule>, period: BudgetPeriod, now: LocalDateTime
    ): LocalDateTime {
        if (period == BudgetPeriod.HOUR) return now.truncatedTo(ChronoUnit.HOURS)
        val midnight = now.toLocalDate().atStartOfDay()
        var start = rules.mapNotNull { it.startOfActiveStretch(now) }.minOrNull() ?: return midnight
        while (true) {
            val earlier = rules.mapNotNull { it.startOfActiveStretch(start.minusSeconds(1)) }.minOrNull()
            if (earlier == null || !earlier.isBefore(start)) return start
            start = earlier
            if (!start.isAfter(now.minusDays(1))) return midnight
        }
    }

    /**
     * Whether a day's budget runs past midnight: the rules keep the group on from one day into the
     * next for less than a whole day, so the window, and the budget with it, does not start over
     * at midnight. What someone who reads "per day" is told about where it applies.
     */
    fun runsPastMidnight(rules: Collection<FeatureScheduleRule>): Boolean {
        if (rules.isEmpty()) return false
        // the rules know weekdays, not dates, so any week will do
        val monday = LocalDate.of(2024, 1, 1)
        return (0L until 7L).any { day ->
            val midnight = monday.plusDays(day).atStartOfDay()
            startOf(rules, BudgetPeriod.DAY, midnight.plusSeconds(1)).isBefore(midnight)
        }
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
