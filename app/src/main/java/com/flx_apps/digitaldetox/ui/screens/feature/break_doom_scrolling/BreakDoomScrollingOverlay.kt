package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import android.content.Intent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import com.flx_apps.digitaldetox.util.ForceStopUtil
import androidx.compose.ui.platform.LocalContext

/**
 * The service that shows the warning screen when the user is caught "doomscrolling". It is an
 * [OverlayService] that shows the [BreakDoomScrollingOverlay].
 *
 * The service is started by [BreakDoomScrollingFeature.onScrollEvent], when certain conditions
 * are met.
 */
class BreakDoomScrollingOverlayService :
    OverlayService(OverlayContent { BreakDoomScrollingOverlay() }) {
    companion object {
        const val EXTRA_OFFER_SNOOZE: String = "offerSnooze"
    }

    /**
     * Whether this break screen offers a snooze ("give me a bit more time") action. Only the
     * initial warning of a doom-scrolling incident does; re-shows while the app is still
     * blocked do not.
     */
    var offersSnooze: Boolean = false
        private set

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        offersSnooze = intent?.getBooleanExtra(EXTRA_OFFER_SNOOZE, false) ?: false
        return super.onStartCommand(intent, flags, startId)
    }
}

/**
 * The warning screen UI that is shown when the user is caught "doomscrolling". It provides a
 * brief "warning message" and a button to go to the home screen.
 */
@Preview
@Composable
fun BreakDoomScrollingOverlay() {
    val context = LocalContext.current
    DetoxDroidTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(2f))
            Text(
                text = stringResource(id = R.string.infiniteScroll_warning_title),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Text(
                text = stringResource(id = R.string.infiniteScroll_warning_message),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(vertical = 32.dp)
            )
            OutlinedButton(modifier = Modifier
                .padding(top = 16.dp)
                .scale(1.5f), onClick = {
                val service = context as OverlayService
                service.closeOverlay()
                // best-effort: kill the doom-scrolling app so it also disappears from recents
                // (works when Shizuku is set up; otherwise degrades gracefully, never crashes)
                ForceStopUtil.tryForceStop(service, service.runningAppPackageName)
            }) {
                Text(text = stringResource(id = R.string.infiniteScroll_warning_exit))
            }
            val overlayService = context as? BreakDoomScrollingOverlayService
            if (overlayService?.offersSnooze == true) {
                TextButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        BreakDoomScrollingFeature.snoozeApp(overlayService.runningAppPackageName)
                        overlayService.dismissOverlay()
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.infiniteScroll_warning_snooze),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground_cropped),
                contentDescription = "Logo",
                modifier = Modifier.size(196.dp)
            )
        }
    }
}