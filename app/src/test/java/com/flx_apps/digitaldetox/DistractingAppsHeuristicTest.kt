package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.util.DistractingAppsHeuristic
import com.flx_apps.digitaldetox.util.DistractionCandidate
import com.flx_apps.digitaldetox.util.KnownAppCategory
import com.flx_apps.digitaldetox.util.knownCategoryOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class DistractingAppsHeuristicTest {
    private fun candidate(
        packageName: String = "com.example.app",
        knownCategory: KnownAppCategory? = null,
        osCategory: Int = DistractingAppsHeuristic.OS_CATEGORY_UNDEFINED,
        avgDailyUsageMs: Long = 0L
    ) = DistractionCandidate(packageName, knownCategory, osCategory, avgDailyUsageMs)

    // region category knowledge base
    @Test
    fun `known package sets resolve to their category`() {
        assertEquals(KnownAppCategory.SOCIAL, knownCategoryOf("com.instagram.android"))
        assertEquals(KnownAppCategory.SOCIAL, knownCategoryOf("com.zhiliaoapp.musically")) // TikTok
        assertEquals(KnownAppCategory.VIDEO, knownCategoryOf("com.google.android.youtube"))
        assertEquals(KnownAppCategory.DATING, knownCategoryOf("com.tinder"))
        assertEquals(KnownAppCategory.MUSIC, knownCategoryOf("com.spotify.music"))
        assertNull(knownCategoryOf("com.unknown.app"))
    }
    // endregion

    // region category weights
    @Test
    fun `category weights follow the distraction table`() {
        assertEquals(1.0, DistractingAppsHeuristic.categoryWeight(KnownAppCategory.SOCIAL, -1), 0.0)
        assertEquals(1.0, DistractingAppsHeuristic.categoryWeight(KnownAppCategory.VIDEO, -1), 0.0)
        assertEquals(0.9, DistractingAppsHeuristic.categoryWeight(KnownAppCategory.DATING, -1), 0.0)
        assertEquals(0.7, DistractingAppsHeuristic.categoryWeight(KnownAppCategory.SHOPPING, -1), 0.0)
        assertEquals(0.4, DistractingAppsHeuristic.categoryWeight(KnownAppCategory.MUSIC, -1), 0.0)
        assertEquals(0.3, DistractingAppsHeuristic.categoryWeight(KnownAppCategory.TRAVEL, -1), 0.0)
        assertEquals(
            1.0,
            DistractingAppsHeuristic.categoryWeight(null, DistractingAppsHeuristic.OS_CATEGORY_SOCIAL),
            0.0
        )
        assertEquals(
            0.9,
            DistractingAppsHeuristic.categoryWeight(null, DistractingAppsHeuristic.OS_CATEGORY_GAME),
            0.0
        )
        assertEquals(
            0.7,
            DistractingAppsHeuristic.categoryWeight(null, DistractingAppsHeuristic.OS_CATEGORY_NEWS),
            0.0
        )
        assertEquals(0.25, DistractingAppsHeuristic.categoryWeight(null, -1), 0.0)
    }

    @Test
    fun `known category wins over os category`() {
        // Spotify is known MUSIC even if the OS labels it social
        assertEquals(
            0.4,
            DistractingAppsHeuristic.categoryWeight(
                KnownAppCategory.MUSIC, DistractingAppsHeuristic.OS_CATEGORY_SOCIAL
            ),
            0.0
        )
    }
    // endregion

    // region score blend
    @Test
    fun `heavy usage alone surfaces an unknown app`() {
        val score = DistractingAppsHeuristic.score(
            candidate(avgDailyUsageMs = TimeUnit.HOURS.toMillis(2))
        )
        assertEquals(0.55 * 0.25 + 0.45, score, 1e-9)
        assertTrue(score >= DistractingAppsHeuristic.PRE_CHECK_SCORE_THRESHOLD)
    }

    @Test
    fun `known social app with zero usage still crosses the pre-check threshold`() {
        val score = DistractingAppsHeuristic.score(candidate(knownCategory = KnownAppCategory.SOCIAL))
        assertEquals(0.55, score, 1e-9)
        assertTrue(score >= DistractingAppsHeuristic.PRE_CHECK_SCORE_THRESHOLD)
    }

    @Test
    fun `usage score saturates at two hours per day`() {
        val twoHours = DistractingAppsHeuristic.score(
            candidate(avgDailyUsageMs = TimeUnit.HOURS.toMillis(2))
        )
        val fiveHours = DistractingAppsHeuristic.score(
            candidate(avgDailyUsageMs = TimeUnit.HOURS.toMillis(5))
        )
        assertEquals(twoHours, fiveHours, 1e-9)
    }
    // endregion

    // region ranking + pre-check
    @Test
    fun `ranking is by score then usage then package name`() {
        val ranked = DistractingAppsHeuristic.rankApps(
            listOf(
                candidate(packageName = "b.same.score", knownCategory = KnownAppCategory.SOCIAL),
                candidate(packageName = "a.same.score", knownCategory = KnownAppCategory.SOCIAL),
                candidate(
                    packageName = "c.more.usage",
                    knownCategory = KnownAppCategory.SOCIAL,
                    avgDailyUsageMs = TimeUnit.MINUTES.toMillis(30)
                ),
                candidate(packageName = "d.low.weight")
            )
        )
        assertEquals(
            listOf("c.more.usage", "a.same.score", "b.same.score", "d.low.weight"),
            ranked.map { it.packageName }
        )
    }

    @Test
    fun `display list is capped and low scorers are not pre-checked`() {
        val manySocial = (1..40).map {
            candidate(packageName = "social.app.%02d".format(it), knownCategory = KnownAppCategory.SOCIAL)
        }
        val ranked = DistractingAppsHeuristic.rankApps(manySocial)
        assertEquals(DistractingAppsHeuristic.MAX_DISPLAYED_APPS, ranked.size)
        assertEquals(
            DistractingAppsHeuristic.MAX_PRE_CHECKED_APPS,
            ranked.count { it.preChecked }
        )
        // the cap keeps the highest-ranked apps checked
        assertTrue(ranked.take(DistractingAppsHeuristic.MAX_PRE_CHECKED_APPS).all { it.preChecked })
    }

    @Test
    fun `apps below the threshold are never pre-checked`() {
        val ranked = DistractingAppsHeuristic.rankApps(
            listOf(candidate(packageName = "boring.app"))
        )
        assertFalse(ranked.single().preChecked)
    }
    // endregion

    // region budget suggestion
    @Test
    fun `budget is eighty percent of average rounded to a quarter hour`() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(75),
            DistractingAppsHeuristic.suggestedDailyBudgetMs(TimeUnit.MINUTES.toMillis(100))
        )
        assertEquals(
            TimeUnit.MINUTES.toMillis(90),
            DistractingAppsHeuristic.suggestedDailyBudgetMs(TimeUnit.MINUTES.toMillis(110))
        )
    }

    @Test
    fun `budget is clamped to fifteen minutes and three hours`() {
        assertEquals(
            DistractingAppsHeuristic.MIN_BUDGET_MS,
            DistractingAppsHeuristic.suggestedDailyBudgetMs(TimeUnit.MINUTES.toMillis(5))
        )
        assertEquals(
            DistractingAppsHeuristic.MAX_BUDGET_MS,
            DistractingAppsHeuristic.suggestedDailyBudgetMs(TimeUnit.HOURS.toMillis(8))
        )
    }

    @Test
    fun `budget falls back to one hour without usage data`() {
        assertEquals(
            DistractingAppsHeuristic.DEFAULT_BUDGET_MS,
            DistractingAppsHeuristic.suggestedDailyBudgetMs(0)
        )
    }

    @Test
    fun `strict grayscale allowance is sixty percent rounded to five minutes`() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(35),
            DistractingAppsHeuristic.strictGrayscaleBudgetMs(TimeUnit.MINUTES.toMillis(60))
        )
        assertEquals(
            TimeUnit.MINUTES.toMillis(55),
            DistractingAppsHeuristic.strictGrayscaleBudgetMs(TimeUnit.MINUTES.toMillis(90))
        )
    }

    @Test
    fun `strict grayscale allowance stays below the block budget and above five minutes`() {
        val minBudget = DistractingAppsHeuristic.MIN_BUDGET_MS
        val grayscale = DistractingAppsHeuristic.strictGrayscaleBudgetMs(minBudget)
        assertTrue(grayscale in TimeUnit.MINUTES.toMillis(5)..minBudget)
        assertTrue(
            DistractingAppsHeuristic.strictGrayscaleBudgetMs(DistractingAppsHeuristic.MAX_BUDGET_MS)
                    < DistractingAppsHeuristic.MAX_BUDGET_MS
        )
    }
    // endregion
}
