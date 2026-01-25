package com.flx_apps.digitaldetox.features

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
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
object PauseButtonFeature : Feature() {
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
     * Holds the time until the pause is over.
     */
    private var pauseUntil = 0L

    override fun onPause(context: Context) {
        pauseUntil = System.currentTimeMillis() + pauseDuration
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
        if (System.currentTimeMillis() - pauseUntil < timeBetweenPausesDuration) {
            val timeUntilNextPauseInMinutes =
                TimeUnit.MILLISECONDS.toMinutes(timeBetweenPausesDuration - (System.currentTimeMillis() - pauseUntil))
            // ignore pause
            Toast.makeText(context, R.string.app_quickSettingsTile_noPause, Toast.LENGTH_SHORT)
                .show()
            return
        }
        pauseFeatures(context)
    }

    fun pauseFeatures(context: Context, stop: Boolean = false) {
        if (!isActivated && !stop) return
        FeaturesProvider.featureList.forEach {
            if ((stop && it == this) || !it.isActive()) return@forEach // call onPause() only for active features
            it.onPause(context)
        }
        DetoxDroidAccessibilityService.updateState()
    }

    fun resume() {
        pauseUntil = 0
        DetoxDroidAccessibilityService.instance.takeIf { it != null }?.let { service ->
            // call onStart() for all active features if we have an instance of the service
            FeaturesProvider.activeFeatures.onEach { it.onStart(service) }
            // Update the foreground notification to reflect resumed state
            service.updateForegroundNotification()
        }
        DetoxDroidAccessibilityService.updateState()
    }

    fun isPausing(): Boolean {
        return System.currentTimeMillis() < pauseUntil
    }
}