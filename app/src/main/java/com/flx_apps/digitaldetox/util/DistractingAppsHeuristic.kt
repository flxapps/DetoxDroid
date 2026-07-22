package com.flx_apps.digitaldetox.util

import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * An installed app as seen by the heuristic. [osCategory] is the OS-assigned
 * `ApplicationInfo.category` passed as a plain int so this file stays free of Android imports
 * (and thus testable on the plain JVM).
 * [avgDailyUsageMs] is the average foreground time per day over the last week (zero days
 * included); 0 when usage access has not been granted.
 */
data class DistractionCandidate(
    val packageName: String,
    val knownCategory: KnownAppCategory?,
    val osCategory: Int,
    val avgDailyUsageMs: Long
)

data class RankedApp(
    val packageName: String,
    val score: Double,
    val avgDailyUsageMs: Long,
    val preChecked: Boolean
)

/**
 * Ranks installed apps by how likely they are to be "distracting", blending how much the user
 * actually uses them with how distracting their category typically is. Used by the onboarding
 * flow to pre-select apps and to suggest a realistic daily screen-time budget.
 */
object DistractingAppsHeuristic {
    // `ApplicationInfo.CATEGORY_*` constants, duplicated so this file has no Android imports
    const val OS_CATEGORY_UNDEFINED = -1
    const val OS_CATEGORY_GAME = 0
    const val OS_CATEGORY_AUDIO = 1
    const val OS_CATEGORY_VIDEO = 2
    const val OS_CATEGORY_SOCIAL = 4
    const val OS_CATEGORY_NEWS = 5

    /** Usage saturates at this value: 2h+/day counts as maximal usage. */
    val USAGE_SATURATION_MS: Long = TimeUnit.HOURS.toMillis(2)

    /** How many ranked apps the onboarding list shows at most. */
    const val MAX_DISPLAYED_APPS = 30

    /** How many apps are pre-checked at most. */
    const val MAX_PRE_CHECKED_APPS = 10

    /** Apps scoring at least this are pre-checked (a known social/video app alone scores 0.55). */
    const val PRE_CHECK_SCORE_THRESHOLD = 0.5

    val MIN_BUDGET_MS: Long = TimeUnit.MINUTES.toMillis(15)
    val MAX_BUDGET_MS: Long = TimeUnit.MINUTES.toMillis(180)
    val DEFAULT_BUDGET_MS: Long = TimeUnit.MINUTES.toMillis(60)

    /**
     * How distracting a category typically is, in [0.25, 1.0]. The hardcoded knowledge base
     * ([KnownAppCategory]) wins over the OS-assigned category.
     */
    fun categoryWeight(knownCategory: KnownAppCategory?, osCategory: Int): Double = when {
        knownCategory == KnownAppCategory.SOCIAL || knownCategory == KnownAppCategory.VIDEO -> 1.0
        knownCategory == KnownAppCategory.DATING -> 0.9
        knownCategory == KnownAppCategory.SHOPPING -> 0.7
        knownCategory == KnownAppCategory.MUSIC -> 0.4
        knownCategory == KnownAppCategory.TRAVEL || knownCategory == KnownAppCategory.WORKOUT -> 0.3
        osCategory == OS_CATEGORY_SOCIAL || osCategory == OS_CATEGORY_VIDEO -> 1.0
        osCategory == OS_CATEGORY_GAME -> 0.9
        osCategory == OS_CATEGORY_NEWS -> 0.7
        osCategory == OS_CATEGORY_AUDIO -> 0.4
        else -> 0.25
    }

    /**
     * Additive blend so that heavy usage alone or a strongly distracting category alone is enough
     * to surface an app: a known social app with zero recorded usage scores 0.55, an unknown app
     * used 2h/day scores ~0.59.
     */
    fun score(candidate: DistractionCandidate): Double {
        val usageScore =
            min(candidate.avgDailyUsageMs.toDouble() / USAGE_SATURATION_MS, 1.0)
        return 0.55 * categoryWeight(candidate.knownCategory, candidate.osCategory) +
                0.45 * usageScore
    }

    /**
     * Ranks the candidates (highest score first, deterministic tie-breaking) and pre-checks the
     * top scorers above [PRE_CHECK_SCORE_THRESHOLD], capped at [MAX_PRE_CHECKED_APPS]. Callers
     * are expected to have filtered out non-launchable apps and DetoxDroid itself beforehand.
     */
    fun rankApps(candidates: List<DistractionCandidate>): List<RankedApp> {
        val sorted = candidates.sortedWith(
            compareByDescending<DistractionCandidate> { score(it) }
                .thenByDescending { it.avgDailyUsageMs }
                .thenBy { it.packageName }
        ).take(MAX_DISPLAYED_APPS)
        return sorted.mapIndexed { index, candidate ->
            val score = score(candidate)
            RankedApp(
                packageName = candidate.packageName,
                score = score,
                avgDailyUsageMs = candidate.avgDailyUsageMs,
                preChecked = index < MAX_PRE_CHECKED_APPS && score >= PRE_CHECK_SCORE_THRESHOLD
            )
        }
    }

    /**
     * Suggests a realistic daily budget for the selected apps: ~80% of the current average daily
     * usage, rounded to 15 minutes and clamped to [MIN_BUDGET_MS]..[MAX_BUDGET_MS]. Falls back to
     * [DEFAULT_BUDGET_MS] when there is no usage data.
     */
    fun suggestedDailyBudgetMs(avgDailySelectedUsageMs: Long): Long {
        if (avgDailySelectedUsageMs <= 0) return DEFAULT_BUDGET_MS
        val target = (avgDailySelectedUsageMs * 0.8).roundToLong()
        val quarterHour = TimeUnit.MINUTES.toMillis(15)
        val rounded = ((target + quarterHour / 2) / quarterHour) * quarterHour
        return rounded.coerceIn(MIN_BUDGET_MS, MAX_BUDGET_MS)
    }

    /** In the Strict preset, grayscale warns at this fraction of the block budget. */
    const val STRICT_GRAYSCALE_BUDGET_FRACTION = 0.6

    /**
     * The grayscale allowance for the Strict preset: apps turn gray at
     * [STRICT_GRAYSCALE_BUDGET_FRACTION] of the daily budget (rounded to 5 minutes, at least
     * 5 minutes) as an escalating warning before they are blocked outright at the full budget.
     * With identical thresholds the block would always win and the grayscale phase would never
     * be visible.
     */
    fun strictGrayscaleBudgetMs(budgetMs: Long): Long {
        val fiveMinutes = TimeUnit.MINUTES.toMillis(5)
        val target = (budgetMs * STRICT_GRAYSCALE_BUDGET_FRACTION).roundToLong()
        val rounded = ((target + fiveMinutes / 2) / fiveMinutes) * fiveMinutes
        return rounded.coerceAtLeast(fiveMinutes)
    }
}
