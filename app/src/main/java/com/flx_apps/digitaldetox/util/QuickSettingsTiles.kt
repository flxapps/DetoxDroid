package com.flx_apps.digitaldetox.util

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes

/**
 * Asks the system to add [tile] to the Quick Settings. The system shows its own dialog; only a
 * tile that is already there gets no answer, so that case is confirmed here with [alreadyAddedRes].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun offerQuickSettingsTile(
    context: Context,
    tile: Class<out TileService>,
    @StringRes labelRes: Int,
    @DrawableRes iconRes: Int,
    @StringRes alreadyAddedRes: Int,
) {
    val appContext = context.applicationContext
    appContext.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(appContext, tile),
        appContext.getString(labelRes),
        Icon.createWithResource(appContext, iconRes),
        appContext.mainExecutor
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
            Toast.makeText(appContext, alreadyAddedRes, Toast.LENGTH_SHORT).show()
        }
    }
}
