package com.flx_apps.digitaldetox.ui.widgets

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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme

/**
 * The calm, dark full-screen layout DetoxDroid uses when it holds the user back from an app: a
 * slowly breathing badge, a title, a message, an optional line of context, and the [actions] the
 * situation offers ([InterventionPrimaryButton], [InterventionSecondaryButton], [DrainingBar]).
 */
@Composable
fun InterventionScreen(
    icon: Painter,
    title: String,
    message: String,
    contextText: String? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    // the screen is dark whatever the system theme, and hosted by an activity the theme also
    // colors the system bars, which must not come out light on it
    DetoxDroidTheme(darkTheme = true) {
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
            BreathingBadge(icon)
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = message,
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
            actions()
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

/** The way out. White and prominent, because leaving is what an intervention screen is for. */
@Composable
fun InterventionPrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White, contentColor = Color.Black
        ),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
        modifier = Modifier.padding(top = 32.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The quiet alternative below [InterventionPrimaryButton], such as carrying on after all. */
@Composable
fun InterventionSecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(modifier = Modifier.padding(top = 8.dp), onClick = onClick, enabled = enabled) {
        Text(text = text, color = Color.White.copy(alpha = if (enabled) 0.7f else 0.3f))
    }
}

/**
 * A thin bar draining from [startFraction] to empty over [durationMs]; calls [onFinished] when it
 * runs out. It owns the timing, so what the bar shows and what happens at its end can never drift
 * apart.
 */
@Composable
fun DrainingBar(durationMs: Long, onFinished: () -> Unit, startFraction: Float = 1f) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(startFraction) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = durationMs.toInt(), easing = LinearEasing)
        )
        currentOnFinished()
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

/**
 * A slowly pulsing ("breathing") circular badge around [icon]. A calm visual anchor that sets the
 * pace against the frantic habit that led here.
 */
@Composable
private fun BreathingBadge(icon: Painter) {
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
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = Color.White.copy(alpha = 0.9f)
        )
    }
}

private val LOGO_SIZE = 196.dp
