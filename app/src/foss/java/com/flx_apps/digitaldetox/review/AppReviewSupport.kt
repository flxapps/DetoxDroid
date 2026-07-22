package com.flx_apps.digitaldetox.review

import android.app.Activity

/**
 * FOSS (GitHub / F-Droid) implementation of the store-review seam: there is no store to review the
 * app on (and F-Droid has no rating API), so everything here is a no-op and review UI stays hidden.
 *
 * The sibling `src/googlePlay/…/review/AppReviewSupport.kt` (private overlay, gitignored) backs
 * this with the Play In-App Review API under the same fully-qualified name — shared code must keep
 * referring to it as `AppReviewSupport` only. [AppReviewController] adds the shared frequency caps.
 */
object AppReviewSupport {
    /** Whether this build can ask for a store review at all. Gates all review UI. */
    const val isAvailable: Boolean = false

    /** Shows the store's in-app review dialog if the store decides to. No-op here. */
    fun launchReviewFlow(activity: Activity) {}

    /** Opens the app's store listing (explicit "rate this app" taps). No-op here. */
    fun openStoreListing(activity: Activity) {}
}
