package com.waivera.waivera.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

// Press and hand-over specs added for the motion pass (M1-01 to M1-16). They live beside the
// scheme rather than in it so each stays one small, separately tested rule.

/** Target scale while a control of [depth] is pressed; 1 when animations are off (M1-04). */
fun WaiveraMotionScheme.pressScaleFor(depth: PressDepth): Float = if (reduced) 1f else depth.scale

/** Where a control of [depth] has sunk to when its long press fires; 1 with animations off (M1-02). */
fun WaiveraMotionScheme.longPressScaleFor(depth: PressDepth): Float = if (reduced) 1f else depth.longPressScale

/**
 * The way into a press: the [press][WaiveraMotionScheme.press] spring, ending on a finer
 * [visibilityThreshold] (M2-03). A Row press is 0.015 deep, nearer rest than the default
 * threshold (0.01) allows for, so the spring was cut after about 12 ms and read as a one-frame
 * snap. Reduced motion snaps.
 */
fun <T> WaiveraMotionScheme.pressIn(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.SPATIAL_DAMPING,
        stiffness = WaiveraMotionScheme.FAST_SPATIAL_STIFFNESS,
        visibilityThreshold = visibilityThreshold,
    )
}

/**
 * The way back out of a press after the finger lifts (M1-03): bouncier and softer than
 * [WaiveraMotionScheme.press], so a 0.04 dip springs back to about 1.004 and settles in about
 * 230 ms. It is felt as a bounce rather than seen as one. The press spring stays the way in.
 * Reduced motion snaps. The overshoot is smaller than the default visibility threshold (0.01),
 * which would end the spring before it ever passed rest, so a scale passes a finer
 * [visibilityThreshold].
 */
fun <T> WaiveraMotionScheme.pressRelease(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.PRESS_RELEASE_DAMPING,
        stiffness = WaiveraMotionScheme.PRESS_RELEASE_STIFFNESS,
        visibilityThreshold = visibilityThreshold,
    )
}

/**
 * The way back out of a press that a scroll, a swipe or leaving the control took (M1-05): no
 * minimum hold and no overshoot, back in about 60 ms, so a row never bounces while it scrolls.
 * A scale passes the same fine [visibilityThreshold] as the press, so a shallow press is not
 * cut short on the way out either (M2-03). Reduced motion snaps.
 */
fun <T> WaiveraMotionScheme.pressCancel(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.EFFECTS_DAMPING,
        stiffness = WaiveraMotionScheme.PRESS_CANCEL_STIFFNESS,
        visibilityThreshold = visibilityThreshold,
    )
}

/**
 * A held press sinking further while its long press arms, over [durationMillis] (the rest of
 * the system long-press timeout), at an even pace, so it is still moving when the long press
 * fires and the pop carries that momentum (M1-02, M2-06). An ease-out spent its last third
 * almost still, and the control sat motionless for about 150 ms before the menu opened.
 * Reduced motion snaps; nothing scales then anyway.
 */
fun WaiveraMotionScheme.longPressSink(durationMillis: Int): FiniteAnimationSpec<Float> =
    if (reduced) snap() else tween(durationMillis = durationMillis, easing = LinearEasing)

/**
 * Content leaving so other content can take its place (M1-01, M1-11, M1-14, M1-16, M2-02): it
 * drops off at once, half gone by about 16 ms and under 7% by 40 ms, when anything arriving in
 * its place ([swapEnterFade], [enterAfterExit]) starts to show. Reduced motion is the short
 * fade; a swap's leaving side uses [swapExitFade], which is gone at once instead.
 */
fun <T> WaiveraMotionScheme.exit(): FiniteAnimationSpec<T> = if (reduced) {
    reducedFadeSpec()
} else {
    tween(durationMillis = WaiveraMotionScheme.EXIT_MILLIS, easing = LinearOutSlowInEasing)
}

/**
 * Content that waits for the leaving content to clear first, as an empty state after the last
 * card goes: it starts as [exit] drops below 7%, with [swapEnterFade]. Reduced motion is the
 * short fade, at once.
 */
fun <T> WaiveraMotionScheme.enterAfterExit(): FiniteAnimationSpec<T> = if (reduced) {
    reducedFadeSpec()
} else {
    tween(durationMillis = ENTER_AFTER_EXIT_MILLIS, delayMillis = ENTER_AFTER_EXIT_DELAY_MILLIS)
}

/** The reduced-motion cross-fade, the same as the scheme's own. */
private fun <T> reducedFadeSpec(): FiniteAnimationSpec<T> =
    tween(WaiveraMotionScheme.REDUCED_FADE_MILLIS, easing = LinearEasing)

private const val ENTER_AFTER_EXIT_MILLIS = 120
private const val ENTER_AFTER_EXIT_DELAY_MILLIS = WaiveraMotionScheme.SWAP_ENTER_DELAY_MILLIS
