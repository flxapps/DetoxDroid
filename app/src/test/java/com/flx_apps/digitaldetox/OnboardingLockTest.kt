package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.rules.RuleGroups
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
 * Re-running onboarding adds a group, so it has to answer to the commitment password like the
 * group screens do. Whether the groups are currently locked depends on the stored password hash,
 * which lives in an EncryptedSharedPreferences and needs a keystore the JVM does not have — what
 * is checked here is that the groups can be locked at all, and that a password that is switched
 * off keeps the wizard reachable.
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
    fun aGroupIsCoveredByThePassword() {
        val group = RuleGroups.create { name = "Feeds" }
        try {
            val lockable = CommitmentPasswordFeature.getLockables().first { it.id == group.id }
            assertTrue("a group is not locked by default", lockable.lockedByDefault)
        } finally {
            RuleGroups.delete(group.id)
        }
    }

    @Test
    fun aDeactivatedPasswordLeavesOnboardingAlone() {
        CommitmentPasswordFeature.updateLockedFeatureIds(setOf(RuleGroups.LOCK_ID))
        CommitmentPasswordFeature.updateActivationState(false)
        CommitmentPasswordFeature.lockSession()
        assertFalse(OnboardingState.isOnboardingLocked)
    }
}
