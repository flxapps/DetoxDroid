package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.ui.screens.onboarding.OnboardingState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Re-running onboarding rewrites the app lists and budgets of the features it configures, so it
 * has to answer to the commitment password like the settings screens do. Whether a given feature
 * is currently locked depends on the stored password hash, which lives in an
 * EncryptedSharedPreferences and needs a keystore the JVM does not have — what is checked here is
 * that the wizard's features can be locked at all, and that a password that is switched off keeps
 * the wizard reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingLockTest {
    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        CommitmentPasswordFeature.updateActivationState(false)
    }

    @Test
    fun everyFeatureOnboardingWritesIsCoveredByThePassword() {
        val lockable = CommitmentPasswordFeature.getLockableFeatures().map { it.id }.toSet()
        OnboardingState.configuredFeatures.forEach {
            assertTrue("${it.id} cannot be locked", it is LockableFeature)
            assertTrue("${it.id} is not offered in the lock list", it.id in lockable)
        }
    }

    @Test
    fun onboardingFeaturesAreLockedByDefault() {
        OnboardingState.configuredFeatures.forEach {
            assertTrue("${it.id} is not locked by default", (it as LockableFeature).lockedByDefault)
        }
    }

    @Test
    fun aDeactivatedPasswordLeavesOnboardingAlone() {
        CommitmentPasswordFeature.updateLockedFeatureIds(setOf(DisableAppsFeature.id))
        CommitmentPasswordFeature.updateActivationState(false)
        CommitmentPasswordFeature.lockSession()
        assertFalse(OnboardingState.isOnboardingLocked)
    }
}
