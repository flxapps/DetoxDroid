package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.ui.screens.schedule.activeMinutes
import com.flx_apps.digitaldetox.ui.screens.schedule.orderedWeekDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime
import java.util.Locale

class WeekScheduleOverviewTest {
    private val wholeDay = listOf(0 until 24 * 60)

    private fun rule(days: List<DayOfWeek>, start: String, end: String) =
        FeatureScheduleRule(days, LocalTime.parse(start), LocalTime.parse(end))

    @Test
    fun `without rules every day is filled`() {
        DayOfWeek.entries.forEach { assertEquals(wholeDay, activeMinutes(emptyList(), it)) }
    }

    @Test
    fun `a rule fills its time range on its days only`() {
        val rules = listOf(rule(listOf(MONDAY), "09:00", "17:00"))
        assertEquals(listOf(9 * 60 until 17 * 60), activeMinutes(rules, MONDAY))
        assertEquals(emptyList<IntRange>(), activeMinutes(rules, TUESDAY))
    }

    @Test
    fun `a rule across midnight spills into the next day`() {
        val rules = listOf(rule(listOf(MONDAY), "22:00", "06:00"))
        assertEquals(listOf(22 * 60 until 24 * 60), activeMinutes(rules, MONDAY))
        assertEquals(listOf(0 until 6 * 60), activeMinutes(rules, TUESDAY))
    }

    @Test
    fun `equal start and end means the whole day, and rules add up`() {
        val rules = listOf(
            rule(listOf(SATURDAY), "00:00", "00:00"),
            rule(listOf(SUNDAY), "08:00", "10:00"),
            rule(listOf(SUNDAY), "12:00", "14:00"),
        )
        assertEquals(wholeDay, activeMinutes(rules, SATURDAY))
        assertEquals(
            listOf(8 * 60 until 10 * 60, 12 * 60 until 14 * 60), activeMinutes(rules, SUNDAY)
        )
    }

    @Test
    fun `the week starts where the locale starts it`() {
        assertEquals(MONDAY, orderedWeekDays(Locale.GERMANY).first())
        assertEquals(SUNDAY, orderedWeekDays(Locale.US).first())
        assertEquals(7, orderedWeekDays(Locale.US).toSet().size)
    }
}
