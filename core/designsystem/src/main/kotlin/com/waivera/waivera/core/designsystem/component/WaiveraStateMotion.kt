package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.waivera.waivera.core.designsystem.theme.SwapAxis
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.loadTransition
import com.waivera.waivera.core.designsystem.theme.swapEnterFade
import com.waivera.waivera.core.designsystem.theme.swapExitFade
import com.waivera.waivera.core.designsystem.theme.swapTransition

/**
 * A pop each time [key] changes after the first composition: the scale jumps to [from] and
 * springs back to 1 on the shared [pop][WaiveraMotionScheme.pop] token, so a tab that was just
 * picked, a count that moved or a check that appeared answers with a little bounce. Nothing pops
 * when the thing first appears or scrolls back into a list, and nothing moves under reduced
 * motion. [enabled] is read when the key changes, so a pop can be kept for one direction only
 * (selected, not deselected). Draw it with [popScale].
 */
@Composable
fun rememberPopScale(
    key: Any?,
    enabled: Boolean = true,
    from: Float = WaiveraMotionScheme.POP_START_SCALE,
): State<Float> {
    val motion = WaiveraTheme.motion
    val scale = remember { Animatable(1f) }
    val last = remember { LastKey(key) }
    val popNow by rememberUpdatedState(enabled)
    LaunchedEffect(key) {
        if (last.value == key) return@LaunchedEffect
        last.value = key
        if (!popNow || motion.reduced) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(from)
        scale.animateTo(1f, motion.pop)
    }
    return scale.asState()
}

private class LastKey(var value: Any?)

/**
 * A wrong sign-in's shake: each time [key] changes after the first composition, the value runs
 * the shared [rejectShake][WaiveraMotionScheme.rejectShake] keyframes once, about 300 ms, and
 * comes to rest at 0. Bump [key] once per rejection. Under reduced motion ("Remove animations")
 * nothing moves and the error state alone answers. Draw it with [rejectShake].
 */
@Composable
fun rememberRejectShake(key: Int): State<Float> {
    val motion = WaiveraTheme.motion
    val offset = remember { Animatable(0f) }
    val last = remember { LastKey(key) }
    LaunchedEffect(key) {
        if (last.value == key) return@LaunchedEffect
        last.value = key
        offset.snapTo(0f)
        if (!motion.reduced) offset.animateTo(0f, motion.rejectShake)
    }
    return offset.asState()
}

/** Draws a [rememberRejectShake] value as a sideways move of up to [WaiveraSpacing.ShakeAmplitude]. */
fun Modifier.rejectShake(offset: State<Float>): Modifier = graphicsLayer {
    translationX = offset.value * WaiveraSpacing.ShakeAmplitude.toPx()
}

/** Draws a [rememberPopScale] value; read in the layer, so the pop never recomposes. */
fun Modifier.popScale(scale: State<Float>): Modifier = graphicsLayer {
    val value = scale.value
    scaleX = value
    scaleY = value
}

/**
 * Shows and hides a small mark (an unread dot, a count, a check) with a pop in and a quick shrink
 * out, so a state that ends visibly goes rather than vanishing. A mark already showing on first
 * composition is simply there. The content stays composed while it leaves, so keep what it shows
 * (see [rememberLatestNonNull]).
 *
 * [swapsInPlace] is for a mark that takes another's place in the same spot, as a busy button's
 * spinner takes its label's (M2-02): its fade waits for the leaving side ([swapEnterFade]), and it
 * leaves on the quick [swapExitFade] without shrinking, so the two never show at half strength.
 */
@Composable
fun WaiveraPopVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    swapsInPlace: Boolean = false,
    content: @Composable () -> Unit,
) {
    val motion = WaiveraTheme.motion
    val pop = scaleIn(motion.pop, initialScale = WaiveraMotionScheme.POP_START_SCALE)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (swapsInPlace) pop + fadeIn(motion.swapEnterFade()) else pop + fadeIn(motion.state()),
        exit = if (swapsInPlace) {
            fadeOut(motion.swapExitFade())
        } else {
            scaleOut(motion.state(), targetScale = WaiveraMotionScheme.POP_START_SCALE) + fadeOut(motion.state())
        },
        label = "popVisibility",
    ) {
        content()
    }
}

/** Which way a [WaiveraExpandVisibility] grows: a strip's height, or an inline slot's width. */
enum class ExpandAxis { Vertical, Horizontal }

/**
 * Shows and hides a strip (an editing bar, a staged image, a timestamp line) by growing and
 * shrinking its height on the layout token, with a fade, so the content around it moves rather
 * than jumps. [axis] Horizontal grows the width instead, for an inline slot such as a chip's check
 * or a status beside a title, so its neighbours slide over rather than jump. Under reduced
 * motion the size snaps and only the fade remains.
 */
@Composable
fun WaiveraExpandVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    axis: ExpandAxis = ExpandAxis.Vertical,
    content: @Composable () -> Unit,
) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = motion.expandEnter(axis),
        exit = motion.expandExit(axis),
        label = "expandVisibility",
    ) {
        content()
    }
}

/**
 * [WaiveraExpandVisibility] driven by [visibleState], for a caller that needs to know when the
 * leaving content has finished collapsing (`currentState` false and `isIdle`).
 */
@Composable
fun WaiveraExpandVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    axis: ExpandAxis = ExpandAxis.Vertical,
    content: @Composable () -> Unit,
) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = motion.expandEnter(axis),
        exit = motion.expandExit(axis),
        label = "expandVisibility",
    ) {
        content()
    }
}

private fun WaiveraMotionScheme.expandEnter(axis: ExpandAxis): EnterTransition {
    val grow = when (axis) {
        ExpandAxis.Vertical -> expandVertically(layout())
        ExpandAxis.Horizontal -> expandHorizontally(layout())
    }
    return grow + fadeIn(state())
}

private fun WaiveraMotionScheme.expandExit(axis: ExpandAxis): ExitTransition {
    val shrink = when (axis) {
        ExpandAxis.Vertical -> shrinkVertically(layout())
        ExpandAxis.Horizontal -> shrinkHorizontally(layout())
    }
    return shrink + fadeOut(state())
}

/**
 * Swaps content in place with [swapTransition]: "Sending…"
 * to "Sent", one period title to the next. [direction] says which way the change went (above 0
 * forward, below 0 back, 0 neither), and [axis] whether a directional change slides across (a
 * period step) or rolls up and down (a count). Only a new [contentKey] animates; other changes
 * recompose the content where it is.
 */
@Composable
fun <T> WaiveraSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    direction: (from: T, to: T) -> Int = { _, _ -> 0 },
    axis: SwapAxis = SwapAxis.Horizontal,
    content: @Composable (T) -> Unit,
) {
    val motion = WaiveraTheme.motion
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { motion.swapTransition(direction(initialState, targetState), axis) },
        contentAlignment = contentAlignment,
        label = "swap",
        contentKey = contentKey,
    ) { state ->
        SwapSide(motion) { content(state) }
    }
}

/**
 * One side of a swap, which leaves on [swapExitFade] even when it was still arriving (M3-02).
 * Compose fades a side that is interrupted mid-entrance on its default spring instead of the
 * transition's exit, so a swap changed again within about 300 ms drew the old line under the new
 * one ("Synced" over "Sending…"). This layer runs beside the built-in fade: it stays at 1 while
 * the side arrives and shows, and drops on the quick exit as the side leaves, however far its
 * arrival got. A settled side's own exit is the same tween, so its fade-out only ends a little
 * sooner. The value is read in the layer, so a swap never recomposes for it.
 */
@Composable
private fun AnimatedVisibilityScope.SwapSide(motion: WaiveraMotionScheme, content: @Composable () -> Unit) {
    val leaving = transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.PostExit) motion.swapExitFade() else snap() },
        label = "swapSide",
    ) { state -> if (state == EnterExitState.PostExit) 0f else 1f }
    Box(Modifier.graphicsLayer { alpha = leaving.value }, propagateMinConstraints = true) { content() }
}

/**
 * Swaps a loading skeleton for the content it stands in for, and back (M1-16): the skeleton drops
 * off first and the content fades in as it clears ([loadTransition]), with no slide, so loaded
 * content settles in place instead of cutting. [loading] decides which side shows; with
 * animations off the old side goes at once and the new one fades in. Content inside keeps its own
 * keys and state across recompositions of one side.
 */
@Composable
fun WaiveraLoadSwap(
    loading: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (loading: Boolean) -> Unit,
) {
    val motion = WaiveraTheme.motion
    AnimatedContent(
        targetState = loading,
        modifier = modifier,
        transitionSpec = { motion.loadTransition() },
        contentAlignment = contentAlignment,
        label = "loadSwap",
    ) { showsSkeleton ->
        SwapSide(motion) { content(showsSkeleton) }
    }
}

/**
 * The last non-null [value] seen, so content that animates out (a count going to zero, a staged
 * image being removed) can keep showing what it showed.
 */
@Composable
fun <T : Any> rememberLatestNonNull(value: T?): T? {
    val latest = remember { Latest<T>() }
    if (value != null) latest.value = value
    return latest.value
}

private class Latest<T : Any> {
    var value: T? = null
}
