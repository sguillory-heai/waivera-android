package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraPalette
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** The share of a ring to fill for [done] of [total]: 0 when there is nothing, never past full. */
fun ringFraction(done: Int, total: Int): Float = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

/**
 * The hero's progress ring: a faint track with a solid lilac-violet arc for [done] of [total]
 * from twelve o'clock (v5 rule A: nothing drawn on the hero is a gradient), the count in the
 * middle ("5/9" over [caption]). The arc grows from empty on first show and follows changes;
 * under reduced motion it simply shows the value.
 *
 * TalkBack reads it once, as [contentDescription] (for example "5 of 9 handled today") with the
 * progress, never the separate numbers. The count inside keeps its size up to 130% font: the
 * ring is a fixed graphic, and the same numbers are in the text beside it at any size.
 *
 * [compact] is the small ring on a short window: its caption is set smaller and kept to one
 * line within [ringCaptionMaxWidth], so it never touches the arc (UI10-06).
 */
@Composable
fun WaiveraProgressRing(
    done: Int,
    total: Int,
    caption: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = WaiveraSizes.Ring,
    stroke: Dp = WaiveraSizes.RingStroke,
    compact: Boolean = false,
) {
    val target = ringFraction(done, total)
    val progress = rememberRingProgress(target)
    val hero = WaiveraHero.colors
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(current = target, range = 0f..1f)
            }
            .drawBehind {
                val width = stroke.toPx()
                drawRing(track = hero.ringTrack, arc = hero.ringArc, progress = progress.value, strokeWidth = width)
            },
        contentAlignment = Alignment.Center,
    ) {
        CappedFontScale {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = done.toString(), style = WaiveraTextStyles.RingNumeral, color = hero.onHeader)
                    Text(
                        text = "/$total",
                        style = WaiveraTextStyles.RingDenominator,
                        color = hero.onHeaderSecondary,
                        modifier = Modifier.padding(bottom = WaiveraSpacing.XxSmall),
                    )
                }
                Text(
                    text = caption,
                    style = if (compact) WaiveraTextStyles.RingCaptionCompact else WaiveraTextStyles.RingCaption,
                    color = hero.onHeaderSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = ringCaptionMaxWidth(size, stroke)),
                )
            }
        }
    }
}

/**
 * All caught up: the big full ring, in the same solid arc as the progress ring, on a soft lilac
 * halo, a white check in the middle and two small sparkles in gold and pink. The halo is one hue
 * fading out, the same light as the hero's own glows, not the brand gradient (v5 rule A). The
 * ring closes and the check appears on first show; under reduced motion both are simply there.
 * Decorative apart from [contentDescription].
 */
@Composable
fun WaiveraRewardRing(contentDescription: String?, modifier: Modifier = Modifier) {
    val progress = rememberRingProgress(target = 1f)
    val hero = WaiveraHero.colors
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(
        modifier = modifier
            .size(WaiveraSizes.RewardRing + WaiveraSizes.RewardHalo * 2)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to hero.ringArc.copy(alpha = HALO_ALPHA),
                    HALO_FADE to hero.ringArc.copy(alpha = 0f),
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(WaiveraSizes.RewardRing)
                .drawBehind {
                    drawRing(
                        track = hero.ringTrack,
                        arc = hero.ringArc,
                        progress = progress.value,
                        strokeWidth = WaiveraSizes.RewardRingStroke.toPx(),
                    )
                    drawCheck(progress = progress.value, color = hero.onHeader)
                },
        )
        Sparkle(modifier = Modifier.align(Alignment.TopEnd), large = true)
        Sparkle(modifier = Modifier.align(Alignment.BottomStart), large = false)
    }
}

/** A four-point sparkle: gold when [large], pink otherwise. */
@Composable
private fun Sparkle(modifier: Modifier, large: Boolean) {
    val color = if (large) WaiveraBrand.Gold else WaiveraPalette.SparklePink
    val size = if (large) SPARKLE_LARGE else SPARKLE_SMALL
    Canvas(
        modifier = modifier
            .padding(WaiveraSizes.RewardHalo / 2)
            .size(WaiveraSizes.IconSmall * size),
    ) {
        val c = center
        val r = this.size.minDimension / 2
        val waist = r * SPARKLE_WAIST
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticTo(c.x + waist, c.y - waist, c.x + r, c.y)
            quadraticTo(c.x + waist, c.y + waist, c.x, c.y + r)
            quadraticTo(c.x - waist, c.y + waist, c.x - r, c.y)
            quadraticTo(c.x - waist, c.y - waist, c.x, c.y - r)
            close()
        }
        drawPath(path = path, color = color)
    }
}

/** The ring's arc, animated toward [target] from empty, or set directly under reduced motion. */
@Composable
private fun rememberRingProgress(target: Float): Animatable<Float, AnimationVector1D> {
    val motion = WaiveraTheme.motion
    val progress = remember { Animatable(if (motion.reduced) target else 0f) }
    LaunchedEffect(target, motion) {
        progress.animateTo(targetValue = target, animationSpec = if (motion.reduced) snap() else motion.celebrate())
    }
    return progress
}

/** Track and solid arc, inset by half the stroke so the ring fits its box. */
private fun DrawScope.drawRing(track: Color, arc: Color, progress: Float, strokeWidth: Float) {
    val inset = strokeWidth / 2
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(inset, inset)
    drawArc(
        color = track,
        startAngle = 0f,
        sweepAngle = FULL_TURN,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth),
    )
    if (progress <= 0f) return
    drawArc(
        color = arc,
        startAngle = TWELVE_O_CLOCK,
        sweepAngle = FULL_TURN * progress,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

/** The reward's check, drawn in once the ring is most of the way round. */
private fun DrawScope.drawCheck(progress: Float, color: Color) {
    val reveal = ((progress - CHECK_FROM) / (1f - CHECK_FROM)).coerceIn(0f, 1f)
    if (reveal <= 0f) return
    val box = WaiveraSizes.RewardCheck.toPx()
    val left = center.x - box / 2
    val top = center.y - box / 2
    val path = Path().apply {
        moveTo(left + box * CHECK_START_X, top + box * CHECK_START_Y)
        lineTo(left + box * CHECK_KNEE_X, top + box * CHECK_KNEE_Y)
        lineTo(left + box * CHECK_END_X, top + box * CHECK_END_Y)
    }
    scale(scale = CHECK_MIN_SCALE + (1f - CHECK_MIN_SCALE) * reveal, pivot = center) {
        drawPath(
            path = path,
            color = color.copy(alpha = reveal),
            style = Stroke(width = CHECK_STROKE * box / CHECK_VIEWPORT, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * The widest the caption may set inside a ring of [size] with [stroke]: the inner diameter less a
 * 4dp clearance from the track on each side.
 */
internal fun ringCaptionMaxWidth(size: Dp, stroke: Dp): Dp = size - stroke * 2 - WaiveraSpacing.Small

/** Keeps the ring's count at its size up to [RING_MAX_FONT_SCALE]: the ring itself does not grow. */
@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val capped = minOf(density.fontScale, RING_MAX_FONT_SCALE)
    CompositionLocalProvider(LocalDensity provides Density(density.density, capped), content = content)
}

internal const val RING_MAX_FONT_SCALE = 1.3f
private const val FULL_TURN = 360f
private const val TWELVE_O_CLOCK = -90f
private const val HALO_ALPHA = 0.3f
private const val HALO_FADE = 0.68f
private const val CHECK_FROM = 0.7f
private const val CHECK_MIN_SCALE = 0.6f
private const val CHECK_VIEWPORT = 24f
private const val CHECK_STROKE = 3f
private const val CHECK_START_X = 0.2f
private const val CHECK_START_Y = 0.52f
private const val CHECK_KNEE_X = 0.41f
private const val CHECK_KNEE_Y = 0.72f
private const val CHECK_END_X = 0.8f
private const val CHECK_END_Y = 0.3f
private const val SPARKLE_LARGE = 1.25f
private const val SPARKLE_SMALL = 0.85f
private const val SPARKLE_WAIST = 0.18f

@WaiveraPreviews
@Composable
private fun WaiveraProgressRingPreview() {
    WaiveraTheme {
        WaiveraHeroBackdrop(trail = WaiveraHeroTrail.None) {
            Row(
                modifier = Modifier.padding(WaiveraSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraProgressRing(
                    done = 5,
                    total = 9,
                    caption = "handled",
                    contentDescription = "5 of 9 handled today",
                )
                WaiveraRewardRing(contentDescription = null)
            }
        }
    }
}
