package com.flx_apps.digitaldetox.screenshot

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelProvider
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DailyAppUsageDao
import com.flx_apps.digitaldetox.data.DailyGrayscaleStatsDao
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingOverlay
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavHostScreen
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.usage_stats.TrendsCarouselTestTag
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * Generates the Play Store / F-Droid phone screenshots into
 * `fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png … 6.png`.
 *
 * Real app screens (home, usage-stats) are driven through the real [MainActivity]/[NavHostScreen]
 * — they cannot be instantiated standalone because `NavViewModel.navViewModel()` casts the local
 * activity to [MainActivity] and reimagined `hiltViewModel()` needs a nav-entry scope. The
 * grayscale / exceptions / doomscrolling / minimal-launcher beats are static mock scenes (see
 * [MockScenes]), per product decision, since Roborazzi can only render DetoxDroid's own UI.
 *
 * Run with:
 *   ./gradlew :app:generateScreenshots
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = PHONE_QUALIFIERS)
class StoreScreenshotTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * Runs between Hilt setup and the activity launch (order 1 of 3): DetoxDroid's singletons read
     * from [com.flx_apps.digitaldetox.DetoxDroidApplication.appContext], which is never set under
     * HiltTestApplication — so it must exist before [MainActivity] first renders the home screen.
     */
    @get:Rule(order = 1)
    val appContextRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                ScreenshotSeed.seedAppContext(RuntimeEnvironment.getApplication())
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var dailyAppUsageDao: DailyAppUsageDao

    @Inject
    lateinit var dailyGrayscaleStatsDao: DailyGrayscaleStatsDao

    @Before
    fun setUp() {
        // Only run when explicitly recording/verifying screenshots (i.e. via `generateScreenshots`
        // or -Proborazzi.test.record/verify), so a plain `testDebugUnitTest` doesn't regenerate the
        // store assets as a side effect.
        assumeTrue(
            "Skipping store screenshots — run ./gradlew :app:generateScreenshots to record them.",
            System.getProperty("roborazzi.test.record") != null ||
                System.getProperty("roborazzi.test.verify") != null,
        )
        hiltRule.inject()
        val app = RuntimeEnvironment.getApplication()
        ScreenshotSeed.seedActiveState()
        ScreenshotSeed.installFakeApps(app)
        ScreenshotSeed.seedTodayUsageStats(app)
        runBlocking { ScreenshotSeed.seedHistory(dailyAppUsageDao, dailyGrayscaleStatsDao) }
    }

    // ── 1 · Home ─────────────────────────────────────────────────────────────
    @Test
    fun screenshot_1_home() = captureApp(
        index = 1,
        titleRes = R.string.screenshot_1_title,
        subtitleRes = R.string.screenshot_1_subtitle,
        navigate = { /* Home is the start destination — no navigation needed */ },
    )

    // ── 2 · Grayscale ────────────────────────────────────────────────────────
    @Test
    fun screenshot_2_grayscale() = captureScene(
        index = 2,
        titleRes = R.string.screenshot_2_title,
        subtitleRes = R.string.screenshot_2_subtitle,
    ) { MockGrayscaleSplit() }

    // ── 3 · App exceptions (kept in colour) ──────────────────────────────────
    @Test
    fun screenshot_3_exceptions() = captureScene(
        index = 3,
        titleRes = R.string.screenshot_3_title,
        subtitleRes = R.string.screenshot_3_subtitle,
    ) { MockExceptionsScene() }

    // ── 4 · Break doomscrolling ──────────────────────────────────────────────
    @Test
    fun screenshot_4_doomscrolling() = captureScene(
        index = 4,
        titleRes = R.string.screenshot_4_title,
        subtitleRes = R.string.screenshot_4_subtitle,
    ) {
        Box(Modifier.fillMaxSize()) {
            MockSocialFeed(Modifier.grayscale())
            BreakDoomScrollingOverlay()
        }
    }

    // ── 5 · Usage stats — In Perspective ─────────────────────────────────────
    @Test
    fun screenshot_5_usageStats() = captureApp(
        index = 5,
        titleRes = R.string.screenshot_5_title,
        subtitleRes = R.string.screenshot_5_subtitle,
        navigate = { navVm -> navVm.openRoute(NavigationRoutes.UsageStats) },
        interact = {
            // Switch to the 7-day timeframe (premium is unlocked in the seed) so the weekly history
            // backed by the seeded Room data is shown instead of today's summary.
            composeRule.onNodeWithText("7 Days").performClick()
            settle()
            // Advance the trends carousel to its "In Perspective" page — scroll distance reframed as
            // a physical distance against a landmark, a far stronger visual than the raw summary.
            // Swipe the carousel node itself, not the root: a root swipe lands at the screen's
            // vertical centre (below the carousel) and scrolls nothing.
            composeRule.onNodeWithTag(TrendsCarouselTestTag).performTouchInput { swipeLeft() }
        },
    )

    // ── 6 · Minimal launcher (text-only home screen) ─────────────────────────
    @Test
    fun screenshot_6_minimalLauncher() = captureScene(
        index = 6,
        titleRes = R.string.screenshot_6_title,
        subtitleRes = R.string.screenshot_6_subtitle,
    ) { MockMinimalLauncher() }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Captures a static mock scene wrapped in the marketing card. */
    private fun captureScene(
        index: Int,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        scene: @Composable () -> Unit,
    ) = forEachLocale { locale ->
        val activity = composeRule.activity
        val title = activity.getString(titleRes)
        val subtitle = activity.getString(subtitleRes)
        activity.runOnUiThread {
            activity.setContent {
                DetoxDroidTheme(darkTheme = false, dynamicColor = false) {
                    MarketingCard(title, subtitle) { scene() }
                }
            }
        }
        settleAndCapture(index, locale)
    }

    /** Renders the real app (NavHostScreen) in the marketing card, then runs [navigate]. */
    private fun captureApp(
        index: Int,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        navigate: (NavViewModel) -> Unit,
        interact: () -> Unit = {},
    ) = forEachLocale { locale ->
        val activity = composeRule.activity
        val title = activity.getString(titleRes)
        val subtitle = activity.getString(subtitleRes)
        activity.runOnUiThread {
            activity.setContent {
                DetoxDroidTheme(darkTheme = false, dynamicColor = false) {
                    MarketingCard(title, subtitle) { NavHostScreen() }
                }
            }
        }
        val navVm = ViewModelProvider(activity)[NavViewModel::class.java]
        activity.runOnUiThread { navigate(navVm) }
        settle()
        interact()
        settleAndCapture(index, locale)
    }

    private inline fun forEachLocale(block: (MarketingLocale) -> Unit) {
        MARKETING_LOCALES.forEach { locale ->
            RuntimeEnvironment.setQualifiers("${locale.qualifier}-$PHONE_QUALIFIERS")
            block(locale)
        }
    }

    private fun settleAndCapture(index: Int, locale: MarketingLocale) {
        settle()
        composeRule.activity.window.decorView.rootView.captureRoboImage(
            screenshotPath(locale.bcp47, index)
        )
    }

    /**
     * Drains both Compose and the main looper, with short real sleeps so background
     * `viewModelScope.launch { withContext(Dispatchers.IO) { … } }` loads (e.g. the usage-stats
     * screen) resume on the main looper and repopulate the UI before capture.
     */
    private fun settle() {
        repeat(20) {
            composeRule.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
