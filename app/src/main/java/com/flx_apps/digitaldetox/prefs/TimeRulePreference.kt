package com.flx_apps.digitaldetox.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.flx_apps.digitaldetox.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

/**
 * Creation Date: 1/19/22
 * @author felix
 */
open class TimeRulePreference : Preference {
    constructor(context: Context): super(context)
    constructor(context: Context, attributeSet: AttributeSet): super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int): super(context, attributeSet, defStyleAttr)
    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attributeSet, defStyleAttr, defStyleRes)

    var timeRule: TimeRule = TimeRule(emptySet(), LocalTime.of(0, 0), LocalTime.of(0, 0))

    init {
        isIconSpaceReserved = false
    }

    override fun onClick() {
        TimeRulePreferenceDialogFragment.showDialog(context, key.toInt())
    }

    override fun getSummary(): CharSequence {
        return timeRule.days.joinToString { dayOfWeek -> dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) } +
                "\n" +
                if (timeRule.fromTime == timeRule.toTime)
                    context.getString(R.string.home_timeRules_allDay)
                else
                    timeRule.fromTime.format(DateTimeFormatter.ISO_TIME) + " - " + timeRule.toTime.format(DateTimeFormatter.ISO_TIME) +
                            if (timeRule.toTime.isBefore(timeRule.fromTime))
                                " " + context.getString(R.string.home_timeRules__nextDay)
                            else ""
    }

}

