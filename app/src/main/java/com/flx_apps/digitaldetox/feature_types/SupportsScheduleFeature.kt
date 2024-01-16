package com.flx_apps.digitaldetox.feature_types

import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A feature that supports scheduling. This means that the feature can be scheduled to be active
 * only at certain times of the day and/or on certain days of the week.
 * @see FeatureScheduleRule
 * @see isScheduled
 */
interface SupportsScheduleFeature {
    /**
     * Holds the schedule rules for a feature.
     */
    var scheduleRules: Set<FeatureScheduleRule>

    /**
     * Returns whether the feature is scheduled at the given date and time.
     */
    fun isScheduled(atDateTime: LocalDateTime = LocalDateTime.now()): Boolean

    /**
     * The implementation of [SupportsAppExceptionsFeature].
     * @param featureId The [FeatureId] is needed in order to properly
     */
    class Impl(private val featureId: FeatureId) : SupportsScheduleFeature {
        override var scheduleRules: Set<FeatureScheduleRule> by DataStoreProperty(
            stringSetPreferencesKey("${featureId}_scheduleRules"),
            setOf(),
            dataTransformer = DataStorePropertyTransformer.SetStorePropertyTransformer(
                itemFromString = {
                    FeatureScheduleRule.fromString(it)
                },
                itemToString = { it.toString() })
        )

        override fun isScheduled(atDateTime: LocalDateTime): Boolean {
            return scheduleRules.isEmpty() || scheduleRules.any {
                it.isActive(atDateTime)
            }
        }
    }
}

/**
 * A rule for when a feature should be active. A rule consists of a time range and a day of the
 * week. The feature will be active during the time range on the specified day of the week. If
 * multiple rules apply, the feature will be active if at least one of them is active.
 *
 * For convenience reasons, we will store the rules in a string format in the data store. (Storing
 * them in a database would be more efficient, but seems like overkill for this simple use case.)
 * Hence, this class also provides a method to convert a rule to a string and vice versa.
 *
 * @param daysOfWeek The days of the week when the feature should be active.
 * @param start The start time of the time range.
 * @param end The end time of the time range.
 *
 * @see FeatureScheduleRule.fromString
 * @see FeatureScheduleRule.toString
 */
data class FeatureScheduleRule(
    val daysOfWeek: List<DayOfWeek>, val start: LocalTime, val end: LocalTime
) {
    companion object {
        /**
         * Converts a string to a rule, e.g. "MONDAY|TUESDAY|WEDNESDAY,00:00,08:00" to a rule that
         * is active on Monday, Tuesday and Wednesday from 00:00 to 08:00. If the string is invalid,
         * null is returned.
         *
         * @param string The string to convert.
         */
        fun fromString(string: String): FeatureScheduleRule? {
            kotlin.runCatching {
                val parts = string.split(",")
                val daysOfWeek = parts[0].takeIf { it.isNotBlank() }?.split("|")
                    ?.map { DayOfWeek.of(it.toInt()) } ?: emptyList()
                val start = LocalTime.parse(parts[1])
                val end = LocalTime.parse(parts[2])
                return FeatureScheduleRule(daysOfWeek, start, end)
            }
            return null
        }
    }

    /**
     * Whether the rule is currently active. If end is before start, the rule is active from start
     * to midnight and from midnight to end (the next day).
     */
    fun isActive(atDateTime: LocalDateTime = LocalDateTime.now()): Boolean {
        var dayOfWeek = atDateTime.dayOfWeek
        val atTime = atDateTime.toLocalTime()

        // copy fromTime and toTime and leave original object alone
        var fromTime = start
        var toTime = end

        if (toTime.isBefore(fromTime)) {
            // we have a rule that spans midnight, e.g. 20:00-04:00 (next day)
            if (atTime.isBefore(toTime)) {
                // timeOfDay is e.g. 03:00, so set fromTime to 00:00 and imagine dayOfWeek as still yesterday
                fromTime = LocalTime.of(0, 0)
                dayOfWeek = dayOfWeek.minus(1)
            } else if (atTime.isAfter(fromTime)) {
                // timeOfDay is e.g. 21:00, so set toTime to 23:59:59 (midnight)
                toTime = LocalTime.of(23, 59, 59, 999999999)
            }
        }
        
        // the rule is active if the current day of week is in the list of days of week and the
        // current time is between fromTime and toTime or fromTime == toTime (then the whole day is
        // considered active)
        return (daysOfWeek.isEmpty() || daysOfWeek.contains(dayOfWeek)) && ((atTime.isAfter(fromTime) && atTime.isBefore(
            toTime
        )) || (fromTime == toTime))
    }

    fun copyWith(
        daysOfWeek: List<DayOfWeek>? = null, start: LocalTime? = null, end: LocalTime? = null
    ): FeatureScheduleRule {
        return FeatureScheduleRule(
            daysOfWeek ?: this.daysOfWeek, start ?: this.start, end ?: this.end
        )
    }

    /**
     * Converts the rule to a string, e.g. "MONDAY|TUESDAY|WEDNESDAY,00:00,08:00".
     */
    override fun toString(): String {
        return "${daysOfWeek.joinToString("|") { it.value.toString() }},$start,$end"
    }
}