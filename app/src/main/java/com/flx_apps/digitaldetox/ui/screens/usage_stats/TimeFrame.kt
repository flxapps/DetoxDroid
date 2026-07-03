package com.flx_apps.digitaldetox.ui.screens.usage_stats

import com.flx_apps.digitaldetox.R

/**
 * Time frame options for usage stats. [days] is the nominal length of the period and is used to
 * derive the queried range and daily averages ([CUSTOM] does not have a fixed length).
 */
enum class TimeFrame(val days: Int, val labelRes: Int) {
    TODAY(days = 1, labelRes = R.string.usage_stats_timeframe_today),
    LAST_7_DAYS(days = 7, labelRes = R.string.usage_stats_timeframe_last7days),
    LAST_30_DAYS(days = 30, labelRes = R.string.usage_stats_timeframe_last30days),
    LAST_90_DAYS(days = 90, labelRes = R.string.usage_stats_timeframe_last90days),
    CUSTOM(days = 0, labelRes = R.string.usage_stats_timeframe_custom)
}
