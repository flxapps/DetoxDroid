package com.flx_apps.digitaldetox.features

import android.content.ContentResolver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.NeedsWriteSecureSettingsPermission
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.ScreenTimeTrackingFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature.eventuallyIncreaseUsedUpScreenTime
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature.onAppOpened
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.ScreenFilterOverlay
import com.flx_apps.digitaldetox.system_integration.ScreenFilterSpec
import com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps.GrayscaleAppsFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil

const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
const val EXTRA_DIM = "reduce_bright_colors_activated"

/**
 * When the [ScreenFilterOverlay] is used instead of (or on top of) the system grayscale filter.
 * [AUTO] picks it exactly when the real filter is out of reach, so the feature does something
 * useful on a phone that never saw an adb cable, and stays out of the way on one that did.
 */
enum class ScreenFilterMode {
    AUTO, ON, OFF
}

/**
 * Which halves of the screen filter run. Kept as the two switches it maps to rather than as its own
 * stored value, so a preference survives a device that cannot blur.
 */
enum class ScreenFilterEffects {
    SHADE, BLUR, SHADE_AND_BLUR
}

/** Below this the wash is invisible, so the picker starts here rather than at 0. */
const val MinScreenFilterIntensity = 10

/** Blur radius in dp at full intensity: a feed reduced to shapes, headlines still guessable. */
const val MaxScreenFilterBlurDp = 3.5f

val GrayscaleAppsFeatureId = Feature.createId(GrayscaleAppsFeature::class.java)

/**
 * The grayscale feature can be used to turn the screen grayscale depending on the schedule and
 * which app is currently in the foreground.
 */
object GrayscaleAppsFeature : Feature(), OnAppOpenedSubscriptionFeature,
    OnScreenTurnedOffSubscriptionFeature,
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(GrayscaleAppsFeatureId),
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(GrayscaleAppsFeatureId),
    ScreenTimeTrackingFeature by ScreenTimeTrackingFeature.Impl(GrayscaleAppsFeatureId),
    NeedsPermissionsFeature, LockableFeature, PausableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        R.string.feature_grayscale,
        R.string.feature_grayscale_subtitle,
        R.string.feature_grayscale_description,
    )
    override val iconRes: Int = R.drawable.ic_contrast
    override val settingsContent: @Composable () -> Unit = { GrayscaleAppsFeatureSettingsSection() }

    /**
     * Represents, whether the feature's effects (system grayscale and/or the screen filter) are
     * currently applied. We use this variable in order to avoid unnecessary calls to the system
     * settings and to the window manager.
     */
    private var areEffectsActive: Boolean = false

    /**
     * Whether *we* currently hold the system color filter in grayscale. Tracked apart from
     * [areEffectsActive], because the feature can be busy filtering the screen while the
     * daltonizer is not in use at all.
     *
     * Persisted, because the setting outlives the process. The accessibility service can be killed
     * without [onPause] ever running, and the filter it left behind would then have nothing left to
     * switch it off: the screen stays gray until someone finds the toggle in the system settings.
     */
    private var isDaltonizerApplied: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_daltonizerApplied"), false
    )

    /**
     * Delegate for the WRITE_SECURE_SETTINGS permission. The feature no longer stands or falls
     * with it: without the permission it runs the [ScreenFilterOverlay] instead of the system
     * grayscale filter, so [hasPermissions] only fails when the user turned that off as well.
     */
    private val writeSecureSettingsPermission = NeedsWriteSecureSettingsPermission()

    /**
     * The package DetoxDroid itself runs in. Its own screens are never filtered: the filter is
     * configured on them, and a washed-out, blurred settings screen is both hard to read and hard
     * to get back out of.
     */
    private const val OwnPackage = "com.flx_apps.digitaldetox"

    /**
     * We use this for people who use a color filter for color blindness. If the user has a color
     * filter enabled, we want to save the current filter and restore it when the grayscale filter
     * is turned off.
     * -1 means that the user does not have a color filter enabled. Persisted alongside
     * [isDaltonizerApplied], so a process death cannot lose the setting we owe the user back.
     */
    private var defaultDaltonizer: Int by DataStoreProperty(
        intPreferencesKey("${id}_defaultDaltonizer"), -1
    )

    /**
     * We use this to check whether the daltonizer was enabled before we turned on the grayscale
     * filter. If it was not enabled, we want to disable it again when the grayscale filter is
     * turned off. Persisted for the same reason as [defaultDaltonizer].
     */
    private var defaultDaltonizerEnabled: Int by DataStoreProperty(
        intPreferencesKey("${id}_defaultDaltonizerEnabled"), 0
    )

    /**
     * Represents, whether the extra dim filter is currently active.
     * We use this variable in order to avoid unnecessary calls to the system settings.
     *
     * Persisted, and the only record there is: since Android 12 [EXTRA_DIM] cannot be read back by
     * an app targeting above S, so a fresh process has no way to ask the system whether extra dim
     * is on. An in-memory flag starting at false would report "already off" and skip the write that
     * would actually switch it off, leaving the screen dimmed for good.
     */
    private var isCurrentlyExtraDim: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_extraDimApplied"), false
    )

    /**
     * Whether the extra dim filter should be turned on when the grayscale filter is active.
     */
    var extraDim: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_extraDim"), true
    )

    /**
     * Whether the system color filter is switched to grayscale. Needs WRITE_SECURE_SETTINGS, and
     * is what the feature did exclusively before the screen filter existed, hence the default.
     */
    var systemGrayscale: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_systemGrayscale"), true
    )

    /**
     * Whether the screen filter overlay is used instead of the system grayscale filter.
     * @see ScreenFilterMode
     */
    var screenFilterMode: ScreenFilterMode by DataStoreProperty(
        key = stringPreferencesKey("${id}_screenFilterMode"),
        defaultValue = ScreenFilterMode.AUTO,
        dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(
            ScreenFilterMode::class.java
        )
    )

    /**
     * How strong the screen filter is, in percent. Drives both the gray wash and the blur radius,
     * so there is one knob instead of three.
     */
    var screenFilterIntensity: Int by DataStoreProperty(
        intPreferencesKey("${id}_screenFilterIntensity"), 60
    )

    /**
     * Whether the screen filter also blurs what is behind it. Requires Android 12 or newer and a
     * device that supports cross-window blurs; where it does not, the wash runs on its own.
     */
    var screenFilterBlur: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_screenFilterBlur"), true
    )

    /**
     * Whether the screen filter washes the color out of what is behind it.
     */
    var screenFilterShade: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_screenFilterShade"), true
    )

    /**
     * The two switches above as the single choice the settings screen offers.
     */
    var screenFilterEffects: ScreenFilterEffects
        get() = when {
            screenFilterShade && screenFilterBlur -> ScreenFilterEffects.SHADE_AND_BLUR
            screenFilterBlur -> ScreenFilterEffects.BLUR
            else -> ScreenFilterEffects.SHADE
        }
        set(value) {
            screenFilterShade = value != ScreenFilterEffects.BLUR
            screenFilterBlur = value != ScreenFilterEffects.SHADE
        }

    /**
     * Whether the grayscale filter should be ignored when the current app is not in full screen mode.
     * This is the case for example when the keyboard, sound controls or the notification bar are
     * shown.
     */
    var ignoreNonFullScreenApps: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_ignoreNonFullScreenApps"), true
    )

    /**
     * The allowed daily color screen time in milliseconds. Once this limit is reached, the grayscale
     * filter will be turned on and will stay on until the next day.
     */
    var allowedDailyColorScreenTime: Long by DataStoreProperty(
        longPreferencesKey("${id}_allowedDailyColorScreenTime"), 0L
    )

    /**
     * Time actually spent with the grayscale filter applied today. Only the portion of tracked
     * screen time above the [allowedDailyColorScreenTime] allowance counts — the allowance
     * portion is spent in color.
     */
    fun currentGrayscaleTimeMs(): Long =
        (currentUsedUpScreenTime() - allowedDailyColorScreenTime).coerceAtLeast(0L)

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Fires when the color-screen-time allowance is expected to run out mid-session. Without it,
     * the filter would only engage on the next window change, so a user could stay inside a single
     * color app indefinitely. [ScreenTimeTrackingFeature.trackingSinceTimestamp] doubles as the
     * "still in a color app" signal: leaving the app or turning the screen off resets it.
     */
    private val allowanceExhaustedChecker = Runnable {
        kotlin.runCatching {
            if (!isActive() || trackingSinceTimestamp == 0L) return@Runnable
            if (DetoxDroidAccessibilityService.updateState() != DetoxDroidState.Active) return@Runnable
            if (allowedDailyColorScreenTime > 0 && currentUsedUpScreenTime() > allowedDailyColorScreenTime) {
                setEffectsActive(DetoxDroidApplication.appContext, true)
            }
        }
    }

    private fun scheduleAllowanceExhaustedCheck() {
        mainHandler.removeCallbacks(allowanceExhaustedChecker)
        val remainingMs = allowedDailyColorScreenTime - currentUsedUpScreenTime()
        if (remainingMs <= 0) return
        mainHandler.postDelayed(allowanceExhaustedChecker, remainingMs + 250)
    }

    /**
     * On start, we trigger [onAppOpened] once to turn the grayscale filter on or off depending on
     * the current app. We also read the actual system state to initialize cached filter state, so
     * saved defaults are not overwritten if the service (re-)starts while filters are active.
     */
    override fun onStart(context: Context) {
        // a filter left over from an earlier run of the service would never be removed: the state
        // below starts out "off", so the first evaluation that also says "off" changes nothing
        ScreenFilterOverlay.apply(context, null)
        // Hand the system settings back before anything else, so the evaluation below starts from a
        // known-clean screen.
        reclaimStrandedFilters(context)
        restoreSystemFilters(context)
        areEffectsActive = false
        val accessibilityEvent = AccessibilityEventUtil.createEvent()
        // Evaluate against the actual foreground app when the service knows it: after a resume
        // (or feature toggle) no new window event arrives while the user stays inside the current
        // app, so dispatching the synthetic event's own package would leave the filter off until
        // the next app switch.
        val foregroundPackage =
            DetoxDroidAccessibilityService.instance?.currentForegroundPackage?.takeIf { it.isNotEmpty() }
                ?: accessibilityEvent.packageName.toString()
        onAppOpened(context, foregroundPackage, accessibilityEvent)
    }

    /**
     * On a pause, turn the grayscale filter off.
     */
    override fun onPause(context: Context) {
        mainHandler.removeCallbacks(allowanceExhaustedChecker)
        setEffectsActive(context, false)
    }

    /**
     * If an app is opened, turn the grayscale filter on or off depending on the app that is
     * currently in the foreground.
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        if (ignoreNonFullScreenApps && !accessibilityEvent.isFullScreen) {
            // we are not in full screen mode, so we do not want to interfere with the app
            return
        }
        // the effects should be on while an app covered by the exception list config is in the
        // foreground (and the daily color allowance, if any, is used up)
        val shouldApplyEffects = packageName != OwnPackage && appliesTo(packageName)

        if (!shouldApplyEffects) {
            // the effects should not be turned on, so we increase the used up screen time
            eventuallyIncreaseUsedUpScreenTime()
        } else {
            eventuallyStartTracking()
            // currentUsedUpScreenTime() includes the running tracking session, so the allowance
            // also runs out while the user stays inside a single color app
            if (allowedDailyColorScreenTime > 0 && currentUsedUpScreenTime() <= allowedDailyColorScreenTime) {
                // allowance available → the user gets color; also lift a still-active filter
                // (e.g. right after the midnight reset or after the allowance was raised)
                if (areEffectsActive) setEffectsActive(context, false)
                // re-check when the allowance is expected to be used up, because no further
                // window events arrive while the user stays in this app
                scheduleAllowanceExhaustedCheck()
                return
            }
        }

        if (shouldApplyEffects != areEffectsActive) {
            // only touch the system settings and the window manager if the state should change
            setEffectsActive(context, shouldApplyEffects)
            areEffectsActive = shouldApplyEffects
        }
    }

    /**
     * When the screen is turned off, the used up screen time is increased by the time since the
     * last grayscale app was opened.
     * @see eventuallyIncreaseUsedUpScreenTime
     */
    override fun onScreenTurnedOff(context: Context?) {
        eventuallyIncreaseUsedUpScreenTime()
        // The lock screen is not the app the filter was meant for, and it is the first thing the
        // user sees on the way back. DetoxDroidAccessibilityService forgets the foreground package
        // at the same time, so the window event that arrives after unlocking re-evaluates even when
        // it comes from the app that was already open.
        context?.let { if (areEffectsActive) setEffectsActive(it, false) }
    }

    /**
     * Function to turn the feature's effects on or off.
     * The screen filter runs on any device. The system grayscale filter is a system-wide setting
     * and additionally needs the WRITE_SECURE_SETTINGS permission, which can be granted by running
     * `adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS`.
     * @param context The context.
     * @param active Whether the effects should be turned on.
     * @return Whether the operation was successful.
     */
    private fun setEffectsActive(
        context: Context, active: Boolean
    ): Boolean {
        ScreenFilterOverlay.apply(context, if (active) currentScreenFilterSpec(context) else null)
        areEffectsActive = active

        if (!writeSecureSettingsPermission.hasPermissions(context)) {
            // without the permission the daltonizer cannot be written at all (it would throw), so
            // the screen filter is the whole effect and there is nothing left to do here
            return true
        }

        // each effect only goes on when the user asked for it, but both always come back off — so
        // switching one off in the settings cannot leave it stuck until the next app switch
        val grayscale = active && systemGrayscale
        val dim = active && extraDim
        var success = true

        if (grayscale != isDaltonizerApplied) {
            val contentResolver = context.contentResolver
            if (grayscale) {
                // save the current color correction state (enabled + mode)
                // Only do this when currently not in grayscale, to not save grayscale as a default.
                defaultDaltonizerEnabled = getSecureInt(
                    contentResolver, DISPLAY_DALTONIZER_ENABLED, 0
                )
                defaultDaltonizer = getSecureInt(
                    contentResolver, DISPLAY_DALTONIZER, -1
                )
            }

            // enable/disable grayscale or restore previous state
            val enabledWritten = Settings.Secure.putInt(
                contentResolver,
                DISPLAY_DALTONIZER_ENABLED,
                if (grayscale) 1 else defaultDaltonizerEnabled
            )
            val modeWritten = Settings.Secure.putInt(
                contentResolver, DISPLAY_DALTONIZER, if (grayscale) 0 else defaultDaltonizer
            )
            if (enabledWritten && modeWritten) isDaltonizerApplied = grayscale else success = false
        }

        // no-op unless the extra dim state actually differs, so this is cheap to call every time
        if (!setExtraDim(context, dim)) success = false
        return success
    }

    /**
     * Drops whatever is currently applied. Called after a settings change, so an effect the user
     * just switched off disappears right away instead of surviving until the next app switch. The
     * next window event puts back whatever is still switched on.
     */
    fun refreshEffects(context: Context) {
        if (areEffectsActive || isDaltonizerApplied) setEffectsActive(context, false)
    }

    /**
     * Claims filters an earlier run left behind but did not record, so [restoreSystemFilters] can
     * hand them back. Two cases need it, and neither can be recognised from our own state.
     *
     * A build that kept the applied-state in memory only (and any process killed before it could
     * write) leaves the daltonizer switched on with nothing saying DetoxDroid owns it. Monochromacy
     * is what this feature writes and close to nothing else does, so while the feature is on we take
     * it as ours.
     *
     * Extra dim cannot be read back at all since Android 12, so on a fresh start there is no way to
     * ask whether it is on. Writing it off costs one setting write and is the difference between a
     * usable screen and one the user cannot fix from inside the app. The evaluation right after this
     * puts it back if it is supposed to be on.
     */
    private fun reclaimStrandedFilters(context: Context) {
        if (!writeSecureSettingsPermission.hasPermissions(context)) return
        val contentResolver = context.contentResolver
        if (!isDaltonizerApplied && getSecureInt(
                contentResolver, DISPLAY_DALTONIZER_ENABLED, 0
            ) == 1 && getSecureInt(contentResolver, DISPLAY_DALTONIZER, -1) == 0
        ) {
            isDaltonizerApplied = true
        }
        isCurrentlyExtraDim = true
    }

    /**
     * Puts the system's own display settings back the way the user had them, and forgets that we
     * ever touched them. Safe to call when nothing is applied and safe to call while DetoxDroid is
     * not running at all, which is the point:
     * [com.flx_apps.digitaldetox.workers.ServiceWatchdogWorker] calls it whenever it finds the
     * accessibility service switched off, so a device whose service was killed mid-filter does not
     * stay gray and dimmed with no way back from inside the app.
     */
    fun restoreSystemFilters(context: Context) {
        if (!writeSecureSettingsPermission.hasPermissions(context)) return
        val contentResolver = context.contentResolver
        if (isDaltonizerApplied) {
            Settings.Secure.putInt(
                contentResolver, DISPLAY_DALTONIZER_ENABLED, defaultDaltonizerEnabled
            )
            Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER, defaultDaltonizer)
            isDaltonizerApplied = false
        }
        if (isCurrentlyExtraDim) {
            Settings.Secure.putInt(contentResolver, EXTRA_DIM, 0)
            isCurrentlyExtraDim = false
        }
    }

    /**
     * Function to turn the extra dim filter on or off.
     * The extra dim filter is a system-wide setting. In order for it to work, the app needs to
     * have the WRITE_SECURE_SETTINGS permission. This can be granted by running
     * `adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS`.
     * @param context The context.
     * @param extraDim Whether the extra dim filter should be turned on.
     * @return Whether the operation was successful.
     */
    private fun setExtraDim(
        context: Context, extraDim: Boolean
    ): Boolean {
        if (isCurrentlyExtraDim == extraDim) return true
        val contentResolver = context.contentResolver
        val result = Settings.Secure.putInt(
            contentResolver, EXTRA_DIM, if (extraDim) 1 else 0
        )
        if (result) isCurrentlyExtraDim = extraDim
        return result
    }

    /**
     * Whether the [ScreenFilterOverlay] should be used, resolving [ScreenFilterMode.AUTO] against
     * the WRITE_SECURE_SETTINGS permission.
     */
    fun isScreenFilterEnabled(context: Context): Boolean = when (screenFilterMode) {
        ScreenFilterMode.ON -> true
        ScreenFilterMode.OFF -> false
        ScreenFilterMode.AUTO -> !writeSecureSettingsPermission.hasPermissions(context)
    }

    /**
     * The filter to apply right now, or null while the screen filter is switched off. One
     * intensity value drives both the wash and the blur, capped at
     * [ScreenFilterOverlay.MAX_WASH_ALPHA] so touches still reach the app below.
     */
    private fun currentScreenFilterSpec(context: Context): ScreenFilterSpec? {
        if (!isScreenFilterEnabled(context)) return null
        val intensity =
            screenFilterIntensity.coerceIn(MinScreenFilterIntensity, 100) / 100f
        val blur = screenFilterBlur && ScreenFilterOverlay.isBlurEffective(context)
        // blur alone on a screen that is not blurring would leave the filter switched on and doing
        // nothing at all, so the wash stands in. Battery saver is the common case: it switches
        // cross-window blurs off system-wide, on a device that supports them perfectly well.
        val shade = screenFilterShade || !blur
        return ScreenFilterSpec(
            washAlpha = if (shade) intensity * ScreenFilterOverlay.MAX_WASH_ALPHA else 0f,
            // linear, now that the ceiling is low enough for the top of the slider to still be
            // worth reaching. Legibility drops off within the first few pixels of radius, so the
            // whole usable range sits between roughly one and ten of them.
            blurDp = if (blur) intensity * MaxScreenFilterBlurDp else 0f
        )
    }

    // region NeedsPermissionsFeature

    /**
     * The feature works either way: with WRITE_SECURE_SETTINGS it drives the system grayscale
     * filter and extra dim, without it the screen filter. It can only be switched on while at
     * least one of those is actually going to do something.
     */
    override fun hasPermissions(context: Context): Boolean =
        isSystemFilterEnabled(context) || isScreenFilterEnabled(context)

    /**
     * Whether one of the two secure settings effects (grayscale, extra dim) can run right now.
     */
    private fun isSystemFilterEnabled(context: Context): Boolean =
        writeSecureSettingsPermission.hasPermissions(context) && (systemGrayscale || extraDim)

    /**
     * With the permission in hand, activation can only be blocked by every effect being switched
     * off, and there is nothing to grant in that case.
     */
    override fun activationBlockedMessage(context: Context): Int =
        if (writeSecureSettingsPermission.hasPermissions(context)) {
            R.string.feature_grayscale_noEffects
        } else {
            R.string.feature_grayscale_noEffects_noPermission
        }

    override fun activationBlockedHasAction(context: Context): Boolean =
        !writeSecureSettingsPermission.hasPermissions(context)

    /**
     * Only the system grayscale filter can be unlocked by a permission, so this always routes to
     * the WRITE_SECURE_SETTINGS instructions.
     */
    override fun requestPermissions(context: Context, navViewModel: NavViewModel) =
        writeSecureSettingsPermission.requestPermissions(context, navViewModel)

    // endregion

    /**
     * Reads a secure settings key, falling back to [default] if the key is not readable.
     * Since Android 12, hidden settings keys that are not explicitly marked as readable (such as
     * [EXTRA_DIM]) cannot be read by third-party apps and throw a [SecurityException] instead —
     * even if the app holds the WRITE_SECURE_SETTINGS permission. Writing them still works.
     */
    private fun getSecureInt(contentResolver: ContentResolver, key: String, default: Int): Int =
        try {
            Settings.Secure.getInt(contentResolver, key, default)
        } catch (e: SecurityException) {
            default
        }
}
