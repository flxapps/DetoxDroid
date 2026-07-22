package com.flx_apps.digitaldetox.ui.screens.onboarding

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController

/**
 * Persisted onboarding state, kept outside the view model so that navigation gating and the home
 * screen can read it cheaply.
 */
object OnboardingState {
    /**
     * Whether the user has been through (or skipped) the onboarding flow.
     */
    var isOnboardingCompleted: Boolean by DataStoreProperty(
        booleanPreferencesKey("onboarding_completed"), false
    )

    /**
     * Features that onboarding configured but could not activate yet because a required permission
     * was missing (e.g. grayscale without WRITE_SECURE_SETTINGS, or scroll breaks without the
     * overlay permission). The home screen offers to finish the setup and activates each feature
     * as soon as its permission shows up.
     * @see resolvePendingFeatureActivations
     */
    var pendingFeatureActivations: Set<FeatureId> by DataStoreProperty(
        stringSetPreferencesKey("onboarding_pendingFeatureActivations"), emptySet()
    )

    /** Whether the pending grayscale activation still waits for its (WRITE_SECURE_SETTINGS) grant. */
    val isGrayscaleActivationPending: Boolean
        get() = GrayscaleAppsFeature.id in pendingFeatureActivations

    /**
     * Whether the onboarding flow should be shown on app start. Users of previous versions that
     * already have features configured are migrated to "completed" on first call.
     */
    fun shouldShowOnboarding(): Boolean {
        if (isOnboardingCompleted) return false
        if (FeaturesProvider.featureList.any { it.isActivated }) {
            isOnboardingCompleted = true
            return false
        }
        return true
    }

    /**
     * Activates every pending feature whose required permission is now present — no matter how it
     * arrived (Shizuku wizard, root, adb or the system settings). Restarts DetoxDroid so it picks
     * up the freshly activated features. Safe to call repeatedly from any screen that can observe
     * a permission showing up.
     * @return true while at least one feature is still waiting for a permission
     */
    fun resolvePendingFeatureActivations(): Boolean {
        val pending = pendingFeatureActivations
        if (pending.isEmpty()) return false
        val context = DetoxDroidApplication.appContext
        val stillPending = mutableSetOf<FeatureId>()
        var activatedAny = false
        pending.forEach { featureId ->
            val feature = FeaturesProvider.getFeatureById(featureId) ?: return@forEach
            val hasPermission = feature !is NeedsPermissionsFeature || feature.hasPermissions(context)
            if (hasPermission) {
                feature.isActivated = true
                FeaturesProvider.startOrStopFeature(feature)
                activatedAny = true
            } else {
                stillPending += featureId
            }
        }
        pendingFeatureActivations = stillPending
        // (re)start DetoxDroid so the newly activated features actually take effect
        if (activatedAny) runCatching { AccessibilityServiceController.activate(context) }
        return stillPending.isNotEmpty()
    }
}
