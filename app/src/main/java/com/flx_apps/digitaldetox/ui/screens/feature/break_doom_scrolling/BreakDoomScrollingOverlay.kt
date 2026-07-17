package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
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
    DetoxDroidTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF5141420), Color(0xFB08080D), Color(0xFF000000))
                    )
                )
                .padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1.2f))
            BreathingBadge(mode)
            Text(
                text = stringResource(
                    id = when (mode) {
                        BreakScreenMode.WARNING -> R.string.infiniteScroll_warning_title
                        BreakScreenMode.COOLDOWN -> R.string.infiniteScroll_cooldown_title
                        BreakScreenMode.GUIDE_OUT -> R.string.infiniteScroll_guideOut_title
                    }
                ),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = stringResource(
                    id = when (mode) {
                        BreakScreenMode.WARNING -> R.string.infiniteScroll_warning_message
                        BreakScreenMode.COOLDOWN -> R.string.infiniteScroll_cooldown_message
                        BreakScreenMode.GUIDE_OUT -> R.string.infiniteScroll_guideOut_message
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 16.dp)
            )
            contextText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
            if (mode == BreakScreenMode.GUIDE_OUT) {
                GuideOutCountdown(onFinished = onExitApp)
            }
            Button(
                onClick = onExitApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.infiniteScroll_warning_exit),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (mode == BreakScreenMode.WARNING) {
                TextButton(
                    modifier = Modifier.padding(top = 8.dp), onClick = onFinishFirst
                ) {
                    Text(
                        text = stringResource(id = R.string.infiniteScroll_warning_finishFirst),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground_cropped),
                contentDescription = null,
                // the drawable carries a 15% transparent safe-zone margin on every side (its
                // artwork is wrapped in a scale(0.7) group); shifting it down by exactly that
                // margin puts the droid flush on the bottom screen edge
                modifier = Modifier
                    .size(LOGO_SIZE)
                    .offset(y = LOGO_SIZE * 0.15f)
            )
        }
    }
}

/**
 * A slowly pulsing ("breathing") circular badge with a mode-specific icon — a calm visual anchor
 * that sets the pace against the frantic scrolling that led here.
 */
@Composable
private fun BreathingBadge(mode: BreakScreenMode) {
    val breath = rememberInfiniteTransition(label = "breathing")
    val scale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(104.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        val tint = Color.White.copy(alpha = 0.9f)
        val iconModifier = Modifier.size(44.dp)
        when (mode) {
            BreakScreenMode.WARNING -> Icon(
                painterResource(id = R.drawable.ic_scroll), null, iconModifier, tint
            )

            BreakScreenMode.COOLDOWN -> Icon(
                Icons.Default.SelfImprovement, null, iconModifier, tint
            )

            BreakScreenMode.GUIDE_OUT -> Icon(
                Icons.Default.WavingHand, null, iconModifier, tint
            )
        }
    }
}

/**
 * A thin bar draining from full to empty over
 * [BreakDoomScrollingOverlayService.GUIDE_OUT_AUTO_EXIT_MS]; calls [onFinished] when it runs out.
 * It owns the guide-out timing, so the countdown and the actual exit can never drift apart.
 */
@Composable
private fun GuideOutCountdown(onFinished: () -> Unit) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = BreakDoomScrollingOverlayService.GUIDE_OUT_AUTO_EXIT_MS.toInt(),
                easing = LinearEasing
            )
        )
        onFinished()
    }
    Box(
        modifier = Modifier
            .padding(top = 24.dp)
            .width(160.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.value)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.85f))
        )
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

private val LOGO_SIZE = 196.dp
