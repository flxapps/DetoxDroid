package com.flx_apps.digitaldetox.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.ColorMatrixColorFilter

/**
 * Renders [content] into an offscreen layer and desaturates it with a saturation-0 colour matrix —
 * the same visual effect DetoxDroid's Grayscale feature applies to the whole system. Used to show
 * a "before/after" of the grayscale feature on a mock social feed without touching real apps.
 */
fun Modifier.grayscale(): Modifier = drawWithContent {
    val paint = Paint().apply {
        asFrameworkPaint().colorFilter = ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(0f) }
        )
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.saveLayer(
            0f, 0f, size.width, size.height, paint.asFrameworkPaint()
        )
        drawContent()
        canvas.nativeCanvas.restore()
    }
}

/**
 * Draws the content in full colour, then redraws a desaturated copy clipped to the bottom half —
 * a crisp colour→gray boundary at the vertical centre. [MockGrayscaleSplit] marks the boundary
 * with a bright edge and a "Grayscale on" chip so it reads as the filter being applied live.
 */
fun Modifier.grayscaleWipe(): Modifier = drawWithContent {
    drawContent() // top: full colour
    val paint = Paint().apply {
        asFrameworkPaint().colorFilter = ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(0f) }
        )
    }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save()
        nc.clipRect(0f, size.height / 2f, size.width, size.height)
        val layer = nc.saveLayer(0f, 0f, size.width, size.height, paint.asFrameworkPaint())
        drawContent() // bottom half: grayscaled copy, pixel-aligned with the colour version
        nc.restoreToCount(layer)
        nc.restore()
    }
}

// A busy, engagement-baity post. Saturated colours so the grayscale "colour drain" reads clearly.
private data class MockPost(
    val author: String,
    val handle: String,
    val avatar: Color,
    val imageTop: Color,
    val imageBottom: Color,
    val caption: String,
    val time: String,
    val badge: String?,      // e.g. "🔥 Trending", "Sponsored" — null for none
    val likes: String,
    val comments: String,
    val shares: String,
    val video: Boolean = false,
)

private val mockPosts = listOf(
    MockPost(
        "Wanderlust", "@travelmore", Color(0xFFEF476F),
        Color(0xFFFF9A8B), Color(0xFFFF3D77),
        "Sunset over the coast 🌅🔥 tap to see 42 more from today #travel #wanderlust #nofilter",
        "2h", "🔥 Trending", "12.4k", "843", "2.1k",
    ),
    MockPost(
        "Foodie Daily", "@eatthis", Color(0xFFFFC43D),
        Color(0xFFFF6A00), Color(0xFFFFD200),
        "10 recipes you HAVE to try this weekend 🍜🍕🔥 #foodie #viral",
        "just now", "Sponsored", "9.8k", "1.2k", "4.5k", video = true,
    ),
    MockPost(
        "Meme Central", "@lol.feed", Color(0xFF1B9AAA),
        Color(0xFF667EEA), Color(0xFF9D50BB),
        "You won't believe what happens next 😂😂 #meme #lol #fyp",
        "5m", "🔥 Trending", "88.2k", "6.4k", "21k",
    ),
    MockPost(
        "Trendsetter", "@daily.drip", Color(0xFF06D6A0),
        Color(0xFFF7971E), Color(0xFFFF3D77),
        "New drop just landed — swipe up before it's gone ⚡️👟 #hype",
        "12m", "Sponsored", "5.1k", "402", "1.3k",
    ),
)

// Story ring gradients (name, ring colours) for the "buzzy" horizontal stories rail. Kept to five
// so every ring fits inside the card width and stays fully round (a sixth overflows and the card's
// rounded-corner clip turns it into a pill).
private val mockStories = listOf(
    "you" to listOf(Color(0xFF9AA0A6), Color(0xFF9AA0A6)),
    "mia" to listOf(Color(0xFFFEDA75), Color(0xFFD62976)),
    "leo" to listOf(Color(0xFF00F5A0), Color(0xFF00D9F5)),
    "ava" to listOf(Color(0xFFFF3D77), Color(0xFFFF9A8B)),
    "sam" to listOf(Color(0xFF7367F0), Color(0xFFCE9FFC)),
)

/**
 * A self-contained mock social-media feed (no external assets). Deliberately loud and busy —
 * stories rail, notification badges, engagement counts and trending chips — so the grayscale
 * filter has plenty of colour to visibly drain. Background for the grayscale/pause scenes.
 */
@Composable
fun MockSocialFeed(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10)),
    ) {
        MockFeedTopBar()
        MockStoriesRail()
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF23232A)))
        mockPosts.forEachIndexed { index, post ->
            MockFeedPost(post)
            if (index != mockPosts.lastIndex) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF0E0E10)))
            }
        }
    }
}

@Composable
private fun MockFeedTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF17171B))
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Feed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFF3D3D))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        // Notification bell with an unread badge
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp).padding(2.dp),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF3D3D))
                    .padding(horizontal = 4.dp),
            ) {
                Text("9+", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        StoryRing(listOf(Color(0xFF3A86FF), Color(0xFF00D9F5)), size = 28.dp)
    }
}

@Composable
private fun MockStoriesRail() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF17171B))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        mockStories.forEach { (name, ring) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StoryRing(ring, size = 56.dp)
                Text(
                    name,
                    color = Color(0xFFD5D7DB),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StoryRing(ring: List<Color>, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(ring))
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(Color(0xFF17171B))
            .padding(2.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(ring.reversed())),
    )
}

/**
 * Shows the grayscale filter being applied to the live feed in the moment: the feed fades from
 * colour to gray across a soft band, with a glowing wipe edge and a "Grayscale on" indicator chip
 * riding the boundary so it reads as an active effect, not a static split.
 */
@Composable
fun MockGrayscaleSplit(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MockSocialFeed(Modifier.grayscaleWipe())
        // Bright leading edge of the wipe.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.5.dp)
                .background(Color.White)
        )
        // Indicator chip riding the wipe edge — names the effect that's happening right now.
        GrayscaleChip(Modifier.align(Alignment.Center))
    }
}

/** A pill that labels the active grayscale filter, led by a half/half "contrast" glyph. */
@Composable
private fun GrayscaleChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF20E1116))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContrastGlyph(18.dp)
        Text(
            "Grayscale on",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 9.dp),
        )
    }
}

/** The universal grayscale/contrast symbol: a circle split half-light, half-dark. */
@Composable
private fun ContrastGlyph(size: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White, CircleShape),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF12141A)))
    }
}

/**
 * The mock feed with one post kept "in colour" while the rest is grayscaled — illustrates per-app
 * exceptions ("keep the apps you need in colour").
 */
@Composable
fun MockExceptionsScene(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0E10)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF17171B))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Messages", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            // The "excepted" (in-colour) app chat
            MockChat()
        }
    }
}

@Composable
private fun MockChat() {
    val bubbles = listOf(
        Triple("Are we still on for the hike tomorrow? 🥾", false, Color(0xFF2A2A31)),
        Triple("Yes! Trailhead at 8, I'll bring coffee ☕", true, Color(0xFF3A86FF)),
        Triple("Perfect. Weather looks amazing 🌞", false, Color(0xFF2A2A31)),
        Triple("Can't wait — see you there!", true, Color(0xFF3A86FF)),
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        bubbles.forEach { (text, mine, color) ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(color)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthMax(),
                )
            }
        }
    }
}

private fun Modifier.widthMax(): Modifier = this.fillMaxWidth(0.72f)

@Composable
private fun MockFeedPost(post: MockPost) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF17171B))
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(post.avatar)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF3A9DF5),
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                    Text(
                        "· ${post.time}",
                        color = Color(0xFF9AA0A6),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(post.handle, color = Color(0xFF9AA0A6), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF3A86FF))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text("Follow", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = Color(0xFF9AA0A6),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(Brush.verticalGradient(listOf(post.imageTop, post.imageBottom))),
            contentAlignment = Alignment.Center,
        ) {
            if (post.video) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            post.badge?.let { badge ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Engagement bar with counts — the buzzy part.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EngagementStat(Icons.Default.Favorite, post.likes, Color(0xFFFF3D77))
            Spacer(Modifier.width(18.dp))
            EngagementStat(Icons.AutoMirrored.Filled.Comment, post.comments, Color(0xFFD5D7DB))
            Spacer(Modifier.width(18.dp))
            EngagementStat(Icons.Default.Repeat, post.shares, Color(0xFFD5D7DB))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color(0xFFD5D7DB))
        }
        Text(
            post.caption,
            color = Color(0xFFD5D7DB),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun EngagementStat(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            count,
            color = Color(0xFFD5D7DB),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

// A calm, text-only home screen for the "minimal launcher" scene. Ordinary phone apps, no icons —
// the opposite of the loud, colourful feed in the other scenes. First entry is highlighted as a
// "favourite"; the rest recede so the whole screen reads as quiet.
private val mockLauncherApps = listOf(
    "Phone" to true,
    "Messages" to true,
    "Maps" to false,
    "Camera" to false,
    "Calendar" to false,
    "Notes" to false,
    "Settings" to false,
)

/**
 * A minimal, text-only launcher home screen (no external assets): a soft twilight wallpaper, the
 * time, and a plain list of app names instead of a grid of colourful icons — DetoxDroid's Minimal
 * Launcher widget. Deliberately calm and low-contrast so it reads as the antidote to the busy feed.
 */
@Composable
fun MockMinimalLauncher(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF20262F), Color(0xFF39434F)))
            )
            .padding(horizontal = 32.dp, vertical = 40.dp),
    ) {
        Text("9:41", color = Color(0xFFF2F4F7), fontSize = 56.sp, fontWeight = FontWeight.Light)
        Text(
            "Saturday, 18 July",
            color = Color(0xFF9AA6B2),
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            mockLauncherApps.forEach { (name, favourite) ->
                Text(
                    text = name,
                    color = if (favourite) Color(0xFFF2F4F7) else Color(0xFFB4BDC7),
                    fontSize = 26.sp,
                    fontWeight = if (favourite) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
