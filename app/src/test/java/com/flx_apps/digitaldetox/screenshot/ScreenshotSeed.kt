package com.flx_apps.digitaldetox.screenshot

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.data.DailyAppUsage
import com.flx_apps.digitaldetox.data.DailyAppUsageDao
import com.flx_apps.digitaldetox.data.DailyGrayscaleStats
import com.flx_apps.digitaldetox.data.DailyGrayscaleStatsDao
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DoNotDisturbFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.premium.PremiumManager
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministic seed data for the store screenshots: a handful of believable "installed" apps with
 * screen-time, plus today's [UsageStatsManager] buckets (for the home donut) and ~14 days of Room
 * history (for the usage-stats charts).
 */
object ScreenshotSeed {

    /** package name -> display label, in rough descending screen-time order. */
    private val apps = listOf(
        "org.thoughtcrime.securesms" to "Signal",
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.android.chrome" to "Chrome",
        "com.whatsapp" to "WhatsApp",
        "com.google.android.apps.maps" to "Maps",
        "com.spotify.music" to "Spotify",
    )

    /** Today's foreground minutes per app (index-aligned with [apps]) for the home donut. */
    private val todayMinutes = listOf(38, 27, 22, 16, 11, 7, 5)

    /**
     * Makes DetoxDroid appear "running" with a few features enabled, so the home screen shows the
     * green "Running" state and green feature indicators.
     */
    fun seedActiveState() {
        DetoxDroidAccessibilityService.state.value = DetoxDroidState.Active
        GrayscaleAppsFeature.isActivated = true
        DoNotDisturbFeature.isActivated = true
        BreakDoomScrollingFeature.isActivated = true
        PauseButtonFeature.isActivated = true
        // Unlock premium so the usage-stats timeframe selector shows without lock badges.
        PremiumManager.unlock()
    }

    /** Registers the fake apps with the package manager so their labels resolve to real names. */
    fun installFakeApps(context: Context) {
        val pm = context.packageManager
        apps.forEach { (pkg, label) ->
            val appInfo = ApplicationInfo().apply {
                packageName = pkg
                name = label
                nonLocalizedLabel = label
                flags = ApplicationInfo.FLAG_INSTALLED
            }
            val packageInfo = PackageInfo().apply {
                packageName = pkg
                applicationInfo = appInfo
            }
            shadowOf(pm).installPackage(packageInfo)
        }
    }

    /** Seeds today's [UsageStatsManager] daily buckets used by the home donut chart. */
    fun seedTodayUsageStats(context: Context) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val shadow = shadowOf(usm)
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        apps.forEachIndexed { index, (pkg, _) ->
            val fgMs = todayMinutes[index] * 60_000L
            val stats = ShadowUsageStatsManager.UsageStatsBuilder.newBuilder()
                .setPackageName(pkg)
                .setFirstTimeStamp(dayStart)
                .setLastTimeStamp(now)
                .setLastTimeUsed(now - index * 60_000L)
                .setTotalTimeInForeground(fgMs)
                .build()
            shadow.addUsageStats(UsageStatsManager.INTERVAL_DAILY, stats)
        }
    }

    /** Seeds ~14 days of Room history for the usage-stats screen charts. */
    suspend fun seedHistory(dao: DailyAppUsageDao, grayscaleDao: DailyGrayscaleStatsDao) {
        val today = LocalDate.now()
        for (dayOffset in 0 until 14) {
            val date = today.minusDays(dayOffset.toLong())
            // ~45–80 min of grayscale time per day, so the "In Grayscale" detox impact is non-zero.
            val grayscaleMinutes = 45 + (dayOffset * 17) % 36
            grayscaleDao.upsert(DailyGrayscaleStats(date, grayscaleMinutes * 60_000L))
        }
        val rows = buildList {
            for (dayOffset in 0 until 14) {
                val date = today.minusDays(dayOffset.toLong())
                // A gentle weekly rhythm so the bar/line charts look natural (weekends heavier).
                val dayFactor = if (date.dayOfWeek.value >= 6) 1.4 else 1.0
                apps.forEachIndexed { index, (pkg, _) ->
                    val baseMinutes = todayMinutes[index]
                    // Deterministic pseudo-variation without RNG (keeps screenshots byte-stable).
                    val wobble = ((dayOffset * 7 + index * 13) % 11) - 5
                    val minutes = ((baseMinutes + wobble) * dayFactor).toInt().coerceAtLeast(1)
                    val launches = (minutes / 4).coerceAtLeast(1)
                    val scrolls = if (index <= 2) minutes * 6 else minutes
                    add(
                        DailyAppUsage(
                            rowId = DailyAppUsage.createRowId(date, pkg),
                            date = date,
                            packageName = pkg,
                            totalTimeMs = minutes * 60_000L,
                            sessionCount = launches,
                            launchCount = launches,
                            scrollCount = scrolls,
                            scrollDistancePx = scrolls * 1700,
                            breakCount = if (index == 1) dayOffset % 3 else 0,
                            blockCount = 0,
                        )
                    )
                }
            }
        }
        dao.upsertAll(rows)
    }

    /** Sets the static app context that DetoxDroid's singletons read from. */
    fun seedAppContext(application: Application) {
        DetoxDroidApplication.appContext = application
    }
}
