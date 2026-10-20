package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.widgets.InterventionPrimaryButton
import com.flx_apps.digitaldetox.ui.widgets.InterventionScreen

class AppDisabledOverlayService : OverlayService(OverlayContent { AppDisabledOverlay() })

/**
 * The overlay that is displayed when the user tries to open a disabled app.
 * When closed, the user will be redirected to the home screen.
 */
@Preview
@Composable
fun AppDisabledOverlay() {
    val context = LocalContext.current
    InterventionScreen(
        icon = painterResource(id = R.drawable.ic_disable_app),
        title = stringResource(id = R.string.feature_disableApps_overlay_title),
        message = stringResource(id = R.string.feature_disableApps_overlay_message) + " " +
                stringResource(id = R.string.feature_disableApps_overlay_message2),
    ) {
        InterventionPrimaryButton(
            text = stringResource(id = R.string.action_close),
            onClick = { (context as OverlayService).closeOverlay() }
        )
    }
}
