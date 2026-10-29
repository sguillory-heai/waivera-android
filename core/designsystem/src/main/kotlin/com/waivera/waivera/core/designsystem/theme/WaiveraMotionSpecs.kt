package com.waivera.waivera.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
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
 * The way back out of a press after the finger lifts (M1-03): bouncier and softer than
 * [WaiveraMotionScheme.press], so a 0.04 dip springs back to about 1.004 and settles in about
 * 230 ms. It is felt as a bounce rather than seen as one. The press spring stays the way in.
 * Reduced motion snaps.
 */
fun <T> WaiveraMotionScheme.pressRelease(): FiniteAnimationSpec<T> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.PRESS_RELEASE_DAMPING,
        stiffness = WaiveraMotionScheme.PRESS_RELEASE_STIFFNESS,
    )
}

/**
 * The way back out of a press that a scroll, a swipe or leaving the control took (M1-05): no
 * minimum hold and no overshoot, back in about 60 ms, so a row never bounces while it scrolls.
 * Reduced motion snaps.
 */
fun <T> WaiveraMotionScheme.pressCancel(): FiniteAnimationSpec<T> = if (reduced) {
    snap()
} else {
    spring(
        dampingRatio = WaiveraMotionScheme.EFFECTS_DAMPING,
        stiffness = WaiveraMotionScheme.PRESS_CANCEL_STIFFNESS,
    )
}

/**
 * A held press sinking further while its long press arms, over [durationMillis] (the rest of
 * the system long-press timeout), eased so it starts gently and lands as the menu opens (M1-02).
 * Reduced motion snaps; nothing scales then anyway.
 */
fun WaiveraMotionScheme.longPressSink(durationMillis: Int): FiniteAnimationSpec<Float> =
    if (reduced) snap() else tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)

/**
 * Content leaving so other content can take its place: faster than anything arriving, so the
 * two never sit half and half (M1-01, M1-11, M1-14, M1-16). Reduced motion is the short fade.
 */
fun <T> WaiveraMotionScheme.exit(): FiniteAnimationSpec<T> =
    if (reduced) reducedFadeSpec() else tween(durationMillis = WaiveraMotionScheme.EXIT_MILLIS)

/**
 * Content that waits for the leaving content to clear first, as an empty state after the last
 * card goes: it starts just before [WaiveraMotionScheme.EXIT_MILLIS] is up. Reduced motion is the
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
private const val ENTER_AFTER_EXIT_DELAY_MILLIS = 60
