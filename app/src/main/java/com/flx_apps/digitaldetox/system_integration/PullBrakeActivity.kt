package com.flx_apps.digitaldetox.system_integration

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature

/**
 * Pulls the brake for the duration it was last pulled for, and shows nothing but a toast that
 * says until when. It is what the launcher shortcut and the home screen icon start, and it is
 * exported with an action of its own so an automation app can pull the brake as well:
 *
 *     am start -a com.flx_apps.digitaldetox.ACTION_PULL_BRAKE
 *
 * A brake that is already on stays as it is; what it says is the same either way.
 */
class PullBrakeActivity : Activity() {
    companion object {
        const val ACTION_PULL_BRAKE = "com.flx_apps.digitaldetox.ACTION_PULL_BRAKE"

        /** The id the static shortcut in `res/xml/shortcuts.xml` carries. */
        private const val SHORTCUT_ID = "pullBrake"

        /**
         * Asks the launcher to put the brake on the home screen as an icon of its own.
         * @return whether the launcher takes shortcuts from apps at all
         */
        fun requestHomeScreenIcon(context: Context): Boolean {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(context.getString(R.string.feature_brake_shortcut))
                .setLongLabel(context.getString(R.string.feature_brake_pull))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_brake))
                .setIntent(
                    Intent(context, PullBrakeActivity::class.java).setAction(ACTION_PULL_BRAKE)
                )
                .build()
            return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = when {
            DetoxDroidAccessibilityService.instance == null -> getString(R.string.feature_brake_status_notRunning)
            !EmergencyBrakeFeature.isActivated -> getString(R.string.feature_brake_status_notActivated)
            else -> {
                EmergencyBrakeFeature.pull(applicationContext)
                getString(R.string.home_state_braking, EmergencyBrakeFeature.endsAtText())
            }
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
