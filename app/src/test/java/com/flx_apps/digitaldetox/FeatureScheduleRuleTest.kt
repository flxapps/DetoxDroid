package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class FeatureScheduleRuleTest {
    /** Monday, 2026-07-06 (an arbitrary fixed Monday). */
    private fun monday(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 7, 6, hour, minute)

    private fun rule(
        start: LocalTime, end: LocalTime, days: List<DayOfWeek> = emptyList()
    ) = FeatureScheduleRule(days, start, end)

    @Test
    fun `simple rule is active within its time range`() {
        val r = rule(LocalTime.of(9, 0), LocalTime.of(17, 0))
        assertTrue(r.isActive(monday(12)))
        assertFalse(r.isActive(monday(8)))
        assertFalse(r.isActive(monday(18)))
    }

    @Test
    fun `rule start is inclusive, end is exclusive`() {
        val r = rule(LocalTime.of(9, 0), LocalTime.of(17, 0))
        assertTrue(r.isActive(monday(9, 0)))
        assertFalse(r.isActive(monday(17, 0)))
    }

    @Test
    fun `equal start and end means active all day`() {
        val r = rule(LocalTime.of(0, 0), LocalTime.of(0, 0))
        assertTrue(r.isActive(monday(0)))
        assertTrue(r.isActive(monday(23, 59)))
    }

    @Test
    fun `midnight-spanning rule is active in the evening and early morning`() {
        val r = rule(LocalTime.of(20, 0), LocalTime.of(4, 0))
        assertTrue(r.isActive(monday(20, 0))) // start inclusive
        assertTrue(r.isActive(monday(22)))
        assertTrue(r.isActive(monday(3)))
        assertFalse(r.isActive(monday(4, 0))) // end exclusive
        assertFalse(r.isActive(monday(12)))
    }

    @Test
    fun `midnight-spanning rule attributes the early morning to the previous day`() {
        // rule only on Mondays, 20:00-04:00: Tuesday 03:00 belongs to Monday's session…
        val r = rule(LocalTime.of(20, 0), LocalTime.of(4, 0), listOf(DayOfWeek.MONDAY))
        assertTrue(r.isActive(monday(3).plusDays(1)))
        // …but Monday 03:00 belongs to Sunday's (inactive) session
        assertFalse(r.isActive(monday(3)))
    }

    @Test
    fun `day-of-week restriction is honored`() {
        val r = rule(LocalTime.of(9, 0), LocalTime.of(17, 0), listOf(DayOfWeek.TUESDAY))
        assertFalse(r.isActive(monday(12)))
        assertTrue(r.isActive(monday(12).plusDays(1)))
    }

    @Test
    fun `toString and fromString round-trip`() {
        val r = rule(
            LocalTime.of(9, 30), LocalTime.of(17, 45), listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        )
        assertEquals(r, FeatureScheduleRule.fromString(r.toString()))
    }

    @Test
    fun `fromString round-trips a rule without week days`() {
        val r = rule(LocalTime.of(0, 0), LocalTime.of(8, 0))
        assertEquals(r, FeatureScheduleRule.fromString(r.toString()))
    }

    @Test
    fun `fromString returns null for garbage`() {
        assertNull(FeatureScheduleRule.fromString("not a rule"))
        assertNull(FeatureScheduleRule.fromString("1|2|3,25:00,09:00"))
        assertNull(FeatureScheduleRule.fromString(""))
    }
}
