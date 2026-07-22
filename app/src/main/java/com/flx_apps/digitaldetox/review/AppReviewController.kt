package com.flx_apps.digitaldetox.review

import android.app.Activity
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import java.time.LocalDate

/**
 * Frequency-capped gate in front of the flavor-specific [AppReviewSupport] review dialog.
 *
 * Call sites mark *meaningful moments* — the user just unlocked premium, or is looking at usage
 * stats proving DetoxDroid has been stepping in for them — and this controller decides whether a
 * review request is appropriate at all. Google's quota already keeps the dialog rare, but the caps
 * here also keep us from burning that quota (and the user's patience) on every visit.
 *
 * Mirrors the shape of [com.flx_apps.digitaldetox.premium.PremiumSheetController]: a plain object
 * with [DataStoreProperty]-backed state, callable from anywhere without wiring.
 */
object AppReviewController {
    /** At most one request per this many days… */
    private const val COOLDOWN_DAYS = 120

    /** …and only this many across the app's lifetime. */
    private const val MAX_LIFETIME_REQUESTS = 3

    private var lastRequestEpochDay: Long by DataStoreProperty(
        longPreferencesKey("review_request_last_day"), 0L
    )
    private var requestCount: Int by DataStoreProperty(
        intPreferencesKey("review_request_count"), 0
    )

    /**
     * Requests the in-app review dialog if this build supports it and the caps allow it. Silently
     * does nothing otherwise. Note that even when we do request, the store may decide not to show
     * anything — by design, callers get no feedback.
     */
    fun maybeAskForReview(activity: Activity) {
        if (!AppReviewSupport.isAvailable) return
        if (requestCount >= MAX_LIFETIME_REQUESTS) return
        val today = LocalDate.now().toEpochDay()
        if (lastRequestEpochDay != 0L && today - lastRequestEpochDay < COOLDOWN_DAYS) return
        lastRequestEpochDay = today
        requestCount += 1
        AppReviewSupport.launchReviewFlow(activity)
    }
}
