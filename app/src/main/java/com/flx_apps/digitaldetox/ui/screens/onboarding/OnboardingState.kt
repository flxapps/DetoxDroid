package com.flx_apps.digitaldetox.ui.screens.onboarding

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.rules.RuleGroups

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
     * Whether the commitment password or a running brake currently protects the groups.
     * Onboarding writes a group, so re-running it is then off limits until the passphrase is
     * entered or the brake has ended, or it would be a way around them.
     */
    val isOnboardingLocked: Boolean
        get() = FeaturesProvider.areSettingsLocked(RuleGroups.LOCK_ID)

    /**
     * Whether the onboarding flow should be shown on app start. Users of previous versions that
     * already have groups or features configured are migrated to "completed" on first call.
     */
    fun shouldShowOnboarding(): Boolean {
        if (isOnboardingCompleted) return false
        if (RuleGroups.all.isNotEmpty() || FeaturesProvider.featureList.any { it.isActivated }) {
            isOnboardingCompleted = true
            return false
        }
        return true
    }
}
