package com.flx_apps.digitaldetox.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Background gradient — dark charcoal to slate, consistent with the previous marketing set. */
private val CardBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1B2430),
        Color(0xFF2C3A47),
    )
)

private val TextPrimary = Color.White
private val TextSecondary = Color(0xCCFFFFFF) // 80% white

/**
 * Full-screen marketing card that frames an app screen (or a mock scene) for the store listing.
 *
 * Layout (fills the whole window):
 * - dark gradient background
 * - headline + subtitle at the top
 * - the screen content below, filling the rest, clipped with rounded corners + a drop shadow so it
 *   reads as a floating phone screen.
 */
@Composable
fun MarketingCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Headline ──────────────────────────────────────────────────────
            Spacer(Modifier.height(36.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            // ── Screen content ────────────────────────────────────────────────
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                content()
            }
        }
    }
}
