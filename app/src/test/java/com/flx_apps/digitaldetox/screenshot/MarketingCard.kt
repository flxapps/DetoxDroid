package com.flx_apps.digitaldetox.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The frame every store screenshot sits in.
//
// Six cards on one charcoal gradient, under the same centred caption, read as a template applied
// six times, which is what the old strip was. Three things vary now, and one deliberately does not.
//
// Varying: the background is a flat colour per slot, the caption carries the icon the app itself
// uses for that feature, and the composition rotates through [CardShape].
//
// Not varying: the backgrounds are flat. A gradient behind an app panel muddies the edge between
// frame and screen at the ~200 px thumbnail Play shows first, which is the size that decides the
// install.
//
// The six colours are the six stripes of the launcher icon, in the order the icon draws them, so
// the strip reads the icon back. Each is that stripe pushed down to a background value; the accent
// beside it is the same hue lifted until it clears 7:1 there, because the caption title is drawn
// in it.

/** Flat background and accent for one screenshot slot. */
class MarketingStyle(val accent: Color, val background: Color)

/**
 * The six slots, in strip order — and in launcher-stripe order: crimson, orange, amber, green,
 * blue, violet.
 *
 * The hues also land where they mean something. Amber is the slot about the apps kept in colour,
 * blue the one about statistics, violet the twilight home screen the minimal launcher draws.
 */
object Slot {
    val Hero = MarketingStyle(accent = Color(0xFFEB84A6), background = Color(0xFF230D15))
    val Grayscale = MarketingStyle(accent = Color(0xFFEE9377), background = Color(0xFF2B1610))
    val Exceptions = MarketingStyle(accent = Color(0xFFF5CD5C), background = Color(0xFF2B230A))
    val Doomscroll = MarketingStyle(accent = Color(0xFF7BD5AF), background = Color(0xFF092519))
    val UsageStats = MarketingStyle(accent = Color(0xFF7BB8F4), background = Color(0xFF09192A))
    val Launcher = MarketingStyle(accent = Color(0xFFB399E6), background = Color(0xFF1C1726))
}

/** 80 % off-white. Subtitles are the same on every card; only the accent moves. */
private val SubtitleColor = Color(0xCCE6EAF5)

/** Horizontal inset shared by panel and caption, so the caption icon's left edge lines up. */
private val Gutter = 26.dp

/** How one card arranges its screen against its caption. */
enum class CardShape {

    /** Panel inset on all four sides, caption underneath. */
    CaptionUnder,

    /** The same panel, caption above it. */
    CaptionOver,

    /**
     * Caption above, and the screen laid out taller than the window it is shown in, so it runs off
     * the bottom edge of the card and is cut there.
     *
     * Only for screens with enough content to fill [BLEED_LAYOUT_HEIGHT]. On a short screen the
     * extra height is white space, and a crop meant to say "there is more below" says "the app is
     * empty" instead.
     */
    Bleed,

    /**
     * The screen edge to edge at full card width, caption on a solid band across the bottom. For
     * the scenes that are a whole phone rather than a panel — a break screen and a home screen lose
     * more to a 26 dp gutter than they gain from the frame around it.
     */
    Band;

    val captionOnTop: Boolean get() = this == CaptionOver || this == Bleed
}

/**
 * Height a screen is laid out at inside a [CardShape.Bleed] window: taller than the window, so the
 * screen keeps its real proportions and the frame cuts it rather than the screen squashing itself
 * to fit and leaving a half-empty list behind.
 */
private val BLEED_LAYOUT_HEIGHT = 900.dp

/** A marketing card: one app screen, one caption, one flat background. */
@Composable
fun MarketingCard(
    title: String,
    subtitle: String,
    style: MarketingStyle,
    icon: @Composable () -> Painter,
    shape: CardShape = CardShape.CaptionUnder,
    content: @Composable () -> Unit,
) {
    if (shape == CardShape.Band) {
        BandCard(title, subtitle, style, icon, content)
        return
    }
    Box(Modifier.fillMaxSize().background(style.background)) {
        Column(Modifier.fillMaxSize()) {
            if (shape.captionOnTop) {
                Spacer(Modifier.height(46.dp))
                Caption(title, subtitle, style, icon)
                Spacer(Modifier.height(28.dp))
                if (shape == CardShape.Bleed) BleedPanel(style, content)
                else FramedPanel(style, content, bottomSpace = 34.dp)
            } else {
                Spacer(Modifier.height(34.dp))
                FramedPanel(style, content, bottomSpace = 28.dp)
                Caption(title, subtitle, style, icon)
                Spacer(Modifier.height(38.dp))
            }
        }
    }
}

/**
 * The first card. Play renders screenshot 1 largest and people decide there, so the headline gets
 * the width and the size the slot is worth, and the home screen rises out of the card's own bottom
 * edge underneath it rather than sitting in a frame like the other five.
 */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    style: MarketingStyle,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(style.background)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(64.dp))
            Box(Modifier.padding(horizontal = Gutter)) {
                FittedText(title, Color.White, HeroTitleSizes, maxLines = 3)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = subtitle,
                color = SubtitleColor,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                modifier = Modifier.padding(horizontal = Gutter),
            )
            Spacer(Modifier.height(40.dp))
            // No bottom margin and no bottom radius: the screen rises out of the card's own edge.
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Gutter).clipToBounds()) {
                Panel(
                    style = style,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FramedPanel(
    style: MarketingStyle,
    content: @Composable () -> Unit,
    bottomSpace: Dp,
) {
    Panel(
        style = style,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Gutter),
        content = content,
    )
    Spacer(Modifier.height(bottomSpace))
}

@Composable
private fun ColumnScope.BleedPanel(style: MarketingStyle, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Gutter).clipToBounds()) {
        Panel(
            style = style,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .requiredHeight(BLEED_LAYOUT_HEIGHT),
            content = content,
        )
    }
}

@Composable
private fun BandCard(
    title: String,
    subtitle: String,
    style: MarketingStyle,
    icon: @Composable () -> Painter,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(style.background)) {
        Box(Modifier.fillMaxSize()) { content() }
        // The band is exactly as tall as the caption it carries, so German running a line longer
        // than English costs height here instead of leaving empty paint under the text.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(style.background),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 36.dp)) {
                Caption(title, subtitle, style, icon)
            }
            // The band cuts the screen at a hard edge; the hairline says that was meant, the way
            // the panel border does in the other shapes.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(style.accent.copy(alpha = 0.24f)),
            )
        }
    }
}

// ── Shared parts ─────────────────────────────────────────────────────────────

/** The app screen itself: rounded, shadowed, and hairlined in the slot's accent. */
@Composable
private fun Panel(
    style: MarketingStyle,
    shape: RoundedCornerShape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(26.dp, shape, spotColor = Color.Black, ambientColor = Color.Black)
            .clip(shape)
            .border(1.dp, style.accent.copy(alpha = 0.22f), shape),
    ) {
        content()
    }
}

/**
 * Icon on the left, title and subtitle flush left beside it.
 *
 * Centred text cost two extra lines on the German captions, and a ragged centre is hard to read at
 * thumbnail size. The icon is the one the app draws for that feature, so the card and the feature
 * screen it advertises agree.
 */
@Composable
private fun Caption(
    title: String,
    subtitle: String,
    style: MarketingStyle,
    icon: @Composable () -> Painter,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(style.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon(),
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            FittedText(title, style.accent, TitleSizes, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(text = subtitle, color = SubtitleColor, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

/** Sizes a caption title tries, largest first. */
private val TitleSizes = listOf(26.sp, 24.sp, 22.sp, 20.sp)

/** Sizes the first card's headline tries. It has the whole width, so it starts far larger. */
private val HeroTitleSizes = listOf(40.sp, 36.sp, 32.sp, 28.sp, 25.sp)

private fun fittedStyle(size: TextUnit, weight: FontWeight) = TextStyle(
    fontSize = size,
    lineHeight = size * 1.30f,
    fontWeight = weight,
    letterSpacing = if (weight == FontWeight.Bold) (-0.5).sp else TextUnit.Unspecified,
)

/**
 * A last line shorter than this fraction of the longest one is a widow, and one step down the ramp
 * usually buys a break that is not.
 */
private const val MIN_LAST_LINE = 0.40f

/** How far down the ramp the widow rule is allowed to push. Below that, small costs more. */
private const val WIDOW_STEPS = 3

/**
 * [text] at the largest of [sizes] that still fits [maxLines] without leaving a widow.
 *
 * "…without losing the apps you need." is 34 characters; the German is 44. One fixed size either
 * wastes the width in English or turns the German caption into a block that crowds out the screen
 * it labels.
 *
 * Fitting is not enough on its own: at the top of the ramp a headline can fit two lines with one
 * word alone on the second, which is worse than the same sentence a step smaller and evenly broken.
 * So among the sizes that fit, the first few are also asked whether their last line is worth
 * calling a line. Measured up front rather than shrunk over recompositions, so the capture is
 * stable.
 */
@Composable
private fun FittedText(
    text: String,
    color: Color,
    sizes: List<TextUnit>,
    maxLines: Int,
    weight: FontWeight = FontWeight.Bold,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val available = Constraints(maxWidth = constraints.maxWidth)
        val fitting = sizes
            .map { fittedStyle(it, weight) }
            .map { it to measurer.measure(text, it, constraints = available) }
            .filter { (_, layout) -> layout.lineCount <= maxLines }
        val style = fitting.take(WIDOW_STEPS).firstOrNull { (_, layout) -> !layout.hasWidow() }?.first
            ?: fitting.firstOrNull()?.first
            ?: fittedStyle(sizes.last(), weight)
        Text(text = text, color = color, style = style)
    }
}

private fun TextLayoutResult.hasWidow(): Boolean {
    if (lineCount < 2) return false
    val widths = (0 until lineCount).map { getLineRight(it) - getLineLeft(it) }
    val widest = widths.max()
    return widest > 0f && widths.last() / widest < MIN_LAST_LINE
}
