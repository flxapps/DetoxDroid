package com.flx_apps.digitaldetox.features

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.OneMinuteInMs
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.features.PauseButtonFeature.hardwareKey
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.PauseInteractionService
import com.flx_apps.digitaldetox.system_integration.PauseTileService
import com.flx_apps.digitaldetox.ui.screens.feature.pause_button.PauseButtonFeatureSettingsSection
import java.util.concurrent.TimeUnit

/**
 * The [PauseButtonFeature] can be used to pause DetoxDroid for a defined amount of time. It will
 * call [Feature.onPause] for all active features and DetoxDroid will ignore all accessibility
 * events until the pause is over.
 *
 * The user can pause DetoxDroid:
 *
 * - by long-pressing the hardware key defined in [hardwareKey] for 2 seconds (see
 * [DetoxDroidAccessibilityService.onKeyEvent],
 *
 * - by clicking the pause button in the quick settings tile (see [PauseTileService]), or
 *
 * - by setting DetoxDroid as default assistant app (see [PauseInteractionService]).
 */
object PauseButtonFeature : Feature(), LockableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_pause,
        subtitle = R.string.feature_pause_subtitle,
        description = R.string.feature_pause_description,
    )
    override val iconRes: Int = R.drawable.ic_pause
    override val settingsContent: @Composable () -> Unit = {
        PauseButtonFeatureSettingsSection()
    }

    /**
     * The duration of the pause in milliseconds.
     */
    var pauseDuration: Long by DataStoreProperty(
        longPreferencesKey("${id}_pauseDuration"), TimeUnit.MINUTES.toMillis(3)
    )

    /**
     * The minimum time that has to pass between two pauses in milliseconds.
     * If the user pauses the app before this time has passed, the pause will be ignored.
     * This is useful to prevent the user from pausing the app and immediately unpausing it again
     * (and thereby tricking themselves).
     */
    var timeBetweenPausesDuration: Long by DataStoreProperty(
        longPreferencesKey("${id}_timeBetweenPausesDuration"), TimeUnit.MINUTES.toMillis(0)
    )

    /**
     * The hardware key that can be used to pause DetoxDroid by long-pressing it for 2 seconds.
     */
    var hardwareKey: Int by DataStoreProperty(
        intPreferencesKey("${id}_hardwareKey"), KeyEvent.KEYCODE_UNKNOWN
    )

    /**
     * The ids of the [PausableFeature]s that a pause should *not* affect (i.e. features that keep
     * running through a pause). Stored as an exclusion set so the default — an empty set — means "a
     * pause affects every pausable feature", and features added in future are pausable by default.
     * @see pausableFeatures
     * @see isFeaturePaused
     */
    var pauseExemptFeatureIds: Set<FeatureId> by DataStoreProperty(
        stringSetPreferencesKey("${id}_exemptFeatureIds"), setOf()
    )

    /** All features a pause can suspend, i.e. every [PausableFeature], in UI order. */
    val pausableFeatures: List<Feature>
        get() = FeaturesProvider.featureList.filter { it is PausableFeature }

    /**
     * Whether [feature] is currently suspended by an active pause — true only while a pause is
     * running, for pausable features the user has not exempted. Consulted by the accessibility
     * service before dispatching events to a feature.
     */
    fun isFeaturePaused(feature: Feature): Boolean =
        isPausing() && feature is PausableFeature && feature.id !in pauseExemptFeatureIds

    /**
     * Holds the time until the pause is over. Also read by the UI to display the actual end time
     * of a running pause.
     */
    var pauseUntil = 0L
        private set

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Fires when the pause expires on its own, so features restart and the notification/tile stop
     * claiming a pause is still running (previously the stale "Resume" action would silently start
     * a new pause).
     */
    private val autoResumeRunnable = Runnable {
        kotlin.runCatching { if (!isPausing()) resume() }
    }

    /**
     * Called when the feature itself is deactivated or paused as a feature (NOT when the user
     * presses the pause button — that is [beginPause]). A running pause must not survive the
     * feature being switched off, otherwise DetoxDroid would stay paused with no way to resume.
     */
    override fun onPause(context: Context) {
        if (isPausing()) resume() else mainHandler.removeCallbacks(autoResumeRunnable)
    }

    /**
     * Starts a user-initiated pause: all active features are suspended until [pauseUntil].
     */
    private fun beginPause(context: Context) {
        pauseUntil = System.currentTimeMillis() + pauseDuration
        mainHandler.removeCallbacks(autoResumeRunnable)
        mainHandler.postDelayed(autoResumeRunnable, pauseDuration + 250)
        Toast.makeText(
            context, context.getString(
                R.string.app_quickSettingsTile_paused,
                TimeUnit.MILLISECONDS.toMinutes(pauseDuration)
            ), Toast.LENGTH_SHORT
        ).show()

        // Update the foreground notification to reflect paused state
        DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
    }

    fun togglePause(context: Context) {
        if (!isActivated) return
        if (isPausing()) {
            resume()
            return
        }
        val timeSincePauseEnd = System.currentTimeMillis() - pauseUntil
        if (pauseUntil > 0 && timeSincePauseEnd < timeBetweenPausesDuration) {
            // ignore pause and tell the user how long they have to wait (rounded up)
            val minutesUntilNextPause =
                (timeBetweenPausesDuration - timeSincePauseEnd + OneMinuteInMs - 1) / OneMinuteInMs
            Toast.makeText(
                context,
                context.getString(R.string.app_quickSettingsTile_noPause, minutesUntilNextPause),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        pauseFeatures(context)
    }

    fun pauseFeatures(context: Context, stop: Boolean = false) {
        if (!isActivated && !stop) return
        if (!stop) beginPause(context) // a user pause; a stop just suspends the features
        FeaturesProvider.featureList.forEach {
            // call onPause() only for active features; this feature manages itself via beginPause
            if (it == this || !it.isActive()) return@forEach
            // a user pause leaves exempt features running; a full stop suspends everything
            if (!stop && it.id in pauseExemptFeatureIds) return@forEach
            it.onPause(context)
        }
        DetoxDroidAccessibilityService.updateState()
    }

    fun resume() {
        // keep the actual end of the pause so the "minimum time between pauses" cooldown starts
        // from the resume instead of being skipped entirely
        pauseUntil = pauseUntil.coerceAtMost(System.currentTimeMillis())
        mainHandler.removeCallbacks(autoResumeRunnable)
        DetoxDroidAccessibilityService.instance?.let { service ->
            // restart the features the pause suspended; exempt features were never paused
            FeaturesProvider.activeFeatures.forEach {
                if (it.id in pauseExemptFeatureIds) return@forEach
                it.onStart(service)
            }
            // Update the foreground notification to reflect resumed state
            service.updateForegroundNotification()
        }
        DetoxDroidAccessibilityService.updateState()
    }

    fun isPausing(): Boolean {
        return System.currentTimeMillis() < pauseUntil
    }
}