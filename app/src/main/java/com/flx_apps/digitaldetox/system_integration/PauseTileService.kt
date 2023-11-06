package com.flx_apps.digitaldetox.system_integration

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flx_apps.digitaldetox.features.PauseButtonFeature

/**
 * The [PauseTileService] is the tile that is shown in the quick settings. It allows the user to
 * toggle the pause state of the [DetoxDroidAccessibilityService].
 */
class PauseTileService : TileService() {
    /**
     * Called when the user clicks the tile.
     * Will toggle the pause state of the [DetoxDroidAccessibilityService].
     */
    override fun onClick() {
        super.onClick()

        if (DetoxDroidAccessibilityService.instance == null) {
            return
        }

        PauseButtonFeature.togglePause(this)
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    /**
     * Updates the tile to the current state of the [DetoxDroidAccessibilityService].
     * @see DetoxDroidAccessibilityService
     * @see DetoxDroidState
     */
    private fun updateTile() {
        val state = when (DetoxDroidAccessibilityService.state.value) {
            DetoxDroidState.Inactive -> Tile.STATE_UNAVAILABLE
            DetoxDroidState.Paused -> Tile.STATE_ACTIVE
            DetoxDroidState.Active -> Tile.STATE_INACTIVE
        }

        val tile = qsTile
        tile.state = state
        tile.updateTile()
    }
}