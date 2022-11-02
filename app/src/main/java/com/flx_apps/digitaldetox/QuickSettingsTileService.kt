package com.flx_apps.digitaldetox

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.flx_apps.digitaldetox.prefs.Prefs_


/**
 * Creation Date: 11/17/20
 * @author felix
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {
    override fun onClick() {
        super.onClick()

        val isPausing = DetoxUtil.togglePause(baseContext)
        log("isPausing=$isPausing")
        setInternalTileState(if (isPausing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
    }

    override fun onStartListening() {
        super.onStartListening()

        val isPausing = Prefs_(applicationContext).pauseUntil().get() >= System.currentTimeMillis()
        setInternalTileState(if (isPausing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
    }

    private fun setInternalTileState(state: Int) {
        val tile = qsTile
        tile.state = state
        tile.updateTile()
    }
}