package com.flx_apps.digitaldetox.features

import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.OneMinuteInMs
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState

/**
 * A helper class that provides access to the features of the app. It provides methods to get a
 * feature by its id and to get features by specific properties (e.g. all currently active features,
 * all features that react to scroll events, etc.) to reduce the number of filter operations.
 *
 * It is a singleton object that is held in memory as long as the app is running, as it is used
 * everywhere in the app.
 */
object FeaturesProvider {
    /**
     * A list of all features of the app. This list is used to display the features in the UI and to
     * iterate over them when an app event occurs. The order of the features in this list is the
     * order in which they are displayed in the UI.
     */
    val featureList = listOf(
        GrayscaleAppsFeature,
        DoNotDisturbFeature,
        BreakDoomScrollingFeature,
        DisableAppsFeature,
        PauseButtonFeature
    )

    /**
     * A map of all features of the app. This map is used to get a feature by its id.
     */
    private val featureMap = featureList.associateBy { it.id }

    /**
     * The last time the active features have been reloaded. This is used to reduce the number of
     * calls to [Feature.isActive] for performance reasons.
     */
    private var lastActiveFeaturesReload = 0L

    /**
     * A list of all currently active features. This list is reloaded only every minute to reduce the
     * number of calls to [Feature.isActive] for performance reasons.
     * @see Feature.isActive
     * @see lastActiveFeaturesReload
     */
    var activeFeatures = mutableSetOf<Feature>()
        get() {
            // reload active features every minute to reduce the number of calls to isActive()
            // for performance reasons
            if (System.currentTimeMillis() - lastActiveFeaturesReload > OneMinuteInMs) {
                val newActiveFeatures = featureList.filter { it.isActive() }.toMutableSet()
                if (DetoxDroidAccessibilityService.updateState() != DetoxDroidState.Inactive) {
                    field.forEach { feature ->
                        if (!newActiveFeatures.contains(feature)) {
                            feature.onPause(DetoxDroidApplication.appContext)
                        }
                    }
                }
                field = featureList.filter { it.isActive() }.toMutableSet()
                lastActiveFeaturesReload = System.currentTimeMillis()
            }
            return field
        }

    /**
     * Forces a reload of the active features. This is useful if the active state of a feature is
     * known to have changed (e.g. when the user has changed the schedule), but the active features
     * have not been reloaded yet.
     */
    fun reloadActiveFeatures() {
        lastActiveFeaturesReload = 0L
        activeFeatures
    }

    /**
     * Starts or stops the given feature. This is used when the user toggles the active state of a
     * feature in the UI.
     * @param feature The feature to start or stop.
     * @see Feature.isActive
     * @see Feature.onStart
     * @see Feature.onPause
     */
    fun startOrStopFeature(feature: Feature) {
        reloadActiveFeatures()
        if (DetoxDroidAccessibilityService.updateState() == DetoxDroidState.Active) {
            // if DetoxDroid is running, call onStart() or onPause() for the feature
            if (feature.isActive()) feature.onStart(DetoxDroidApplication.appContext) else feature.onPause(
                DetoxDroidApplication.appContext
            )
        }
    }

    /**
     * Returns all features that implement the [OnScrollEventSubscriptionFeature] interface.
     */
    val onScrollEventFeatures =
        featureList.filterIsInstance<OnScrollEventSubscriptionFeature>().toSet()

    /**
     * Returns all features that implement the [OnAppOpenedSubscriptionFeature] interface.
     */
    val onAppOpenedFeatures = featureList.filterIsInstance<OnAppOpenedSubscriptionFeature>().toSet()

    /**
     * Returns all features that implement the [OnScreenTurnedOffSubscriptionFeature] interface.
     */
    val onScreenTurnedOffFeatures =
        featureList.filterIsInstance<OnScreenTurnedOffSubscriptionFeature>().toSet()

    /**
     * Returns the feature with the given id or null if no feature with this id exists.
     * @see Feature.id
     */
    fun getFeatureById(id: String): Feature? {
        return featureMap[id]
    }
}

