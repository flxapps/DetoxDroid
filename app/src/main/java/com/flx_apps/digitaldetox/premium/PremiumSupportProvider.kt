package com.flx_apps.digitaldetox.premium

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An external "support the developer" link (e.g. Ko-Fi, Liberapay). The URL is a string resource so
 * it can be reused from the existing About screen strings and localized if ever needed.
 */
data class SupportLink(
    val id: String,
    val labelRes: Int,
    val subtitleRes: Int,
    val urlRes: Int,
    val icon: ImageVector,
)

/**
 * Abstracts *how* a build lets the user support the project and unlock premium.
 *
 * There is exactly one implementation per distribution. The FOSS build's [PremiumSupport] (in
 * `src/main` today) opens external donation links and offers an honor-system unlock. When the
 * Google Play flavor is added, its own `PremiumSupport` — same fully-qualified name, living in
 * `src/googlePlay` — will back this with Play Billing instead, and this FOSS copy moves to
 * `src/foss`. Shared UI only ever talks to this interface, so neither flavor needs to know about
 * the other.
 *
 * See PREMIUM_TIER_PLAN.md §2–§4 for the rationale. [PremiumManager] holds the resulting
 * entitlement state.
 */
interface PremiumSupportProvider {
    /**
     * Whether this build can sell premium via an in-app purchase. `false` for FOSS (donations are
     * external), `true` for the Google Play flavor. UI shows a "buy" action only when true.
     */
    val supportsInAppPurchase: Boolean

    /**
     * Whether this build offers an honest "unlock without paying" action. `true` for FOSS (the lock
     * is a nudge, not DRM); will be `false` for Google Play, where an unpaid unlock would make the
     * IAP look like a donation.
     */
    val allowsFreeUnlock: Boolean

    /**
     * External support links to present (Ko-Fi, Liberapay, …). Will be empty for the Google Play
     * flavor, which may not steer users to outside payment methods.
     */
    val supportLinks: List<SupportLink>

    /**
     * Launches the in-app purchase flow. No-op in the FOSS flavor; the Google Play flavor will
     * override this with Play Billing. Kept here so shared UI can call it unconditionally.
     */
    fun launchInAppPurchase(context: Context) {}

    /**
     * Re-checks the entitlement against the source of truth. No-op for FOSS (the local flag is the
     * truth); the Google Play flavor will override this to query Play for existing purchases.
     */
    suspend fun refreshEntitlement() {}
}
