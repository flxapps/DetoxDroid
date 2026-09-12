package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.ui.screens.schedule.ScheduleStatus
import com.flx_apps.digitaldetox.ui.screens.schedule.activeMinutes
import com.flx_apps.digitaldetox.ui.screens.schedule.dayRangesText
import com.flx_apps.digitaldetox.ui.screens.schedule.hourLabel
import com.flx_apps.digitaldetox.ui.screens.schedule.orderedWeekDays
import com.flx_apps.digitaldetox.ui.screens.schedule.scheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime
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

    private val workdays = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

    /** 2024-01-01 is a Monday, so the day of this month is the day of the week. */
    private fun at(dayOfMonth: Int, time: String) =
        LocalDateTime.of(2024, 1, dayOfMonth, 0, 0).with(LocalTime.parse(time))

    @Test
    fun `without rules the feature is always active`() {
        assertEquals(ScheduleStatus.AlwaysActive, scheduleStatus(emptyList(), at(3, "10:30")))
        val everyDayAllDay = listOf(rule(emptyList(), "00:00", "00:00"))
        assertEquals(ScheduleStatus.AlwaysActive, scheduleStatus(everyDayAllDay, at(3, "10:30")))
    }

    @Test
    fun `the status names the next change, a weekend away if need be`() {
        val rules = listOf(rule(workdays, "09:00", "17:00"))
        assertEquals(ScheduleStatus.ActiveUntil(at(3, "17:00")), scheduleStatus(rules, at(3, "10:30")))
        assertEquals(ScheduleStatus.InactiveUntil(at(8, "09:00")), scheduleStatus(rules, at(5, "17:00")))
    }

    @Test
    fun `a rule across midnight ends the next morning`() {
        val rules = listOf(rule(listOf(FRIDAY), "22:00", "02:00"))
        assertEquals(ScheduleStatus.ActiveUntil(at(6, "02:00")), scheduleStatus(rules, at(5, "23:59")))
    }

    @Test
    fun `hours are labelled the way the locale tells time`() {
        assertEquals("18", hourLabel(18, Locale.GERMANY))
        assertEquals("24", hourLabel(24, Locale.GERMANY))
        assertEquals("6 PM", hourLabel(18, Locale.US))
        assertEquals("12 AM", hourLabel(24, Locale.US))
    }

    @Test
    fun `three or more days in a row read as a range`() {
        assertEquals("Mon-Fri", dayRangesText(workdays, Locale.UK))
        assertEquals("Mon, Tue, Thu", dayRangesText(listOf(THURSDAY, MONDAY, TUESDAY), Locale.UK))
    }

    @Test
    fun `the weekend stays together where the week starts on Sunday`() {
        assertEquals("Sat, Sun", dayRangesText(listOf(SATURDAY, SUNDAY), Locale.US))
        assertEquals("Fri-Mon", dayRangesText(listOf(FRIDAY, SATURDAY, SUNDAY, MONDAY), Locale.US))
        assertEquals("Fri-Mon", dayRangesText(listOf(FRIDAY, SATURDAY, SUNDAY, MONDAY), Locale.UK))
    }
}
