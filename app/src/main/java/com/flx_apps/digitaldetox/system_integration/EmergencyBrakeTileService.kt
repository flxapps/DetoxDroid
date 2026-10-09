package com.flx_apps.digitaldetox.system_integration

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature

/**
 * The quick settings tile that pulls the brake, for the duration it was last pulled for. A second
 * tap takes it back while that is still possible, and does nothing afterwards: ending a brake
 * takes a wait, and that is asked for in the app.
 */
class EmergencyBrakeTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (DetoxDroidAccessibilityService.instance == null || !EmergencyBrakeFeature.isActivated) {
            return
        }
        when {
            !EmergencyBrakeFeature.isBraking() -> EmergencyBrakeFeature.pull(this)
            EmergencyBrakeFeature.canUndo() -> EmergencyBrakeFeature.undo(this)
            else -> Toast.makeText(
                this,
                getString(R.string.home_state_braking, EmergencyBrakeFeature.endsAtText()),
                Toast.LENGTH_SHORT
            ).show()
        }
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        // qsTile is null outside the listening window
        val tile = qsTile ?: return
        val braking = EmergencyBrakeFeature.isBraking()
        tile.state = when {
            DetoxDroidAccessibilityService.updateState() == DetoxDroidState.Inactive -> Tile.STATE_UNAVAILABLE
            !EmergencyBrakeFeature.isActivated -> Tile.STATE_UNAVAILABLE
            braking -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (braking) {
                getString(R.string.app_brakeTile_until, EmergencyBrakeFeature.endsAtText())
            } else null
        }
        tile.updateTile()
    }
}
