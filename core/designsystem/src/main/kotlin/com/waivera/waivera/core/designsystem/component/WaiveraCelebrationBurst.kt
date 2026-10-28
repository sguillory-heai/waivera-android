package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The celebrate moment: a one-shot burst of brand-gradient confetti that rises from the top
 * centre of its bounds, spreads and falls away on the [celebrateBurst][com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme.celebrateBurst]
 * timeline. Lay it over the thing being celebrated (it never takes touches or focus).
 *
 * It plays once per [trigger] value, and does not replay when a list scrolls it back into view
 * or the screen is recreated; pass something that changes only when there is something new to
 * celebrate (for example the people celebrated today). With reduced motion it draws nothing.
 */
@Composable
fun WaiveraCelebrationBurst(trigger: Any?, modifier: Modifier = Modifier, seed: Int = 0) {
    val motion = WaiveraTheme.motion
    if (motion.reduced || trigger == null) return
    val pieces = remember(seed) { Confetti.pieces(seed) }
    var played by rememberSaveable(trigger.hashCode()) { mutableStateOf(false) }
    val progress = remember(trigger) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(trigger) {
        if (!played) {
            played = true
            progress.animateTo(1f, motion.celebrateBurst)
        }
    }
    val accent = WaiveraTheme.colors.accent
    val palette = remember(accent) { WaiveraBrand.Colors + accent }
    Canvas(modifier = modifier.fillMaxSize()) {
        val now = progress.value
        if (!Confetti.isVisible(reduced = false, progress = now)) return@Canvas
        val origin = Offset(size.width / 2f, size.height * Confetti.ORIGIN_HEIGHT_FRACTION)
        val radius = min(size.width, size.height * 2f) / 2f
        pieces.forEach { piece ->
            val color = palette[piece.colorIndex % palette.size]
            drawPiece(
                piece = piece,
                frame = Confetti.frame(piece, now),
                center = origin,
                radius = radius,
                color = color,
            )
        }
    }
}

/** Draws one piece at [frame], measured in burst radii ([radius]) from the burst's [center]. */
private fun DrawScope.drawPiece(
    piece: ConfettiPiece,
    frame: ConfettiFrame,
    center: Offset,
    radius: Float,
    color: Color,
) {
    if (frame.alpha <= 0f) return
    val pieceSize = WaiveraSizes.ConfettiPiece.toPx()
    val at = center + Offset(frame.x * radius, frame.y * radius)
    val tinted = color.copy(alpha = frame.alpha)
    if (piece.round) {
        drawCircle(color = tinted, radius = pieceSize / 2f, center = at)
    } else {
        rotate(degrees = frame.rotation, pivot = at) {
            val pieceShape = Size(pieceSize, pieceSize * Confetti.STRIP_ASPECT)
            drawRect(
                color = tinted,
                topLeft = at - Offset(pieceShape.width / 2f, pieceShape.height / 2f),
                size = pieceShape,
            )
        }
    }
}

/** One confetti piece's fixed character: where it heads, how far, how it spins and its colour. */
@Immutable
internal data class ConfettiPiece(
    /** Launch direction in radians; 0 points right, -π/2 straight up. */
    val angle: Float,
    /** How far it travels, as a share of the burst radius. */
    val reach: Float,
    /** Turns over the whole burst; negative spins the other way. */
    val spin: Float,
    val colorIndex: Int,
    /** Share of the timeline before this piece launches, so the burst ripples instead of popping. */
    val delay: Float,
    /** Round dots and paper strips mix, for a confetti look rather than a particle effect. */
    val round: Boolean,
)

/** Where a piece is at one moment, relative to the burst origin, in burst radii. */
@Immutable
internal data class ConfettiFrame(val x: Float, val y: Float, val rotation: Float, val alpha: Float)

/** Pure confetti physics, so the burst is deterministic in screenshots and unit tested. */
internal object Confetti {
    const val PIECES = 36
    const val ORIGIN_HEIGHT_FRACTION = 0.35f
    const val STRIP_ASPECT = 0.45f
    private const val MAX_DELAY = 0.15f
    private const val MIN_REACH = 0.45f
    private const val REACH_RANGE = 0.55f
    private const val MAX_SPIN = 3f
    private const val GRAVITY = 0.9f
    private const val FADE_FROM = 0.65f
    private const val FULL_TURN = 360f

    /** A spread of directions, mostly upward, so pieces rise before they fall. */
    private const val SPREAD = 2.4f
    private const val UP = -1.5707964f

    fun pieces(seed: Int, count: Int = PIECES): List<ConfettiPiece> {
        val random = Random(seed)
        return List(count) { index ->
            ConfettiPiece(
                angle = UP + (random.nextFloat() - 0.5f) * 2f * SPREAD,
                reach = MIN_REACH + random.nextFloat() * REACH_RANGE,
                spin = (random.nextFloat() - 0.5f) * 2f * MAX_SPIN,
                colorIndex = index,
                delay = random.nextFloat() * MAX_DELAY,
                round = index % 3 == 0,
            )
        }
    }

    /** Nothing is drawn before launch, after the end, or ever with reduced motion. */
    fun isVisible(reduced: Boolean, progress: Float): Boolean = !reduced && progress > 0f && progress < 1f

    fun frame(piece: ConfettiPiece, progress: Float): ConfettiFrame {
        val local = ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
        if (local <= 0f) return ConfettiFrame(0f, 0f, 0f, 0f)
        // Ease out: fast from the origin, slowing as it spreads; gravity then pulls it down.
        val travel = 1f - (1f - local) * (1f - local)
        val x = cos(piece.angle) * piece.reach * travel
        val y = sin(piece.angle) * piece.reach * travel + GRAVITY * local * local
        val alpha = if (local < FADE_FROM) 1f else ((1f - local) / (1f - FADE_FROM)).coerceIn(0f, 1f)
        return ConfettiFrame(x = x, y = y, rotation = piece.spin * FULL_TURN * local, alpha = alpha)
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraCelebrationBurstPreview() {
    WaiveraTheme {
        Box(
            Modifier
                .height(WaiveraSizes.IllustrationReward)
                .background(WaiveraTheme.colors.background),
        ) {
            WaiveraCelebrationBurst(trigger = Unit)
        }
    }
}
