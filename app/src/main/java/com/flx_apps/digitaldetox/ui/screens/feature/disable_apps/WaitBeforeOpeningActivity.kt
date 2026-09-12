package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.widgets.DrainingBar
import com.flx_apps.digitaldetox.ui.widgets.InterventionPrimaryButton
import com.flx_apps.digitaldetox.ui.widgets.InterventionScreen
import com.flx_apps.digitaldetox.ui.widgets.InterventionSecondaryButton
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.appLabel

/**
 * Holds a listed app back for [DisableAppsFeature.waitBeforeOpening] before it can be opened.
 *
 * This is an activity rather than an [OverlayService] like the block screen, because the app has
 * to stop while the user waits: an overlay leaves it running underneath, so a feed would start
 * playing behind the countdown. An opaque activity in a task of its own sends the app to the
 * background instead, and finishing it returns to the app exactly as it was, even when it had been
 * opened from a notification or a link.
 *
 * Leaving it in any way, the screen going off included, abandons the wait, so the next open starts
 * a fresh one. So does a pause, which lets the app through.
 */
class WaitBeforeOpeningActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val EXTRA_WAIT_MS = "waitMs"
        private const val EXTRA_OPENS_AT = "opensAt"

        /** Holds back [packageName], which just came to the front, for [waitMs] milliseconds. */
        fun start(context: Context, packageName: String, waitMs: Long) {
            context.startActivity(Intent(context, WaitBeforeOpeningActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_WAIT_MS, waitMs)
                // a point in time rather than a duration, so a recreated activity carries on with
                // the countdown instead of starting it over
                putExtra(EXTRA_OPENS_AT, SystemClock.elapsedRealtime() + waitMs)
            })
        }
    }

    /** The app that is held back, and until when (in [SystemClock.elapsedRealtime] time). */
    private data class HeldBackApp(
        val packageName: String, val label: String, val waitMs: Long, val opensAtMs: Long
    )

    private var heldBackApp by mutableStateOf<HeldBackApp?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        heldBackApp = readHeldBackApp(intent)
        setContent {
            BackHandler(onBack = ::leave)
            val detoxDroidState by DetoxDroidAccessibilityService.state.collectAsState()
            LaunchedEffect(detoxDroidState) {
                // a pause (or DetoxDroid stopping) lets the app through right away
                if (detoxDroidState == DetoxDroidState.Inactive ||
                    PauseButtonFeature.isFeaturePaused(DisableAppsFeature)
                ) finish()
            }
            heldBackApp?.let { app ->
                // another app taking over (see onNewIntent) gets a countdown of its own
                key(app) {
                    WaitBeforeOpeningScreen(
                        appLabel = app.label,
                        waitMs = app.waitMs,
                        opensAtMs = app.opensAtMs,
                        onOpen = { open(app.packageName) },
                        onLeave = ::leave
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val app = readHeldBackApp(intent)
        // the same app announcing itself once more (e.g. its next window on the way up) keeps the
        // countdown it already has
        if (app.packageName == heldBackApp?.packageName) return
        setIntent(intent)
        heldBackApp = app
    }

    /**
     * Leaving abandons the wait. When the screen goes off, the phone goes home as well, so unlocking
     * it does not resume the app that was held back before the next wait catches it.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (!getSystemService(PowerManager::class.java).isInteractive) {
            startActivity(NavigationUtil.homeScreenIntent())
        }
        finish()
    }

    /** The user waited and still wants the app; finishing brings it back to the front. */
    private fun open(packageName: String) {
        DisableAppsFeature.skipWaitUntilScreenOff(packageName)
        finish()
    }

    private fun leave() {
        startActivity(NavigationUtil.homeScreenIntent())
        finish()
    }

    private fun readHeldBackApp(intent: Intent): HeldBackApp {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        return HeldBackApp(
            packageName = packageName,
            label = appLabel(packageName),
            waitMs = intent.getLongExtra(EXTRA_WAIT_MS, 0L),
            opensAtMs = intent.getLongExtra(EXTRA_OPENS_AT, 0L)
        )
    }
}

/**
 * Names the app that is held back and drains a bar over the wait. Leaving is always one tap away,
 * opening the app only once the bar is empty.
 */
@Composable
private fun WaitBeforeOpeningScreen(
    appLabel: String,
    waitMs: Long,
    opensAtMs: Long,
    onOpen: () -> Unit,
    onLeave: () -> Unit,
) {
    // taken once, so a recomposition cannot restart the bar, while a recreated activity continues it
    val remainingMs = remember { (opensAtMs - SystemClock.elapsedRealtime()).coerceIn(0L, waitMs) }
    var canOpen by remember { mutableStateOf(false) }
    InterventionScreen(
        icon = rememberVectorPainter(Icons.Default.HourglassTop),
        title = stringResource(id = R.string.feature_disableApps_wait_title),
        message = stringResource(id = R.string.feature_disableApps_wait_message, appLabel),
    ) {
        DrainingBar(
            durationMs = remainingMs,
            startFraction = if (waitMs > 0L) remainingMs.toFloat() / waitMs else 0f,
            onFinished = { canOpen = true }
        )
        InterventionPrimaryButton(
            text = stringResource(id = R.string.feature_disableApps_wait_leave),
            onClick = onLeave
        )
        InterventionSecondaryButton(
            text = stringResource(id = R.string.feature_disableApps_wait_open, appLabel),
            onClick = onOpen,
            enabled = canOpen
        )
    }
}

@Preview
@Composable
private fun WaitBeforeOpeningPreview() {
    WaitBeforeOpeningScreen(
        appLabel = "Instagram",
        waitMs = 10_000L,
        opensAtMs = SystemClock.elapsedRealtime() + 7_000L,
        onOpen = {},
        onLeave = {},
    )
}
