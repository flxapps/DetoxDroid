package com.flx_apps.digitaldetox.util

import java.time.LocalDate
import java.time.ZoneId

/** The moment this day begins in [zone], in epoch milliseconds, as the usage records count time. */
fun LocalDate.startOfDayMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()
