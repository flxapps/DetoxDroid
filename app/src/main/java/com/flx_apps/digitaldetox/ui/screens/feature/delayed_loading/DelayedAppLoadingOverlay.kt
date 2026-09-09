package com.flx_apps.digitaldetox.ui.screens.feature.delayed_loading

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DelayedAppLoadingFeature
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import kotlinx.coroutines.delay

class DelayedAppLoadingOverlayService : OverlayService(OverlayContent {
    val service = LocalContext.current as DelayedAppLoadingOverlayService
    DelayedAppLoadingOverlay(
        packageName = service.runningAppPackageName,
        totalDelaySeconds = service.delaySeconds,
        onCountdownFinished = {
            DelayedAppLoadingFeature.grantTemporaryAccess(service.runningAppPackageName)
            service.dismissOverlayOnly()
        },
        onCancel = {
            service.closeOverlay() // Returns user to home screen
        }
    )
}) {
    companion object {
        const val EXTRA_DELAY_SECONDS = "extraDelaySeconds"
    }

    var delaySeconds: Int = 10
        private set

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        delaySeconds = intent?.getIntExtra(EXTRA_DELAY_SECONDS, 10) ?: 10
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Dismisses the overlay without taking the user to the home screen so they can use the app.
     */
    fun dismissOverlayOnly() {
        stopSelf()
    }
}

@Composable
fun DelayedAppLoadingOverlay(
    packageName: String,
    totalDelaySeconds: Int,
    onCountdownFinished: () -> Unit,
    onCancel: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(totalDelaySeconds) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
        onCountdownFinished()
    }

    val progress = if (totalDelaySeconds > 0) {
        (totalDelaySeconds - remainingSeconds).toFloat() / totalDelaySeconds.toFloat()
    } else 1f

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "delayProgress")

    DetoxDroidTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(id = R.string.feature_delayedLoading_overlay_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.feature_delayedLoading_overlay_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            // Circular Countdown Timer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 10.dp,
                    trackColor = Color.DarkGray
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$remainingSeconds",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(id = R.string.feature_delayedLoading_seconds_remaining),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Cancellation button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .scale(1.2f)
                        .padding(bottom = 24.dp)
                ) {
                    Text(text = stringResource(id = R.string.feature_delayedLoading_action_cancel))
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_cropped),
                    contentDescription = "Logo",
                    modifier = Modifier.size(96.dp)
                )
            }
        }
    }
}
