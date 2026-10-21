package com.flx_apps.digitaldetox.util

import android.content.Context
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoRepository
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import java.util.concurrent.TimeUnit

/** An installed app, ranked by how distracting it is likely to be, see [RankedApps]. */
data class RankedAppRow(
    val packageName: String,
    val appName: String,
    val appCategory: String,
    val isSystemApp: Boolean,
    val knownCategory: KnownAppCategory?,
    val osCategory: Int,
    val avgDailyUsageMs: Long,
    /** Whether the heuristic would pick it, see [DistractingAppsHeuristic.rankApps]. */
    val preChecked: Boolean,
)

/**
 * The installed apps that could be worth a rule, most distracting first: by the last week's usage
 * where usage access is granted, by category alone where it is not. What the onboarding and the
 * setup of a new group both start from.
 */
object RankedApps {
    /** Usage is averaged over the last week (zero days included). */
    const val USAGE_DAYS = 7

    /** System apps without a known category are hidden below this average daily usage. */
    val MIN_SYSTEM_APP_USAGE_MS: Long = TimeUnit.MINUTES.toMillis(5)

    /**
     * Blocking: reads the package manager and, with [hasUsageAccess], the usage records.
     * @param limit how many rows at most, see [DistractingAppsHeuristic.rankApps]
     * @return the rows, and whether they are backed by real usage data
     */
    fun load(
        context: Context,
        hasUsageAccess: Boolean,
        limit: Int = DistractingAppsHeuristic.MAX_DISPLAYED_APPS,
    ): Pair<List<RankedAppRow>, Boolean> {
        val avgUsageByApp: Map<String, Long> = if (hasUsageAccess) {
            val totals = HashMap<String, Long>()
            UsageStatsProvider.queryDailyUsage(USAGE_DAYS).forEach { (_, timeByApp) ->
                timeByApp.forEach { (packageName, timeMs) ->
                    totals.merge(packageName, timeMs, Long::plus)
                }
            }
            totals.mapValues { it.value / USAGE_DAYS }
        } else {
            emptyMap()
        }
        val installedApps = ApplicationInfoRepository.getInstalledApps()
        val candidates = installedApps.filter { app ->
            app.packageName != context.packageName && app.hasLaunchIntent &&
                    (!app.isSystemApp || knownCategoryOf(app.packageName) != null ||
                            (avgUsageByApp[app.packageName] ?: 0L) >= MIN_SYSTEM_APP_USAGE_MS)
        }.map { app ->
            DistractionCandidate(
                packageName = app.packageName,
                knownCategory = knownCategoryOf(app.packageName),
                osCategory = app.osCategory,
                avgDailyUsageMs = avgUsageByApp[app.packageName] ?: 0L
            )
        }
        val appsByPackage = installedApps.associateBy { it.packageName }
        val rows = DistractingAppsHeuristic.rankApps(candidates, limit).map { rankedApp ->
            val appInfo = appsByPackage.getValue(rankedApp.packageName)
            RankedAppRow(
                packageName = rankedApp.packageName,
                appName = appInfo.appName,
                appCategory = appInfo.appCategory,
                isSystemApp = appInfo.isSystemApp,
                knownCategory = knownCategoryOf(rankedApp.packageName),
                osCategory = appInfo.osCategory,
                avgDailyUsageMs = rankedApp.avgDailyUsageMs,
                preChecked = rankedApp.preChecked,
            )
        }
        return rows to (hasUsageAccess && avgUsageByApp.isNotEmpty())
    }
}
