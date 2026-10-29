package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
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
 */
@Composable
fun WaiveraPopVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(motion.pop, initialScale = WaiveraMotionScheme.POP_START_SCALE) + fadeIn(motion.state()),
        exit = scaleOut(motion.state(), targetScale = WaiveraMotionScheme.POP_START_SCALE) + fadeOut(motion.state()),
        label = "popVisibility",
    ) {
        content()
    }
}

/**
 * Shows and hides a strip (an editing bar, a staged image) by growing and shrinking its height on
 * the layout token, with a fade, so the content around it moves rather than jumps.
 */
@Composable
fun WaiveraExpandVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(motion.layout()) + fadeIn(motion.state()),
        exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
        label = "expandVisibility",
    ) {
        content()
    }
}

/**
 * Swaps content in place with [swapTransition]: "Sending…"
 * to "Sent", one period title to the next. [direction] says which way the change went (above 0
 * forward, below 0 back, 0 neither). Only a new [contentKey] animates; other changes recompose
 * the content where it is.
 */
@Composable
fun <T> WaiveraSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    direction: (from: T, to: T) -> Int = { _, _ -> 0 },
    content: @Composable (T) -> Unit,
) {
    val motion = WaiveraTheme.motion
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { motion.swapTransition(direction(initialState, targetState)) },
        contentAlignment = contentAlignment,
        label = "swap",
        contentKey = contentKey,
    ) { state ->
        content(state)
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
