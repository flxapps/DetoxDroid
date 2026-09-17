package com.flx_apps.digitaldetox.ui.screens.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.InterventionPrimaryButton
import com.flx_apps.digitaldetox.ui.widgets.InterventionScreen
import com.flx_apps.digitaldetox.ui.widgets.InterventionSecondaryButton
import com.flx_apps.digitaldetox.util.NavigationUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** How far the droid at the bottom of the home screen has woken up. */
internal enum class DroidMood { Asleep, OneEyeOpen, Awake, WideAwake }

/** A run of taps on the droid fast enough to count as tapping it the way people scroll a feed. */
internal data class DroidCaught(val taps: Int, val seconds: Int)

/**
 * Counts the taps on the droid. The first one opens an eye, a few more open both, and
 * [CATCH_TAPS] within [CATCH_WINDOW_MS] get the tapper caught.
 */
internal class DroidTapTracker {
    private val recentTaps = ArrayDeque<Long>()
    private var tapsSinceWakeUp = 0
    private var oneMoreTapAllowed = false

    var mood = DroidMood.Asleep
        private set

    /** Counts a tap at [nowMs] and returns the catch when this tap completes one. */
    fun onTap(nowMs: Long): DroidCaught? {
        if (oneMoreTapAllowed) {
            // a deal's a deal: the one tap that was asked for, then the droid goes back to sleep
            fallAsleep()
            return null
        }
        recentTaps.addLast(nowMs)
        while (nowMs - recentTaps.first() > CATCH_WINDOW_MS) recentTaps.removeFirst()
        tapsSinceWakeUp++
        mood = when {
            tapsSinceWakeUp >= WIDE_AWAKE_TAPS -> DroidMood.WideAwake
            tapsSinceWakeUp >= AWAKE_TAPS -> DroidMood.Awake
            else -> DroidMood.OneEyeOpen
        }
        if (recentTaps.size < CATCH_TAPS) return null
        val seconds = ceil((nowMs - recentTaps.first()) / 1000.0).toInt().coerceAtLeast(1)
        val caught = DroidCaught(taps = recentTaps.size, seconds = seconds)
        // the count starts over, but the droid stays wide awake while it has the tapper's attention
        recentTaps.clear()
        tapsSinceWakeUp = 0
        return caught
    }

    /** Lets the next tap through without counting it, and puts the droid to sleep on it. */
    fun allowOneMoreTap() {
        oneMoreTapAllowed = true
    }

    fun fallAsleep() {
        recentTaps.clear()
        tapsSinceWakeUp = 0
        oneMoreTapAllowed = false
        mood = DroidMood.Asleep
    }

    companion object {
        const val AWAKE_TAPS = 3
        const val WIDE_AWAKE_TAPS = 10
        const val CATCH_TAPS = 15
        const val CATCH_WINDOW_MS = 10_000L
    }
}

/**
 * The droid's state, held by the home screen: the droid scrolls away with the list, while the
 * screen that catches the tapper covers all of it.
 */
@Stable
internal class SleepyDroidState {
    private val tracker = DroidTapTracker()

    var mood by mutableStateOf(DroidMood.Asleep)
        private set

    /** Goes up with every tap, for the effects that start over on one. */
    var taps by mutableIntStateOf(0)
        private set

    var caught by mutableStateOf<DroidCaught?>(null)
        private set

    fun tap() {
        if (caught != null) return
        caught = tracker.onTap(SystemClock.uptimeMillis())
        mood = tracker.mood
        taps++
    }

    fun fallAsleep() {
        tracker.fallAsleep()
        mood = tracker.mood
    }

    fun leave() {
        caught = null
        fallAsleep()
    }

    fun finishFirst() {
        caught = null
        tracker.allowOneMoreTap()
    }
}

@Composable
internal fun rememberSleepyDroidState() = remember { SleepyDroidState() }

/**
 * The droid at the bottom of the home screen. It sleeps, and taps wake it up bit by bit until it
 * nods off again after [SLEEP_AFTER_MS] without one. Between [NIGHT_START_HOUR] and
 * [NIGHT_END_HOUR] it snores and keeps its eyes shut, whoever taps it.
 *
 * Taps are read with a gesture detector rather than `clickable`, so screen readers keep treating
 * the droid as the decoration it looks like.
 */
@Composable
internal fun SleepyDroid(state: SleepyDroidState, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isNight by produceState(initialValue = isNightNow()) {
        while (true) {
            delay(60_000L)
            value = isNightNow()
        }
    }
    val antennaTwitch = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    var talksInSleep by remember { mutableStateOf(false) }

    LaunchedEffect(state.taps, state.caught) {
        if (state.caught != null || state.mood == DroidMood.Asleep) return@LaunchedEffect
        delay(SLEEP_AFTER_MS)
        state.fallAsleep()
    }
    LaunchedEffect(state.taps) {
        if (!talksInSleep) return@LaunchedEffect
        delay(SPEECH_BUBBLE_MS)
        talksInSleep = false
    }

    val mood = if (isNight) DroidMood.Asleep else state.mood
    val leftEyeOpen by animateFloatAsState(
        targetValue = if (mood != DroidMood.Asleep) 1f else 0f,
        animationSpec = tween(EYE_MS),
        label = "leftEyeOpen"
    )
    val rightEyeOpen by animateFloatAsState(
        targetValue = if (mood >= DroidMood.Awake) 1f else 0f,
        animationSpec = tween(EYE_MS),
        label = "rightEyeOpen"
    )
    val eyeSize by animateFloatAsState(
        targetValue = if (mood == DroidMood.WideAwake) 1.3f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "eyeSize"
    )

    val body = painterResource(id = R.drawable.droid_body)
    val antennaLeft = painterResource(id = R.drawable.droid_antenna_left)
    val antennaRight = painterResource(id = R.drawable.droid_antenna_right)
    val eyeLeftClosed = painterResource(id = R.drawable.droid_eye_left_closed)
    val eyeRightClosed = painterResource(id = R.drawable.droid_eye_right_closed)

    Box(modifier = modifier.height(DROID_SIZE)) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(DROID_SIZE)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // the upper half of the drawable is empty space above the antennas
                        if (offset.y < size.height / 2) return@detectTapGestures
                        state.tap()
                        haptics.performHapticFeedback(
                            if (state.caught != null) HapticFeedbackType.LongPress
                            else HapticFeedbackType.TextHandleMove
                        )
                        scope.launch {
                            squash.snapTo(0.9f)
                            squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
                        }
                        if (isNight) {
                            talksInSleep = true
                        } else if (state.mood >= DroidMood.Awake) {
                            val angle = if (state.mood == DroidMood.WideAwake) 16f else 10f
                            scope.launch {
                                antennaTwitch.snapTo(if (state.taps % 2 == 0) angle else -angle)
                                antennaTwitch.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 400f))
                            }
                        }
                    }
                }
        ) {
            val unit = size.width / VIEWPORT
            scale(
                scaleX = 1f + (1f - squash.value) / 2,
                scaleY = squash.value,
                pivot = Offset(size.width / 2, size.height)
            ) {
                // the antennas swing in opposite directions, so they twitch as a pair
                rotate(antennaTwitch.value, pivot = LEFT_ANTENNA_PIVOT * unit) {
                    with(antennaLeft) { draw(size) }
                }
                rotate(-antennaTwitch.value, pivot = RIGHT_ANTENNA_PIVOT * unit) {
                    with(antennaRight) { draw(size) }
                }
                with(body) { draw(size) }
                drawEye(eyeLeftClosed, LEFT_EYE_CENTER * unit, leftEyeOpen, eyeSize, unit)
                drawEye(eyeRightClosed, RIGHT_EYE_CENTER * unit, rightEyeOpen, eyeSize, unit)
            }
        }
        AnimatedVisibility(
            visible = isNight && !talksInSleep,
            enter = fadeIn(),
            exit = fadeOut(),
            // starts at the top of the head, right of its middle
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 6.dp, y = 30.dp)
        ) {
            Snores()
        }
        AnimatedVisibility(
            visible = talksInSleep,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            SpeechBubble(text = stringResource(id = R.string.home_droid_asleep))
        }
    }
}

/**
 * An eye that is [openness] of the way open: the closed curve from the artwork fades out as a dark
 * oval grows from its middle, and the glint comes last.
 */
private fun DrawScope.drawEye(
    closed: Painter, center: Offset, openness: Float, eyeSize: Float, unit: Float
) {
    val closedAlpha = (1f - openness / 0.4f).coerceIn(0f, 1f)
    if (closedAlpha > 0f) with(closed) { draw(size, alpha = closedAlpha) }
    if (openness <= 0f) return
    val radiusX = EYE_RADIUS_X * unit * eyeSize
    val radiusY = EYE_RADIUS_Y * unit * eyeSize * openness
    drawOval(
        color = DROID_INK,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2, radiusY * 2)
    )
    if (openness > 0.7f) {
        drawCircle(
            color = DROID_SHINE,
            radius = GLINT_RADIUS * unit * eyeSize,
            center = Offset(center.x - radiusX * 0.35f, center.y - radiusY * 0.4f),
            alpha = (openness - 0.7f) / 0.3f
        )
    }
}

/** Three z's taking turns to drift up from the droid's head, growing as they fade. */
@Composable
private fun Snores() {
    val cycle = rememberInfiniteTransition(label = "snores")
    val progress by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SNORE_CYCLE_MS, easing = LinearEasing)),
        label = "snoreProgress"
    )
    val z = stringResource(id = R.string.home_droid_snore)
    Box {
        repeat(3) { index ->
            Text(
                text = z,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // read in the layer only, so the drift redraws without recomposing
                modifier = Modifier.graphicsLayer {
                    val phase = (progress + index / 3f) % 1f
                    translationX = phase * 14.dp.toPx() + sin(phase * 2 * PI.toFloat()) * 3.dp.toPx()
                    translationY = -phase * 30.dp.toPx()
                    scaleX = 0.7f + phase * 0.6f
                    scaleY = scaleX
                    alpha = if (phase < 0.2f) phase / 0.2f else (1f - phase) / 0.8f
                }
            )
        }
    }
}

@Composable
private fun SpeechBubble(text: String) {
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .background(color, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        // the tail: a square on its corner, its upper half lost in the bubble
        Box(
            modifier = Modifier
                .offset(y = (-4).dp)
                .size(8.dp)
                .rotate(45f)
                .background(color)
        )
    }
}

/**
 * The doom-scrolling warning, turned on whoever taps the droid like a feed. It covers the whole
 * home screen the way the real one covers an app, and its way out leads to the home screen too.
 */
@Composable
internal fun DroidCaughtOverlay(state: SleepyDroidState) {
    val context = LocalContext.current
    // no size transform: the screen fades in at full size instead of growing out of a corner
    AnimatedContent(
        targetState = state.caught,
        transitionSpec = { (fadeIn() togetherWith fadeOut()).using(null) },
        label = "droidCaught"
    ) { caught ->
        if (caught == null) return@AnimatedContent
        val leave = {
            state.leave()
            context.startActivity(NavigationUtil.homeScreenIntent())
        }
        BackHandler(enabled = state.caught != null, onBack = leave)
        LightSystemBarIcons()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // keeps taps from reaching the home screen underneath
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            InterventionScreen(
                icon = rememberVectorPainter(Icons.Default.TouchApp),
                title = stringResource(id = R.string.feature_doomScrolling_warning_title),
                message = stringResource(id = R.string.home_droid_caught_message),
                contextText = pluralStringResource(
                    id = R.plurals.home_droid_caught_context,
                    count = caught.seconds,
                    caught.taps,
                    caught.seconds
                ),
            ) {
                InterventionPrimaryButton(
                    text = stringResource(id = R.string.feature_doomScrolling_warning_exit),
                    onClick = leave
                )
                InterventionSecondaryButton(
                    text = stringResource(id = R.string.feature_doomScrolling_warning_finishFirst),
                    onClick = { state.finishFirst() }
                )
            }
        }
    }
}

/**
 * Light status and navigation bar icons while this is shown. The activity picks icon colors for
 * the app's theme, which leaves dark icons over the dark intervention screen in light mode.
 */
@Composable
private fun LightSystemBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}

private fun isNightNow() = LocalTime.now().hour in NIGHT_START_HOUR until NIGHT_END_HOUR

private const val SLEEP_AFTER_MS = 3_000L
private const val SPEECH_BUBBLE_MS = 3_000L
private const val EYE_MS = 140
private const val SNORE_CYCLE_MS = 3_600
private const val NIGHT_START_HOUR = 1
private const val NIGHT_END_HOUR = 5

private val DROID_SIZE = 76.dp

// Geometry in the 512 × 512 viewport the droid drawables share. The pivots sit in the middle of
// the cut between each antenna and the head, so a twitch never opens a visible gap.
private const val VIEWPORT = 512f
private val LEFT_ANTENNA_PIVOT = Offset(148.75f, 335.63f)
private val RIGHT_ANTENNA_PIVOT = Offset(363.75f, 336.25f)
private val LEFT_EYE_CENTER = Offset(179f, 414f)
private val RIGHT_EYE_CENTER = Offset(326f, 414f)
private const val EYE_RADIUS_X = 15f
private const val EYE_RADIUS_Y = 19f
private const val GLINT_RADIUS = 4.2f
private val DROID_INK = Color(0xFF4D4D4D)
private val DROID_SHINE = Color(0xFFF2F2F2)
