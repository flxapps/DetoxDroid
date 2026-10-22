package com.flx_apps.digitaldetox.system_integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.rules.Rules

/**
 * A [BroadcastReceiver] that receives the [Intent.ACTION_SCREEN_OFF] event and forwards it to the
 * [FeaturesProvider.onScreenTurnedOffFeatures].
 */
class ScreenTurnedOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Features that drop what they are showing while the screen is off need a window event to
        // put it back, and no event arrives when the user unlocks straight into the app they left.
        // Forgetting the package turns that first event back into an app switch.
        DetoxDroidAccessibilityService.instance?.forgetForegroundPackage()
        // nothing is in front of a screen that is off, so the groups' budgets stop counting
        Rules.onNothingInFront()
        UsageStatsTracker.onScreenTurnedOff()
        // the widget on the lock screen should show the stretch of use that just ended
        context?.let { ScreenTimeSurfaces.refreshAfterScreenOff(it) }
        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onScreenTurnedOffFeatures)
            .forEach {
                (it as OnScreenTurnedOffSubscriptionFeature).onScreenTurnedOff(context)
            }
    }
}