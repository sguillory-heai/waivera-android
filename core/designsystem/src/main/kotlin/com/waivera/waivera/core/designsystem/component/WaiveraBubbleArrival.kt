package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlin.math.roundToInt

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

/**
 * The share of its height a row that lands with your own send has grown to (M2-01), or null for
 * a row that does not grow. A send used to insert its whole row (bubble, footer and any "Today"
 * line) in one frame, and every older message jumped up by that height at once. Rows that land
 * together grow from 0 to their full height on the layout token instead, so the conversation is
 * pushed up smoothly while the bubble pops. Whether a row grows is decided on its first
 * composition and kept, so the growth is never cut off; reduced motion never grows. Draw it with
 * [landingGrow].
 */
@Composable
fun rememberLandingGrow(grows: Boolean): State<Float>? {
    val motion = WaiveraTheme.motion
    val height = remember { if (grows && !motion.reduced) Animatable(0f) else null } ?: return null
    LaunchedEffect(height) { height.animateTo(1f, motion.landing()) }
    return height.asState()
}

/**
 * The layout spring, ending within [LANDING_VISIBILITY_THRESHOLD] of the row's height (under half
 * a pixel on a 200 px landing) instead of snapping the last 1%. Reduced motion snaps, though a
 * row never grows then.
 */
private fun WaiveraMotionScheme.landing(): FiniteAnimationSpec<Float> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.SPATIAL_DAMPING,
        stiffness = WaiveraMotionScheme.DEFAULT_SPATIAL_STIFFNESS,
        visibilityThreshold = LANDING_VISIBILITY_THRESHOLD,
    )
}

private const val LANDING_VISIBILITY_THRESHOLD = 0.002f

/**
 * Lays the row out [height] of its full height, its top in place, so the part not grown yet waits
 * below the slot, under the composer's edge, and rises into view. The row is masked to its slot
 * only while it grows, so nothing it draws at rest is cut. The value is read in the layout and
 * layer blocks: the growth re-measures this one row each frame and never recomposes it.
 */
fun Modifier.landingGrow(height: State<Float>): Modifier = this
    .graphicsLayer { clip = height.value < 1f }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shown = (placeable.height * height.value.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, shown) { placeable.place(0, 0) }
    }
