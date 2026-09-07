package com.flx_apps.digitaldetox.system_integration

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A [BroadcastReceiver] that puts the features back to work when the user comes back to the phone.
 *
 * Features tear their effects down while the screen is off (see [ScreenTurnedOffReceiver]) and are
 * otherwise woken by window events, which a screen that turns straight back into the app it was
 * showing does not have to produce.
 */
class ScreenTurnedOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val service = DetoxDroidAccessibilityService.instance ?: return
        // Where there is a lock screen, [Intent.ACTION_SCREEN_ON] arrives while it is still up and
        // [Intent.ACTION_USER_PRESENT] follows once it is gone; a phone without one only sends the
        // former. So the screen turning on counts only while nothing is locked, and the lock screen
        // itself is left undressed.
        if (intent?.action == Intent.ACTION_SCREEN_ON) {
            val keyguardManager =
                context?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isKeyguardLocked != false) return
        }
        service.reevaluateForegroundApp()
    }
}
