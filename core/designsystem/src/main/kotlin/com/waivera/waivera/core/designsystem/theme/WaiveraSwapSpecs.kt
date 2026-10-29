package com.waivera.waivera.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize

// The swap's own spatial specs (M3-02): a swap counts as busy until its pop and its size change
// end, so both stop as soon as the eye can no longer see them move.

/**
 * The small pop an in-place swap's arriving side grows in on (M3-02): the [pop][WaiveraMotionScheme.pop]
 * spring, ending on a coarser threshold, so the arrival is over by about 190 ms, as its fade is.
 * The stand-alone pop's fine threshold ran on for about 290 ms, and a swap changed again in that
 * time still had its last side arriving. Reduced motion snaps, though nothing pops then.
 */
internal fun WaiveraMotionScheme.swapPop(): FiniteAnimationSpec<Float> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.POP_DAMPING,
        stiffness = WaiveraMotionScheme.POP_STIFFNESS,
        visibilityThreshold = SWAP_POP_VISIBILITY_THRESHOLD,
    )
}

/**
 * The swap's size change: the layout spring, ending within a pixel (M3-02). With no threshold a
 * size spring runs to a hundredth of a pixel, about 400 ms, and the swap counted as busy all that
 * time. A snap with animations off.
 */
internal fun WaiveraMotionScheme.swapSize(): FiniteAnimationSpec<IntSize> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.SPATIAL_DAMPING,
        stiffness = WaiveraMotionScheme.DEFAULT_SPATIAL_STIFFNESS,
        visibilityThreshold = IntSize.VisibilityThreshold,
    )
}

private const val SWAP_POP_VISIBILITY_THRESHOLD = 0.005f
