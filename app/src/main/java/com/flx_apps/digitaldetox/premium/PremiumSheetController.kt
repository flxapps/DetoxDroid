package com.flx_apps.digitaldetox.premium

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Why the premium bottom sheet is being shown. The sheet's headline adapts to this.
 */
sealed interface PremiumSheetTrigger {
    /** Opened deliberately (e.g. the About entry). */
    data object Generic : PremiumSheetTrigger

    /** Surfaced automatically after sustained power use (see [PremiumSheetController.notifyPowerUse]). */
    data object PowerUseNudge : PremiumSheetTrigger

    /** Opened by tapping a locked premium control; [featureLabelRes] names what it unlocks. */
    data class LockedFeature(val featureLabelRes: Int) : PremiumSheetTrigger
}

/**
 * App-global controller for the premium bottom sheet ([com.flx_apps.digitaldetox.ui.screens.premium.PremiumSheetHost]).
 *
 * The sheet is the single premium surface: it is opened explicitly ([show]), by tapping a locked
 * premium control ([showForLockedFeature]), or automatically by a capped power-use nudge
 * ([notifyPowerUse]). Being a plain object (like [PremiumManager] / FeaturesProvider) lets any view
 * model or composable trigger it without wiring a shared view model.
 */
object PremiumSheetController {
    /** At most one nudge per this many days, and never after the user has brushed it off a few times. */
    private const val NUDGE_COOLDOWN_DAYS = 14
    private const val NUDGE_MAX_DISMISSALS = 3

    private var nudgeLastShownEpochDay: Long by DataStoreProperty(
        longPreferencesKey("premium_nudge_last_shown_day"), 0L
    )
    private var nudgeDismissCount: Int by DataStoreProperty(
        intPreferencesKey("premium_nudge_dismiss_count"), 0
    )

    private val _trigger = MutableStateFlow<PremiumSheetTrigger?>(null)

    /** Non-null while the sheet is visible; carries the reason it opened. */
    val trigger: StateFlow<PremiumSheetTrigger?> = _trigger.asStateFlow()

    /** Opens the sheet deliberately (About, etc.). Shown even when already unlocked (as a thank-you). */
    fun show() {
        _trigger.value = PremiumSheetTrigger.Generic
    }

    /** Opens the sheet because the user tapped a locked premium control. */
    fun showForLockedFeature(featureLabelRes: Int) {
        if (PremiumManager.isUnlocked) return
        _trigger.value = PremiumSheetTrigger.LockedFeature(featureLabelRes)
    }

    /**
     * Requests a power-use nudge. Silently does nothing if premium is already unlocked, the sheet is
     * already open, the user has dismissed nudges too often, or one was shown recently.
     */
    fun notifyPowerUse() {
        if (PremiumManager.isUnlocked) return
        if (_trigger.value != null) return
        if (nudgeDismissCount >= NUDGE_MAX_DISMISSALS) return
        val today = LocalDate.now().toEpochDay()
        if (nudgeLastShownEpochDay != 0L && today - nudgeLastShownEpochDay < NUDGE_COOLDOWN_DAYS) return
        nudgeLastShownEpochDay = today
        _trigger.value = PremiumSheetTrigger.PowerUseNudge
    }

    /** Dismissed without acting. A power-use nudge counts toward the "stop bothering me" budget. */
    fun onDismiss() {
        if (_trigger.value is PremiumSheetTrigger.PowerUseNudge) {
            nudgeDismissCount += 1
        }
        _trigger.value = null
    }

    /** Closes the sheet without recording a dismissal (e.g. right after unlocking). */
    fun hide() {
        _trigger.value = null
    }
}
