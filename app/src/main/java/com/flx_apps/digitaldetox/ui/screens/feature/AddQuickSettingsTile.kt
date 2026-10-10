package com.flx_apps.digitaldetox.ui.screens.feature

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * Offers one of DetoxDroid's tiles to the Quick Settings, so nobody has to find it in the tile
 * editor. Adding a tile changes no setting, so it stays tappable while the settings are locked.
 * @see com.flx_apps.digitaldetox.util.offerQuickSettingsTile
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AddQuickSettingsTile(@StringRes subtitleRes: Int, onClick: () -> Unit) {
    SimpleListTile(
        leadingIcon = Icons.Default.GridView,
        titleText = stringResource(id = R.string.feature_pause_fromQuickSettings),
        subtitleText = stringResource(id = subtitleRes),
        allowClickWhenLocked = true,
        onClick = onClick
    )
}
