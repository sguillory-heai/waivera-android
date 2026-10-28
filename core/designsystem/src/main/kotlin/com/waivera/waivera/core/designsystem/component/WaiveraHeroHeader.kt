package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraHeroColors
import com.waivera.waivera.core.designsystem.theme.WaiveraHeroGeometry
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Where the light trails traced from the logo's looped "W" lie on a hero. Placements are the
 * concept's, on its 390-wide frame, and scale with the window width.
 */
enum class WaiveraHeroTrail {
    /** Sign-in and the gates: two strong strands crossing behind the lockup. */
    SignIn,

    /** A tab screen's hero: one faint loop on the end side, behind the greeting and the card. */
    Tab,

    /** The all-caught-up hero: one faint loop framing the big ring. */
    Reward,

    /** No trails. */
    None,
}

/**
 * The concept's plum hero: the 165° plum-to-violet gradient, a pink glow in the top-end corner, a
 * violet glow in the bottom-start corner, and the light trails of [trail] at low opacity. It
 * draws behind the status bar; the caller pads its content for the system bars. Everything here
 * is decoration: the content above it carries the meaning.
 */
@Composable
fun WaiveraHeroBackdrop(
    modifier: Modifier = Modifier,
    trail: WaiveraHeroTrail = WaiveraHeroTrail.Tab,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val hero = WaiveraHero.colors
    Box(
        modifier = modifier
            .clipToBounds()
            .drawWithCache {
                val header = hero.headerBrush(size)
                val frame = size.width / WaiveraHero.FrameWidth.toPx()
                onDrawBehind {
                    drawRect(brush = header)
                    drawGlows(hero, frame)
                    trailSpec(trail)?.let { drawTrail(it, frame) }
                }
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * The sign-in header: [WaiveraHeroBackdrop] with the sign-in trails and its content centred.
 * [content] is usually [WaiveraHeroLogo].
 */
@Composable
fun WaiveraHeroHeader(
    modifier: Modifier = Modifier,
    trail: WaiveraHeroTrail = WaiveraHeroTrail.SignIn,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    WaiveraHeroBackdrop(
        modifier = modifier.fillMaxWidth(),
        trail = trail,
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * The real logo drawn light-on-dark for the hero: the looped "W" keeps its brand gradient, and
 * the base lines and wordmark take the hero's on-header colour instead of the page text colour
 * (which is near-black in light mode and would vanish on the plum).
 */
@Composable
fun WaiveraHeroLogo(
    height: Dp,
    modifier: Modifier = Modifier,
    variant: WaiveraLogoVariant = WaiveraLogoVariant.Lockup,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .height(height)
            .aspectRatio(variant.aspectRatio)
            .then(semantics),
    ) {
        Image(
            painter = painterResource(variant.gradientLayer),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(variant.inkLayer),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(WaiveraHero.colors.onHeader),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The two glows: 360 and 380 wide circles, fading out at 65% of their radius, as in the concept. */
private fun DrawScope.drawGlows(hero: WaiveraHeroColors, frame: Float) {
    val unit = frame * density
    val pinkRadius = WaiveraHeroGeometry.PINK_RADIUS * unit
    val pinkCenter = Offset(
        x = size.width - WaiveraHeroGeometry.PINK_END_INSET * unit,
        y = WaiveraHeroGeometry.PINK_TOP * unit,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to hero.glowPink,
            WaiveraHeroGeometry.GLOW_FADE to hero.glowPink.copy(alpha = 0f),
            center = pinkCenter,
            radius = pinkRadius,
        ),
        radius = pinkRadius,
        center = pinkCenter,
    )
    val violetRadius = WaiveraHeroGeometry.VIOLET_RADIUS * unit
    val violetCenter = Offset(
        x = WaiveraHeroGeometry.VIOLET_START * unit,
        y = size.height + WaiveraHeroGeometry.VIOLET_BELOW * unit,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to hero.glowViolet,
            WaiveraHeroGeometry.GLOW_FADE to hero.glowViolet.copy(alpha = 0f),
            center = violetCenter,
            radius = violetRadius,
        ),
        radius = violetRadius,
        center = violetCenter,
    )
}

/**
 * One placement of the trail: an SVG box ([left], [top], [width], [height] on the 390 frame) that
 * the 35 × 26 trail viewport is fitted into, the stroke width in viewport units, the opacity, and
 * whether the thin second strand runs beside the main one.
 */
internal data class TrailSpec(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val stroke: Float,
    val alpha: Float,
    val strand: Boolean,
)

internal fun trailSpec(trail: WaiveraHeroTrail): TrailSpec? = when (trail) {
    WaiveraHeroTrail.SignIn -> TrailSpec(
        left = -20f,
        top = -150f,
        width = 680f,
        height = 505f,
        stroke = 0.42f,
        alpha = 0.55f,
        strand = true,
    )
    WaiveraHeroTrail.Tab -> TrailSpec(
        left = 120f,
        top = 40f,
        width = 420f,
        height = 320f,
        stroke = 1.3f,
        alpha = 0.16f,
        strand = false,
    )
    WaiveraHeroTrail.Reward -> TrailSpec(
        left = -40f,
        top = -170f,
        width = 680f,
        height = 505f,
        stroke = 1.2f,
        alpha = 0.16f,
        strand = false,
    )
    WaiveraHeroTrail.None -> null
}

/** Fits the 35 × 26 viewport into the spec's box ("xMidYMid meet") and strokes the trail with the brand gradient. */
private fun DrawScope.drawTrail(spec: TrailSpec, frame: Float) {
    val unit = frame * density
    val boxWidth = spec.width * unit
    val boxHeight = spec.height * unit
    val scale = minOf(boxWidth / TRAIL_VIEW_WIDTH, boxHeight / TRAIL_VIEW_HEIGHT)
    val left = spec.left * unit + (boxWidth - TRAIL_VIEW_WIDTH * scale) / 2f
    val top = spec.top * unit + (boxHeight - TRAIL_VIEW_HEIGHT * scale) / 2f
    val brush = Brush.horizontalGradient(
        0f to WaiveraBrand.Violet,
        TRAIL_PINK_STOP to WaiveraBrand.Pink,
        1f to WaiveraBrand.Gold,
        startX = TRAIL_START_X,
        endX = TRAIL_END_X,
    )
    translate(left = left, top = top) {
        withTransform({ scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) }) {
            drawPath(
                path = TrailPaths.main,
                brush = brush,
                alpha = spec.alpha,
                style = Stroke(width = spec.stroke, cap = StrokeCap.Round),
            )
            if (spec.strand) {
                drawPath(
                    path = TrailPaths.strand,
                    brush = brush,
                    alpha = spec.alpha * STRAND_ALPHA,
                    style = Stroke(width = STRAND_STROKE, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** The trail paths, traced from the logo's loop and extended off both edges (`concepts/src/A.html`). */
private object TrailPaths {
    val main: Path by lazy {
        PathParser().parsePathString(
            "M-2 9.2C-2 9.2 3.9 23.7 10.25 23.47C14.58 23.24 22.76 10.47 21.56 5.5C20.36 .54 15.3 .54 " +
                "14.34 5.5C13.38 10.47 22.28 23.71 25.17 23.47C28.05 23.24 33.5 16.9 38 9.2",
        ).toPath()
    }
    val strand: Path by lazy {
        PathParser().parsePathString("M-2 10.4C-2 10.4 4.2 24.6 10.4 24.3C15 24 23.4 10.9 22.3 5.4").toPath()
    }
}

private const val TRAIL_VIEW_WIDTH = 35f
private const val TRAIL_VIEW_HEIGHT = 26f
private const val TRAIL_START_X = -2f
private const val TRAIL_END_X = 38f
private const val TRAIL_PINK_STOP = 0.55f
private const val STRAND_STROKE = 0.18f
private const val STRAND_ALPHA = 0.6f

@WaiveraPreviews
@Composable
private fun WaiveraHeroHeaderPreview() {
    WaiveraTheme {
        WaiveraHeroHeader(modifier = Modifier.height(WaiveraHero.HeaderHeight + WaiveraHero.SheetOverlap)) {
            WaiveraHeroLogo(height = WaiveraHero.Lockup)
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraHeroBackdropTabPreview() {
    WaiveraTheme {
        WaiveraHeroBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaiveraHero.HeaderHeight),
            trail = WaiveraHeroTrail.Tab,
        )
    }
}
