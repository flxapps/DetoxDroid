package com.flx_apps.digitaldetox

import android.Manifest
import android.app.Application
import android.provider.Settings
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.DISPLAY_DALTONIZER
import com.flx_apps.digitaldetox.features.DISPLAY_DALTONIZER_ENABLED
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.ScreenFilterMode
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The system color filter has a switch of its own in the system settings. While the feature holds
 * the filter in grayscale, switching it off there does not last; once the feature lets go, the
 * setting is the user's again.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: DetoxDroid's own hands the display filters back on a thread of its own when
// the service is off, which it is here, and that thread would race the test for the same settings.
@Config(application = Application::class, sdk = [34])
class GrayscaleSystemFilterTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver
    private val listed = "com.example.feed"
    private val unlisted = "com.example.maps"

    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = context
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 0)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER, -1)
        GrayscaleAppsFeature.screenFilterMode = ScreenFilterMode.OFF
        GrayscaleAppsFeature.systemGrayscale = true
        GrayscaleAppsFeature.extraDim = false
        GrayscaleAppsFeature.allowedDailyColorScreenTime = 0L
        GrayscaleAppsFeature.appExceptionListType = AppExceptionListType.ONLY_LIST
        GrayscaleAppsFeature.appExceptions = setOf(listed)
        GrayscaleAppsFeature.isActivated = true
    }

    @After
    fun tearDown() {
        GrayscaleAppsFeature.onPause(context)
        GrayscaleAppsFeature.isActivated = false
        GrayscaleAppsFeature.screenFilterMode = ScreenFilterMode.AUTO
        GrayscaleAppsFeature.extraDim = true
        GrayscaleAppsFeature.appExceptionListType = AppExceptionListType.NOT_LIST
        GrayscaleAppsFeature.appExceptions = emptySet()
    }

    private fun open(packageName: String) =
        GrayscaleAppsFeature.onAppOpened(context, packageName, AccessibilityEventUtil.createEvent())

    private fun filterEnabled() = Settings.Secure.getInt(resolver, DISPLAY_DALTONIZER_ENABLED, 0)
    private fun filterMode() = Settings.Secure.getInt(resolver, DISPLAY_DALTONIZER, -1)

    @Test
    fun switchingTheFilterOffInTheSystemSettingsDoesNotLast() {
        open(listed)
        assertEquals(1, filterEnabled())

        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 0)
        assertTrue(GrayscaleAppsFeature.reassertSystemFilter(context))
        assertEquals(1, filterEnabled())
        assertEquals(0, filterMode())
    }

    @Test
    fun pickingAnotherColorModeDoesNotLastEither() {
        open(listed)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER, 12)
        assertTrue(GrayscaleAppsFeature.reassertSystemFilter(context))
        assertEquals(0, filterMode())
    }

    @Test
    fun theNextLookAtTheSameAppPutsItBack() {
        open(listed)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 0)
        open(listed)
        assertEquals(1, filterEnabled())
    }

    @Test
    fun anUntouchedFilterIsLeftAlone() {
        open(listed)
        assertFalse(GrayscaleAppsFeature.reassertSystemFilter(context))
    }

    @Test
    fun outsideAListedAppTheSettingIsTheUsers() {
        open(listed)
        open(unlisted)
        assertEquals(0, filterEnabled())

        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 1)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER, 12)
        assertFalse(GrayscaleAppsFeature.reassertSystemFilter(context))
        assertEquals(12, filterMode())
    }

    @Test
    fun whatWasPutBackStillReturnsToTheUsersOwnSetting() {
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 1)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER, 12)
        open(listed)
        Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 0)
        GrayscaleAppsFeature.reassertSystemFilter(context)

        open(unlisted)
        assertEquals(1, filterEnabled())
        assertEquals(12, filterMode())
    }

    @Test
    fun aTugOfWarIsGivenUp() {
        open(listed)
        var putBack = 0
        repeat(10) {
            Settings.Secure.putInt(resolver, DISPLAY_DALTONIZER_ENABLED, 0)
            if (GrayscaleAppsFeature.reassertSystemFilter(context)) putBack++
        }
        assertEquals(5, putBack)
    }
}
