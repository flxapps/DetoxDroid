package com.flx_apps.digitaldetox.feature_types

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import java.time.LocalDateTime

/**
 * The texts that are displayed for each feature.
 */
data class FeatureTexts(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val description: Int,
)

/**
 * The feature id right now corresponds to the simple name of the feature class. We could also
 * use a UUID or something like that in the future, hence the typealias.
 * @see Feature.createId
 */
typealias FeatureId = String

/**
 * A feature is a module of the app that can be turned on/off by the user.
 * Each feature has a title, a description, a subtitle.
 * It can react to app events and scroll events.
 * It also contains helper methods that provides simple access to the data store.
 *
 * This is an abstract class, because each feature has to implement the methods differently.
 */
abstract class Feature {
    companion object {
        /**
         * Creates a feature id from the given feature class.
         * @see FeatureId
         */
        fun createId(featureClass: Class<out Feature>): FeatureId {
            return featureClass.simpleName
        }
    }

    /**
     * The unique id of the feature.
     */
    val id: FeatureId = createId(this::class.java)

    /**
     * The title, description, subtitle of the feature.
     */
    abstract val texts: FeatureTexts

    /**
     * The icon that is displayed for the feature.
     */
    abstract val iconRes: Int

    /**
     * A composable function that displays additional settings for the feature.
     * Typically, you would implement this in the [com.flx_apps.digitaldetox.ui.screens.feature].{feature_name} package.
     *
     * TODO move texts, iconRes, settingsContent to a separate class "FeatureUiInfo" or something like that
     */
    abstract val settingsContent: @Composable () -> Unit

    /**
     * Whether the feature is currently active.
     */
    open var isActivated: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_isActivated"), false
    )

    /**
     * Called for each [Feature] when DetoxDroid is started or when the feature is activated.
     */
    open fun onStart(context: Context) {}

    /**
     * Called for each [Feature] when DetoxDroid is paused (temporarily) or stopped, or when the
     * feature is deactivated.
     */
    open fun onPause(context: Context) {}

    /**
     * Returns whether the feature is currently active (i.e. whether it is activated and whether
     * there is a schedule rule that is active).
     */
    fun isActive(atDateTime: LocalDateTime = LocalDateTime.now()): Boolean {
        return isActivated && (this !is SupportsScheduleFeature || isScheduled(atDateTime))
    }
}

