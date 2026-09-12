package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.widgets.DrainingBar
import com.flx_apps.digitaldetox.ui.widgets.InterventionPrimaryButton
import com.flx_apps.digitaldetox.ui.widgets.InterventionScreen
import com.flx_apps.digitaldetox.ui.widgets.InterventionSecondaryButton
import com.flx_apps.digitaldetox.util.ForceStopUtil

/**
 * The variants of the doom-scrolling break screen.
 * @see BreakDoomScrollingOverlay
 */
enum class BreakScreenMode {
    /** A doom-scrolling trigger fired: offer to leave now or to finish the current item first. */
    WARNING,

    /** The user ran into an active cooldown: tell them how long the app/surface stays locked. */
    COOLDOWN,

    /** The finish-grace is over: announce the exit and go to the home screen automatically. */
    GUIDE_OUT,
}

/**
 * The service that shows the break screen when the user is caught "doomscrolling". It is an
 * [OverlayService] that shows the [BreakDoomScrollingOverlay].
 *
 * The service is started by [BreakDoomScrollingFeature], when certain conditions are met.
 */
class BreakDoomScrollingOverlayService :
    OverlayService(OverlayContent { BreakDoomScrollingOverlay() }) {
    companion object {
        const val EXTRA_MODE: String = "breakScreenMode"
        const val EXTRA_CONTEXT_TEXT: String = "contextText"

        /** How long the guide-out screen stays before it takes the user to the home screen. */
        const val GUIDE_OUT_AUTO_EXIT_MS = 3_500L
    }

    /**
     * Which break-screen variant to show. Backed by Compose state so a re-delivered intent
     * updates an overlay that is already showing (a plain field would leave the UI stale).
     */
    var mode: BreakScreenMode by mutableStateOf(BreakScreenMode.WARNING)
        private set

    /**
     * An optional line with details: why the warning fired, or how long the cooldown still lasts.
     */
    var contextText: String? by mutableStateOf(null)
        private set

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { BreakScreenMode.valueOf(it) }.getOrNull() }
            ?: BreakScreenMode.WARNING
        contextText = intent?.getStringExtra(EXTRA_CONTEXT_TEXT)
        return super.onStartCommand(intent, flags, startId)
    }
}

/**
 * Connects the [BreakDoomScrollingOverlayService] state to the actual break-screen UI.
 */
@Composable
fun BreakDoomScrollingOverlay() {
    val context = LocalContext.current
    val overlayService = context as? BreakDoomScrollingOverlayService
    BreakDoomScrollingOverlayContent(
        mode = overlayService?.mode ?: BreakScreenMode.WARNING,
        contextText = overlayService?.contextText,
        onExitApp = {
            overlayService?.let { service ->
                service.closeOverlay()
                // best-effort: kill the doom-scrolling app so it also disappears from recents
                // (works when Shizuku is set up; otherwise degrades gracefully, never crashes)
                ForceStopUtil.tryForceStop(service, service.runningAppPackageName)
            }
        },
        onFinishFirst = {
            overlayService?.let { service ->
                BreakDoomScrollingFeature.startFinishGrace(service, service.runningAppPackageName)
                service.dismissOverlay()
            }
        },
    )
}

/**
 * The break screen that is shown over a doom-scrolling app, in one of three variants
 * (see [BreakScreenMode]). All variants offer an immediate exit; [BreakScreenMode.WARNING]
 * additionally offers to finish the current item first, and [BreakScreenMode.GUIDE_OUT] counts
 * down and exits by itself.
 */
@Composable
fun BreakDoomScrollingOverlayContent(
    mode: BreakScreenMode,
    contextText: String?,
    onExitApp: () -> Unit,
    onFinishFirst: () -> Unit,
) {
    InterventionScreen(
        icon = when (mode) {
            BreakScreenMode.WARNING -> painterResource(id = R.drawable.ic_scroll)
            BreakScreenMode.COOLDOWN -> rememberVectorPainter(Icons.Default.SelfImprovement)
            BreakScreenMode.GUIDE_OUT -> rememberVectorPainter(Icons.Default.WavingHand)
        },
        title = stringResource(
            id = when (mode) {
                BreakScreenMode.WARNING -> R.string.feature_doomScrolling_warning_title
                BreakScreenMode.COOLDOWN -> R.string.feature_doomScrolling_cooldown_title
                BreakScreenMode.GUIDE_OUT -> R.string.feature_doomScrolling_guideOut_title
            }
        ),
        message = stringResource(
            id = when (mode) {
                BreakScreenMode.WARNING -> R.string.feature_doomScrolling_warning_message
                BreakScreenMode.COOLDOWN -> R.string.feature_doomScrolling_cooldown_message
                BreakScreenMode.GUIDE_OUT -> R.string.feature_doomScrolling_guideOut_message
            }
        ),
        contextText = contextText,
    ) {
        if (mode == BreakScreenMode.GUIDE_OUT) {
            DrainingBar(
                durationMs = BreakDoomScrollingOverlayService.GUIDE_OUT_AUTO_EXIT_MS,
                onFinished = onExitApp
            )
        }
        InterventionPrimaryButton(
            text = stringResource(id = R.string.feature_doomScrolling_warning_exit),
            onClick = onExitApp
        )
        if (mode == BreakScreenMode.WARNING) {
            InterventionSecondaryButton(
                text = stringResource(id = R.string.feature_doomScrolling_warning_finishFirst),
                onClick = onFinishFirst
            )
        }
    }
}

@Preview
@Composable
private fun BreakDoomScrollingWarningPreview() {
    BreakDoomScrollingOverlayContent(
        mode = BreakScreenMode.WARNING,
        contextText = "You've flicked through about 42 screens in the last 3 minutes.",
        onExitApp = {},
        onFinishFirst = {},
    )
}

@Preview
@Composable
private fun BreakDoomScrollingCooldownPreview() {
    BreakDoomScrollingOverlayContent(
        mode = BreakScreenMode.COOLDOWN,
        contextText = "This feed in Instagram will be back in 7 min.",
        onExitApp = {},
        onFinishFirst = {},
    )
}

@Preview
@Composable
private fun BreakDoomScrollingGuideOutPreview() {
    BreakDoomScrollingOverlayContent(
        mode = BreakScreenMode.GUIDE_OUT,
        contextText = null,
        onExitApp = {},
        onFinishFirst = {},
    )
}
