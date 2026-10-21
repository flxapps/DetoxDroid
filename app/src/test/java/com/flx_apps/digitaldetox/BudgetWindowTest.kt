package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.BudgetWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A budget counts from the moment its window opens. For a window that spans midnight, the early
 * morning belongs to the window that opened the evening before; a window that started at the
 * morning's own date, or at midnight, would hand out a fresh budget at 00:00 and look right.
 */
class BudgetWindowTest {
    @Test
    fun `the morning half of a night window started the evening before`() {
        val nights = setOf(FeatureScheduleRule(emptyList(), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val tuesdayThreeAm = LocalDateTime.of(2026, 10, 20, 3, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 19, 22, 0),
            BudgetWindow.startOf(nights, BudgetPeriod.DAY, tuesdayThreeAm)
        )
    }

    @Test
    fun `a day window starts when it opens, not at midnight`() {
        val weekdays = setOf(
            FeatureScheduleRule(
                listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(10, 0), LocalTime.of(22, 0)
            )
        )
        val tuesdayNoon = LocalDateTime.of(2026, 10, 20, 12, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 20, 10, 0),
            BudgetWindow.startOf(weekdays, BudgetPeriod.DAY, tuesdayNoon)
        )
    }
}
