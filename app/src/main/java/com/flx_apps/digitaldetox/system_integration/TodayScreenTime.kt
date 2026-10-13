package com.flx_apps.digitaldetox.system_integration

import android.content.Context
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.util.UsageAccessUtil

/**
 * Today's foreground time per package, from whichever instrument this phone allows.
 *
 * The system's own record is the better one and is used wherever usage access was granted. Where
 * it was not, and on some ROMs a sideloaded app never gets it, what is left is the time DetoxDroid
 * measured itself while it was running.
 * @see com.flx_apps.digitaldetox.util.ForegroundTimeRecorder
 */
object TodayScreenTime {
    fun byPackage(context: Context): Map<String, Long> =
        if (UsageAccessUtil.hasUsageAccess(context)) {
            UsageStatsProvider.usageStatsToday().mapValues { it.value.screenTimeMs }
        } else {
            UsageStatsTracker.foregroundTimeRecorder.snapshot()
                .mapValues { it.value.foregroundMs }
        }
}
