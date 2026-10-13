package com.flx_apps.digitaldetox.features

import android.os.Handler
import android.os.Looper
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.feature_types.ScreenTimeTrackingFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import timber.log.Timber

/**
 * Wakes a feature up when its daily budget is expected to run out in the middle of a session.
 * Features act on window changes, and none arrives while the user stays in one app, so without
 * this the limit would only take hold once they leave it.
 *
 * [onExhausted] runs on the main thread, and only while [tracking] still has a session running
 * (leaving the app or turning the screen off ends it) and DetoxDroid is active.
 */
class BudgetTimer(
    private val tracking: ScreenTimeTrackingFeature,
    private val onExhausted: () -> Unit,
) {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val check = Runnable {
        runCatching {
            if (tracking.trackingSinceTimestamp == 0L) return@Runnable
            if (DetoxDroidAccessibilityService.updateState() != DetoxDroidState.Active) {
                return@Runnable
            }
            onExhausted()
            // the widgets show the budget, and the launcher widget dims what it now blocks
            ScreenTimeSurfaces.invalidate(DetoxDroidApplication.appContext)
        }.onFailure { Timber.e(it, "Could not enforce a budget that ran out") }
    }

    /** Checks again once [allowedMs] are used up, if that is still ahead. */
    fun schedule(allowedMs: Long) {
        cancel()
        val remainingMs = allowedMs - tracking.currentUsedUpScreenTime()
        if (remainingMs > 0) mainHandler.postDelayed(check, remainingMs + PAST_END_MS)
    }

    fun cancel() = mainHandler.removeCallbacks(check)

    private companion object {
        /** Just past the expected end, so the check finds the budget spent, not a few ms left. */
        const val PAST_END_MS = 250L
    }
}
