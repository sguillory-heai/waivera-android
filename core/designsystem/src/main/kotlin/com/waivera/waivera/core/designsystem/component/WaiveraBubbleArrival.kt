package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** One frame of a message bubble arriving. [offsetFraction] is a share of the arrival offset. */
@Immutable
data class BubbleArrivalFrame(val scale: Float, val offsetFraction: Float, val alpha: Float)

/**
 * How a new message enters a conversation. Your own send pops up from the composer corner on
 * the quick, slightly springy [sendPop][com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme.sendPop]
 * (a satisfying landing, no vibration); someone else's glides up and fades in on the layout
 * token. History and reduced motion arrive at rest.
 */
object BubbleArrival {
    /** Own sends start a little small so the pop reads; small enough to stay inside a tap. */
    const val SEND_START_SCALE = 0.82f
    const val MIN_ALPHA = 0.35f

    /** The spring may overshoot 1 for a moment; the bubble then lifts a hair past rest and settles. */
    private const val MAX_OVERSHOOT = 0.15f

    fun frame(progress: Float, mine: Boolean): BubbleArrivalFrame {
        val settled = progress.coerceIn(0f, 1f + MAX_OVERSHOOT)
        val scale = if (mine) SEND_START_SCALE + (1f - SEND_START_SCALE) * settled else 1f
        val alpha = (MIN_ALPHA + (1f - MIN_ALPHA) * settled).coerceIn(0f, 1f)
        return BubbleArrivalFrame(scale = scale, offsetFraction = 1f - settled, alpha = alpha)
    }
}

/**
 * 0 → 1 for a live arrival (your send on `sendPop`, others on `layout`), 1 at once for history
 * or with reduced motion. The value can briefly pass 1 on a send; clamp it before using it as
 * an opacity. Returned as state: read it in a layer or draw block only, so an arriving bubble
 * never recomposes per frame (M1-08).
 */
@Composable
fun rememberBubbleArrival(isLiveArrival: Boolean, mine: Boolean): State<Float> {
    val motion = WaiveraTheme.motion
    val progress = remember { Animatable(if (isLiveArrival && !motion.reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, if (mine) motion.sendPop else motion.layout<Float>())
    }
    return progress.asState()
}

/**
 * Draws [progress] as a [BubbleArrival] frame: own bubbles grow from their bottom end corner.
 * The layer is placed from a layout modifier because the graphics layer scope has no layout
 * direction, and the end corner flips in right-to-left layouts. The progress is read in the
 * layer block, so the arrival updates the layer and never re-measures or recomposes the row.
 */
fun Modifier.bubbleArrival(progress: State<Float>, mine: Boolean, offset: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val endX = if (layoutDirection == LayoutDirection.Rtl) 0f else 1f
        layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val frame = BubbleArrival.frame(progress.value, mine)
                transformOrigin = TransformOrigin(pivotFractionX = if (mine) endX else 0f, pivotFractionY = 1f)
                scaleX = frame.scale
                scaleY = frame.scale
                translationY = frame.offsetFraction * offset.toPx()
                alpha = frame.alpha
            }
        }
    }
