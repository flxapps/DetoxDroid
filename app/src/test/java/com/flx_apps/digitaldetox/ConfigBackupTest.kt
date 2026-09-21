package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.data.ConfigBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A settings file is read back by a build that may be newer than the one that wrote it, on a phone
 * where the preferences it names may not exist yet. What it has to keep is the type of every value
 * ("5" is not 5, and a one-element set is not a string), and it has to keep out the two things a
 * restore must never be able to change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigBackupTest {
    @Test
    fun everyValueComesBackAsWhatItWas() {
        val values = mapOf(
            "GrayscaleAppsFeature_isActivated" to true,
            "UsageStatsScreen_hourlyRate" to 25,
            "DisableAppsFeature_allowedDailyScreenTime" to 1_800_000L,
            "SomeFeature_ratio" to 0.5f,
            "SomeFeature_precise" to 0.125,
            "DisableAppsFeature_disableAppsMode" to "SUSPEND",
            "GrayscaleAppsFeature_appExceptions" to setOf("com.instagram.android", "com.x.app")
        )
        val restored = ConfigBackup.parse(ConfigBackup.serialize(values))
        assertEquals(values.keys, restored.keys)
        for ((key, value) in values) {
            assertEquals(key, value, restored[key])
        }
        assertTrue(restored["GrayscaleAppsFeature_isActivated"] is Boolean)
        assertTrue(restored["UsageStatsScreen_hourlyRate"] is Int)
        assertTrue(restored["DisableAppsFeature_allowedDailyScreenTime"] is Long)
        assertTrue(restored["GrayscaleAppsFeature_appExceptions"] is Set<*>)
    }

    @Test
    fun theLockAndTheEntitlementAreNotInABackup() {
        assertFalse(ConfigBackup.isBackedUp("CommitmentPasswordFeature_isActivated"))
        assertFalse(ConfigBackup.isBackedUp("CommitmentPasswordFeature_lockedFeatureIds"))
        assertFalse(ConfigBackup.isBackedUp("premium_unlocked"))
        assertTrue(ConfigBackup.isBackedUp("GrayscaleAppsFeature_isActivated"))
    }

    @Test
    fun anEntryOfAnUnknownTypeIsSkippedRatherThanFailingTheFile() {
        val fromALaterVersion = """
            {
              "formatVersion": 99,
              "values": {
                "Known_key": { "type": "int", "value": 3 },
                "Unknown_key": { "type": "duration", "value": "PT1H" }
              }
            }
        """.trimIndent()
        assertEquals(mapOf<String, Any>("Known_key" to 3), ConfigBackup.parse(fromALaterVersion))
    }

    @Test(expected = IllegalArgumentException::class)
    fun someOtherJsonFileIsRefused() {
        ConfigBackup.parse("""{ "notes": ["a shopping list"] }""")
    }
}
