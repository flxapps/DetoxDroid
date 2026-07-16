package com.flx_apps.digitaldetox.premium

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolunteerActivism
import com.flx_apps.digitaldetox.R

/**
 * FOSS (GitHub / F-Droid) implementation of [PremiumSupportProvider].
 *
 * Premium is supported by external donations (Ko-Fi / Liberapay) and can also simply be unlocked
 * for free — the lock is a nudge, not DRM (see PREMIUM_TIER_PLAN.md §1). There is no in-app
 * purchase here.
 *
 * NOTE: when the Google Play flavor is added, this file moves to `src/foss/…/premium/` unchanged,
 * and a sibling `src/googlePlay/…/premium/PremiumSupport.kt` provides the Billing-backed variant.
 * Shared code must therefore keep referring to it as `PremiumSupport` only. Link URLs are shared
 * with the About screen ([R.string.about_coffee_link] / [R.string.about_patron_link]).
 */
object PremiumSupport : PremiumSupportProvider {
    override val supportsInAppPurchase = false
    override val allowsFreeUnlock = true

    override val supportLinks = listOf(
        SupportLink(
            id = "kofi",
            labelRes = R.string.about_coffee,
            subtitleRes = R.string.about_coffee_subtitle,
            urlRes = R.string.about_coffee_link,
            icon = Icons.Default.Favorite,
        ),
        SupportLink(
            id = "liberapay",
            labelRes = R.string.about_patron,
            subtitleRes = R.string.about_patron_subtitle,
            urlRes = R.string.about_patron_link,
            icon = Icons.Default.VolunteerActivism,
        ),
    )
}
