package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DoNotDisturbFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifies the "which features does a pause affect" logic: the [PausableFeature] set, the exclusion
 * config, and [PauseButtonFeature.isFeaturePaused] gating during an active pause.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PausableFeatureTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = RuntimeEnvironment.getApplication()
        PauseButtonFeature.isActivated = true
        PauseButtonFeature.pauseExemptFeatureIds = emptySet()
    }

    @After
    fun tearDown() {
        PauseButtonFeature.resume()
        PauseButtonFeature.pauseExemptFeatureIds = emptySet()
        PauseButtonFeature.isActivated = false
    }

    @Test
    fun onlyEnforcementFeaturesArePausable() {
        assertTrue((GrayscaleAppsFeature as Feature) is PausableFeature)
        assertTrue((DoNotDisturbFeature as Feature) is PausableFeature)
        assertTrue((BreakDoomScrollingFeature as Feature) is PausableFeature)
        assertTrue((DisableAppsFeature as Feature) is PausableFeature)
        // Meta-features are intentionally not pausable.
        assertFalse((PauseButtonFeature as Feature) is PausableFeature)
        assertFalse((CommitmentPasswordFeature as Feature) is PausableFeature)
    }

    @Test
    fun pausableFeaturesListMatchesTheFourEnforcementFeatures() {
        assertEquals(
            setOf(
                GrayscaleAppsFeature,
                DoNotDisturbFeature,
                BreakDoomScrollingFeature,
                DisableAppsFeature,
            ),
            PauseButtonFeature.pausableFeatures.toSet(),
        )
    }

    @Test
    fun noFeatureIsPausedWhileNotPausing() {
        // No pause is running.
        assertFalse(PauseButtonFeature.isPausing())
        assertFalse(PauseButtonFeature.isFeaturePaused(GrayscaleAppsFeature))
    }

    @Test
    fun byDefaultAPauseAffectsEveryPausableFeature() {
        PauseButtonFeature.pauseFeatures(context)
        assertTrue(PauseButtonFeature.isPausing())

        assertTrue(PauseButtonFeature.isFeaturePaused(GrayscaleAppsFeature))
        assertTrue(PauseButtonFeature.isFeaturePaused(DoNotDisturbFeature))
        assertTrue(PauseButtonFeature.isFeaturePaused(BreakDoomScrollingFeature))
        assertTrue(PauseButtonFeature.isFeaturePaused(DisableAppsFeature))
        // Non-pausable features are never reported as paused.
        assertFalse(PauseButtonFeature.isFeaturePaused(CommitmentPasswordFeature))
    }

    @Test
    fun anExemptFeatureKeepsRunningDuringAPause() {
        PauseButtonFeature.pauseExemptFeatureIds = setOf(DoNotDisturbFeature.id)
        PauseButtonFeature.pauseFeatures(context)

        assertFalse(PauseButtonFeature.isFeaturePaused(DoNotDisturbFeature))
        // Other features are still suspended.
        assertTrue(PauseButtonFeature.isFeaturePaused(GrayscaleAppsFeature))
    }
}
