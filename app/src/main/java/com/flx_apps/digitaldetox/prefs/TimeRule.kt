package com.flx_apps.digitaldetox.prefs

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class TimeRule(var days: Set<DayOfWeek>, var fromTime: LocalTime, var toTime: LocalTime) {
    companion object {
        fun fromString(timeRuleString: String): TimeRule {
            val parts = timeRuleString.split("|")
            return TimeRule(
                parts[0].split(",").map { s -> DayOfWeek.of(s.toInt()) }.toSet(),
                LocalTime.parse(parts[1]),
                LocalTime.parse(parts[2])
            )
        }

        fun getById(context: Context, id: Int): TimeRule {
            return fromString(Prefs_(context).timeRules().get().toTypedArray()[id])
        }
    }

    fun isActive(atTime: LocalDateTime = LocalDateTime.now()): Boolean {
        var dayOfWeek = atTime.dayOfWeek
        var timeOfDay = atTime.toLocalTime()

        // copy fromTime and toTime and leave original object alone
        var _fromTime = fromTime
        var _toTime = toTime

        if (toTime.isBefore(fromTime)) {
            // we have a rule like 20:00-04:00 (next day)
            if (timeOfDay.isBefore(toTime)) {
                // timeOfDay is e.g. 03:00, so set _fromTime to 00:00 and imagine dayOfWeek as still yesterday
                _fromTime = LocalTime.of(0, 0)
                dayOfWeek = dayOfWeek.minus(1)
            }
            else if (timeOfDay.isAfter(fromTime)) {
                // timeOfDay is e.g. 21:00, so set _toTime to 23:59:59
                _toTime = LocalTime.of(23, 59, 59, 999999999)
            }
        }

        return days.contains(dayOfWeek) && (
                (timeOfDay.isAfter(_fromTime) && timeOfDay.isBefore(_toTime)) ||
                (fromTime == toTime)
        )
    }

    override fun toString(): String {
        return days.joinToString(separator = ",") { dayOfWeek -> dayOfWeek.value.toString() } + "|$fromTime|$toTime"
    }
}