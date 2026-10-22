package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.data.ConfigBackup
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeatureId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
        val restored = ConfigBackup.read(ConfigBackup.serialize(values)).values
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
    fun whatDescribesThisPhoneIsNotInABackup() {
        listOf(
            "AppSuspender_suspendedApps",
            "trustedClock_anchor",
            "GrayscaleAppsFeature_daltonizerApplied",
            "GrayscaleAppsFeature_defaultDaltonizerEnabled",
            "GrayscaleAppsFeature_extraDimApplied",
            "DisableAppsFeature_usedUpScreenTime",
            "minimal_launcher_widget_selected_apps_42",
            "accessibilityService_disclosureAccepted",
            "widget_style_theme_42",
            "widget_style_backgroundOpacity_42",
        ).forEach { assertFalse(it, ConfigBackup.isBackedUp(it)) }
        listOf(
            "GrayscaleAppsFeature_exceptions",
            "GrayscaleAppsFeature_systemGrayscale",
            "appearance_themeMode",
            "onboarding_completed",
        ).forEach { assertTrue(it, ConfigBackup.isBackedUp(it)) }
    }

    @Test
    fun thePreviewSaysWhatAFeatureHoldsInTheFile() = runBlocking {
        DetoxDroidApplication.appContext = RuntimeEnvironment.getApplication()
        val file = ConfigBackup.serialize(
            mapOf(
                "GrayscaleAppsFeature_isActivated" to true,
                "GrayscaleAppsFeature_exceptions" to setOf("com.instagram.android", "com.x.app"),
                "Nobody_knowsThis" to 1,
            )
        )
        val preview = ConfigBackup.preview(file)
        val grayscale = preview.features.single { it.featureId == GrayscaleAppsFeatureId }
        assertEquals(true, grayscale.isActivated)
        assertEquals(2, grayscale.appCount)
        assertEquals(1, preview.otherSettings)
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
        assertEquals(mapOf<String, Any>("Known_key" to 3), ConfigBackup.read(fromALaterVersion).values)
    }

    @Test(expected = IllegalArgumentException::class)
    fun someOtherJsonFileIsRefused() {
        ConfigBackup.read("""{ "notes": ["a shopping list"] }""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun somethingThatIsNotJsonIsRefusedTheSameWay() {
        ConfigBackup.read("<html>not even close</html>")
    }

    @Test
    fun theFileSaysWhichBuildWroteItAndWhen() {
        val file = ConfigBackup.read(
            ConfigBackup.serialize(mapOf("A_b" to 1), appVersionName = "2.9.0", appVersionCode = 20900)
        )
        assertEquals("2.9.0", file.appVersionName)
        assertEquals(20900, file.appVersionCode)
        assertEquals(java.time.LocalDate.now(), file.createdAt)
    }

    @Test
    fun aFileWithoutAHeaderStillReads() {
        val file = ConfigBackup.read("""{ "values": { "A_b": { "type": "boolean", "value": true } } }""")
        assertEquals(null, file.createdAt)
        assertEquals(null, file.appVersionName)
        assertEquals(null, file.appVersionCode)
        assertEquals(mapOf<String, Any>("A_b" to true), file.values)
    }

    @Test
    fun anEntryWhoseTypeChangedSinceIsDroppedInsteadOfBreakingTheReader() {
        val fromTheFile = mapOf<String, Any>(
            "Feature_budget" to 30,            // once an Int, now stored as a Long
            "Feature_isActivated" to true,
            "Feature_appExceptions" to setOf("a"),
            "Feature_newSetting" to "x"         // not on this phone yet
        )
        val current = mapOf<String, Any>(
            "Feature_budget" to 30L,
            "Feature_isActivated" to false,
            "Feature_appExceptions" to hashSetOf("b")
        )
        val applicable = ConfigBackup.applicable(fromTheFile, current)
        assertEquals(
            setOf("Feature_isActivated", "Feature_appExceptions", "Feature_newSetting"),
            applicable.keys
        )
    }
}
