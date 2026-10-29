package com.waivera.waivera.core.designsystem.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The shared press response: a spring to the press scale that starts on touch-down, not on
 * release. Clickables inside a scrolling list hold back their press by the tap timeout, and a
 * quick tap used to send the press and the release in the same frame, so the control never moved
 * (a dead tap). This follows the finger itself, lets go as soon as a scroll or a swipe takes the
 * gesture, and keeps every press visible for at least [PressTiming.MIN_VISIBLE_PRESS_MILLIS].
 * Keyboard and switch-access presses still arrive through the interaction source. Under reduced
 * motion the press scale is 1, so nothing moves and the ripple or tint alone answers the touch.
 *
 * Use [pressFeedback] on the control, or [pressTracking] on the touch target and [pressScale]
 * on the part that should shrink (a tab's icon rather than the whole tab).
 */
@Stable
class PressFeedbackState internal constructor(
    private val scope: CoroutineScope,
    private val motion: WaiveraMotionScheme,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private val scale = Animatable(1f)
    private val inputs = PressInputs(clock)
    private var pressedAtMillis = 0L
    private var job: Job? = null

    /** The current scale; read it in a graphics layer, never in composition. */
    val value: Float get() = scale.value

    internal fun onPointer(down: Boolean) = follow(inputs.pointer(down))

    internal fun onInteraction(down: Boolean) = follow(inputs.interaction(down))

    private fun follow(pressed: Boolean?) {
        when (pressed) {
            true -> press()
            false -> release()
            null -> Unit
        }
    }

    private fun press() {
        pressedAtMillis = clock()
        job?.cancel()
        job = scope.launch { scale.animateTo(motion.pressScale, motion.press()) }
    }

    private fun release() {
        val wait = PressTiming.releaseDelayMillis(pressedAtMillis, clock())
        // The press keeps animating while it waits; animateTo then takes the value from there.
        job = scope.launch {
            if (wait > 0L) delay(wait)
            scale.animateTo(1f, motion.press())
        }
    }
}

/** Remembers the press response for a control whose clickable uses [interactionSource]. */
@Composable
fun rememberPressFeedback(interactionSource: InteractionSource): PressFeedbackState {
    val motion = WaiveraTheme.motion
    val scope = rememberCoroutineScope()
    val state = remember(scope, motion) { PressFeedbackState(scope, motion) }
    LaunchedEffect(interactionSource, state) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> state.onInteraction(down = true)
                is PressInteraction.Release, is PressInteraction.Cancel -> state.onInteraction(down = false)
            }
        }
    }
    return state
}

/** Touch-down tracking plus the scale, on one control. */
fun Modifier.pressFeedback(state: PressFeedbackState, enabled: Boolean = true): Modifier =
    pressTracking(state, enabled).pressScale(state)

/** Draws [state]'s scale. The value is read in the layer only, so pressing never recomposes. */
fun Modifier.pressScale(state: PressFeedbackState): Modifier = graphicsLayer {
    val scale = state.value
    scaleX = scale
    scaleY = scale
}

/**
 * Watches the finger without consuming anything: the press starts on the first touch, and ends
 * when every finger lifts, the finger leaves the control, or a scroll or swipe consumes the move.
 */
fun Modifier.pressTracking(state: PressFeedbackState, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.onPointer(down = true)
            try {
                var holding = true
                while (holding) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val lifted = event.changes.none { it.pressed }
                    val taken = event.changes.any { change ->
                        change.pressed && !change.changedToDownIgnoreConsumed() && change.isConsumed
                    }
                    val outside = event.changes.any { it.isOutOfBounds(size, extendedTouchPadding) }
                    holding = !lifted && !taken && !outside
                }
            } finally {
                state.onPointer(down = false)
            }
        }
    }
}

/**
 * Merges the finger and the interaction source into one pressed state. A touch reaches both, the
 * source late (held back by the tap timeout inside a list, or sent on release), so a source press
 * during or just after a touch is the same gesture and is ignored. Returns the new pressed state
 * when it changed, or null.
 */
internal class PressInputs(private val clock: () -> Long) {
    private var pointerDown = false
    private var interactionDown = false
    private var pointerEndedAtMillis = Long.MIN_VALUE

    private val pressed: Boolean get() = pointerDown || interactionDown

    fun pointer(down: Boolean): Boolean? {
        val was = pressed
        pointerDown = down
        if (down) interactionDown = false else pointerEndedAtMillis = clock()
        return changed(was)
    }

    fun interaction(down: Boolean): Boolean? {
        val was = pressed
        if (down && (pointerDown || pointerJustEnded())) return null
        interactionDown = down
        return changed(was)
    }

    private fun pointerJustEnded(): Boolean =
        pointerEndedAtMillis != Long.MIN_VALUE && clock() - pointerEndedAtMillis < PressTiming.POINTER_ECHO_MILLIS

    private fun changed(was: Boolean): Boolean? = pressed.takeIf { it != was }
}

/** How long a press stays visible, kept apart so it can be unit tested. */
internal object PressTiming {
    /** Long enough for the press spring to reach most of its depth, short enough to feel instant. */
    const val MIN_VISIBLE_PRESS_MILLIS = 90L

    /** How long after a touch ends a late source press is still that touch's echo. */
    const val POINTER_ECHO_MILLIS = 300L

    /** How long a release waits so the press it ends was visible for the minimum time. */
    fun releaseDelayMillis(pressedAtMillis: Long, releasedAtMillis: Long): Long =
        (MIN_VISIBLE_PRESS_MILLIS - (releasedAtMillis - pressedAtMillis)).coerceIn(0L, MIN_VISIBLE_PRESS_MILLIS)
}
