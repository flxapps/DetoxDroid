package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The passphrase lives in an EncryptedSharedPreferences behind the AndroidKeyStore, and a keystore
 * can refuse to hand back what it once encrypted - after a restored backup, or on a ROM that drops
 * keys. Every screen asks whether a password is set while it composes, so a refusal there used to
 * take the whole app down with an AEADBadTagException.
 *
 * The JVM has no AndroidKeyStore at all, which is the same refusal at its most complete: nothing
 * here may throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommitmentPasswordStoreTest {
    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = RuntimeEnvironment.getApplication()
    }

    @Test
    fun aKeystoreThatGivesNoStoreLeavesThePasswordUnset() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(CommitmentPasswordFeature.isPasswordSet(context))
        assertFalse(CommitmentPasswordFeature.verifyPassword(context, "wheat-lunar-onyx"))
        assertFalse(CommitmentPasswordFeature.setPassword(context, "wheat-lunar-onyx"))
    }

    @Test
    fun withoutAStoreNoFeatureReportsItselfLocked() {
        CommitmentPasswordFeature.isActivated = true
        try {
            CommitmentPasswordFeature.getLockableFeatures().forEach {
                assertFalse(it.id, CommitmentPasswordFeature.isFeatureLocked(it.id))
            }
        } finally {
            CommitmentPasswordFeature.isActivated = false
        }
    }
}
