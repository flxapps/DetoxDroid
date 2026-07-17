package com.flx_apps.digitaldetox.premium

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for whether premium is unlocked on this device.
 *
 * The entitlement is intentionally flavor-agnostic: *how* it gets unlocked differs per build (an
 * honor-system tap or "I've donated" in FOSS, a restored purchase in Google Play — see
 * [PremiumSupportProvider]), but the resulting flag and the way the UI observes it are shared.
 *
 * Persistence mirrors the rest of the app ([DataStoreProperty]); because that delegate reads once
 * and does not emit changes, we keep an in-memory [MutableStateFlow] in sync for Compose.
 */
object PremiumManager {
    private var persistedUnlocked: Boolean by DataStoreProperty(
        booleanPreferencesKey("premium_unlocked"), false
    )

    private val _isPremiumUnlocked = MutableStateFlow(persistedUnlocked)

    /** Observable unlock state for the UI. */
    val isPremiumUnlocked: StateFlow<Boolean> = _isPremiumUnlocked.asStateFlow()

    /** Synchronous read for non-Compose call sites (e.g. future feature gating). */
    val isUnlocked: Boolean get() = _isPremiumUnlocked.value

    /**
     * Marks premium as unlocked. Called by the honor-system / "I've donated" actions in FOSS and,
     * in the Google Play flavor, once a purchase is confirmed or restored.
     */
    fun unlock() = setUnlocked(true)

    /** Re-locks premium (e.g. a user-initiated reset). */
    fun relock() = setUnlocked(false)

    private fun setUnlocked(value: Boolean) {
        if (_isPremiumUnlocked.value == value) return
        persistedUnlocked = value
        _isPremiumUnlocked.value = value
    }
}
