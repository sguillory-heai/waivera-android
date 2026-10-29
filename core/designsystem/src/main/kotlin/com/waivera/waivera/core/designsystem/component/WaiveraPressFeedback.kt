package com.waivera.waivera.core.designsystem.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.longPressScaleFor
import com.waivera.waivera.core.designsystem.theme.longPressSink
import com.waivera.waivera.core.designsystem.theme.pressCancel
import com.waivera.waivera.core.designsystem.theme.pressRelease
import com.waivera.waivera.core.designsystem.theme.pressScaleFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The shared press response: a spring to the control's [PressDepth] that starts on touch-down, not
 * on release. Clickables inside a scrolling list hold back their press by the tap timeout, and a
 * quick tap used to send the press and the release in the same frame, so the control never moved
 * (a dead tap). This follows the finger itself and keeps every press visible for at least
 * [PressTiming.MIN_VISIBLE_PRESS_MILLIS], then springs back past rest on the bouncier
 * [pressRelease][WaiveraMotionScheme.pressRelease]. When a scroll or a swipe takes the gesture, or
 * the finger leaves, it lets go at once on [pressCancel][WaiveraMotionScheme.pressCancel], with no
 * hold and no bounce, so a row never sits dipped while it scrolls (M1-05). Keyboard and
 * switch-access presses still arrive through the interaction source.
 *
 * A control with a long press keeps sinking while the hold arms and springs back past rest on the
 * pop token when it fires (M1-02), the visible answer the long-press buzz used to give.
 *
 * Under reduced motion nothing scales. The press answers at once with a state-layer tint instead
 * ([tintAlpha], drawn by [pressFeedback]), because the ripple inside a list waits for the tap
 * timeout (M1-12); a long press that arms deepens the tint.
 *
 * Use [pressFeedback] on the control, or [pressTracking] on the touch target and [pressScale]
 * on the part that should shrink (a tab's icon rather than the whole tab).
 */
@Stable
class PressFeedbackState internal constructor(
    private val scope: CoroutineScope,
    private val motion: WaiveraMotionScheme,
    private val depth: PressDepth = PressDepth.Button,
    private val armsLongPress: Boolean = false,
    private val longPressTimeoutMillis: Long = DEFAULT_LONG_PRESS_TIMEOUT_MILLIS,
    /** The reduced-motion tint's colour: the content colour where the control sits, as a ripple's. */
    internal val tintColor: Color = Color.Unspecified,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private val scale = Animatable(1f)
    private val tint = mutableFloatStateOf(0f)
    private val inputs = PressInputs(clock)
    private var pressedAtMillis = 0L
    private var longPressFired = false
    private var job: Job? = null

    /** The current scale; read it in a graphics layer, never in composition. */
    val value: Float get() = scale.value

    /** The reduced-motion state layer's alpha, 0 when not pressed; read it in a draw block only. */
    val tintAlpha: Float get() = tint.floatValue

    /**
     * The finger went down or ended. [cancelled] is true when the gesture was taken (a scroll, a
     * swipe, leaving the control, a system cancel) rather than lifted.
     */
    internal fun onPointer(down: Boolean, cancelled: Boolean = false) = follow(inputs.pointer(down), cancelled)

    internal fun onInteraction(down: Boolean) = follow(inputs.interaction(down), cancelled = false)

    /**
     * The long press fired: the sunk control springs back to rest on the pop token, past it a
     * little, as the menu rises. The release that follows leaves that spring alone.
     */
    fun onLongPress() {
        longPressFired = true
        job?.cancel()
        job = scope.launch {
            scale.snapTo(scale.value)
            scale.animateTo(1f, motion.pop)
        }
    }

    private fun follow(pressed: Boolean?, cancelled: Boolean) {
        when (pressed) {
            true -> press()
            false -> release(cancelled)
            null -> Unit
        }
    }

    private fun press() {
        pressedAtMillis = clock()
        longPressFired = false
        job?.cancel()
        if (motion.reduced) tint.floatValue = WaiveraMotionScheme.PRESSED_TINT_ALPHA
        job = scope.launch {
            scale.animateTo(motion.pressScaleFor(depth), motion.press())
            if (armsLongPress) armLongPress()
        }
    }

    /** Waits out the arm delay, then sinks (or, with animations off, deepens the tint). */
    private suspend fun armLongPress() {
        val waited = clock() - pressedAtMillis
        delay((WaiveraMotionScheme.LONG_PRESS_ARM_DELAY_MILLIS - waited).coerceAtLeast(0L))
        if (motion.reduced) {
            tint.floatValue = WaiveraMotionScheme.LONG_PRESS_TINT_ALPHA
            return
        }
        val sinkMillis = (longPressTimeoutMillis - WaiveraMotionScheme.LONG_PRESS_ARM_DELAY_MILLIS).toInt()
            .coerceAtLeast(WaiveraMotionScheme.LONG_PRESS_MIN_SINK_MILLIS)
        scale.animateTo(motion.longPressScaleFor(depth), motion.longPressSink(sinkMillis))
    }

    private fun release(cancelled: Boolean) {
        if (longPressFired) {
            // The long press's pop is already taking the control home.
            longPressFired = false
            tint.floatValue = 0f
            return
        }
        val wait = PressTiming.releaseDelayMillis(pressedAtMillis, clock(), cancelled)
        val pressing = job
        // The press keeps animating while it waits; animateTo then takes the value from there.
        job = scope.launch {
            if (wait > 0L) delay(wait)
            // Stop a long press that was still waiting to sink.
            pressing?.cancel()
            tint.floatValue = 0f
            scale.animateTo(1f, if (cancelled) motion.pressCancel() else motion.pressRelease())
        }
    }
}

/** Android's default long-press timeout, used only where no view configuration is at hand. */
private const val DEFAULT_LONG_PRESS_TIMEOUT_MILLIS = 400L

/**
 * Remembers the press response for a control whose clickable uses [interactionSource]. [depth]
 * follows the control's size; [armsLongPress] is true when it has a long press, so a held press
 * sinks toward it (call [PressFeedbackState.onLongPress] when it fires).
 */
@Composable
fun rememberPressFeedback(
    interactionSource: InteractionSource,
    depth: PressDepth = PressDepth.Button,
    armsLongPress: Boolean = false,
): PressFeedbackState {
    val motion = WaiveraTheme.motion
    val scope = rememberCoroutineScope()
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val tintColor = LocalContentColor.current
    val state = remember(scope, motion, depth, armsLongPress, longPressTimeout, tintColor) {
        PressFeedbackState(scope, motion, depth, armsLongPress, longPressTimeout, tintColor)
    }
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

/**
 * Touch-down tracking, the scale, and the reduced-motion tint, on one control. [shape] is the
 * control's outline, so the tint covers what the ripple would and nothing more.
 */
fun Modifier.pressFeedback(
    state: PressFeedbackState,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
): Modifier = pressTracking(state, enabled).pressScale(state).pressTint(state, shape)

/** Draws [state]'s scale. The value is read in the layer only, so pressing never recomposes. */
fun Modifier.pressScale(state: PressFeedbackState): Modifier = graphicsLayer {
    val scale = state.value
    scaleX = scale
    scaleY = scale
}

/**
 * Draws [state]'s reduced-motion state layer over the control, in [shape]. With animations on
 * the alpha stays 0 and nothing is drawn. Read in the draw block, so it never recomposes.
 */
fun Modifier.pressTint(state: PressFeedbackState, shape: Shape = RectangleShape): Modifier = drawWithContent {
    drawContent()
    val alpha = state.tintAlpha
    if (alpha > 0f && state.tintColor.isSpecified) {
        drawOutline(shape.createOutline(size, layoutDirection, this), state.tintColor, alpha = alpha)
    }
}

/**
 * Watches the finger without consuming anything: the press starts on the first touch, and ends
 * when every finger lifts (a release), or when the finger leaves the control or a scroll or swipe
 * consumes the move (a cancel, which lets go at once).
 * Only the innermost tracker under the finger answers, so a row does not shrink along with the
 * control inside it (a Join call button in a chat row would otherwise scale twice).
 */
fun Modifier.pressTracking(state: PressFeedbackState, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return pointerInput(state) {
        awaitEachGesture {
            // The main pass reaches the innermost node first, in the same frame as the touch.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            if (!PressClaim.take(down.id)) return@awaitEachGesture
            state.onPointer(down = true)
            // Anything but a lift (a scroll or swipe taking the move, leaving the control, a
            // system cancel) lets go at once, without the minimum hold.
            var cancelled = true
            try {
                var holding = true
                while (holding) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val lifted = event.changes.none { it.pressed }
                    val taken = event.changes.any { change ->
                        change.pressed && !change.changedToDownIgnoreConsumed() && change.isConsumed
                    }
                    val outside = event.changes.any { it.isOutOfBounds(size, extendedTouchPadding) }
                    if (lifted) cancelled = false
                    holding = !lifted && !taken && !outside
                }
            } finally {
                PressClaim.release(down.id)
                state.onPointer(down = false, cancelled = cancelled)
            }
        }
    }
}

/**
 * The finger a tracker is already answering, so the trackers around it leave that touch alone.
 * Pointer input runs on the main thread only. A claim lasts for its gesture and no longer, so a
 * reused pointer id never inherits it.
 */
private object PressClaim {
    private var claimed: PointerId? = null

    fun take(id: PointerId): Boolean {
        if (claimed == id) return false
        claimed = id
        return true
    }

    fun release(id: PointerId) {
        if (claimed == id) claimed = null
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

    /**
     * How long a release waits so the press it ends was visible for the minimum time. A press that
     * was [cancelled] (taken by a scroll or a swipe, or left) does not wait: holding the dip while
     * the list moves read as a stuck row (M1-05).
     */
    fun releaseDelayMillis(pressedAtMillis: Long, releasedAtMillis: Long, cancelled: Boolean = false): Long =
        if (cancelled) {
            0L
        } else {
            (MIN_VISIBLE_PRESS_MILLIS - (releasedAtMillis - pressedAtMillis)).coerceIn(0L, MIN_VISIBLE_PRESS_MILLIS)
        }
}
