package com.waivera.waivera.core.designsystem.feedback

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Moments that earn a haptic, from the UX research "Waivera feedback map". Plain taps on
 * buttons and rows have no haptic on purpose (the press scale is the feedback), and incoming
 * messages never buzz while the app is open.
 */
enum class HapticMoment {
    /** A switch turned on. */
    ToggleOn,

    /** A switch turned off. */
    ToggleOff,

    /** Picking from chips, a segmented control or a time picker. */
    Selection,

    /** A swipe action crossing its threshold. */
    SwipeThreshold,

    /** Task done, message sent after a retry, invitation accepted, copied. */
    Success,

    /** Validation or send failure. */
    Error,

    /** Confirming a destructive action, or the app going offline. */
    Warning,

    /** A long press that opens a menu. */
    LongPress,
}

/** Platform-neutral haptic signal; one per meaning so each moment feels the same everywhere. */
internal enum class HapticSignal {
    ToggleOn,
    ToggleOff,
    SegmentTick,
    GestureThresholdActivate,
    Confirm,
    Reject,
    LongPress,
}

internal fun HapticMoment.signal(): HapticSignal = when (this) {
    HapticMoment.ToggleOn -> HapticSignal.ToggleOn
    HapticMoment.ToggleOff -> HapticSignal.ToggleOff
    HapticMoment.Selection -> HapticSignal.SegmentTick
    HapticMoment.SwipeThreshold -> HapticSignal.GestureThresholdActivate
    HapticMoment.Success -> HapticSignal.Confirm
    HapticMoment.Error -> HapticSignal.Reject
    HapticMoment.Warning -> HapticSignal.Reject
    HapticMoment.LongPress -> HapticSignal.LongPress
}

private fun HapticSignal.feedbackType(): HapticFeedbackType = when (this) {
    HapticSignal.ToggleOn -> HapticFeedbackType.ToggleOn
    HapticSignal.ToggleOff -> HapticFeedbackType.ToggleOff
    HapticSignal.SegmentTick -> HapticFeedbackType.SegmentTick
    HapticSignal.GestureThresholdActivate -> HapticFeedbackType.GestureThresholdActivate
    HapticSignal.Confirm -> HapticFeedbackType.Confirm
    HapticSignal.Reject -> HapticFeedbackType.Reject
    HapticSignal.LongPress -> HapticFeedbackType.LongPress
}

/**
 * The only way screens trigger haptics. Uses Compose's semantic haptic types, which map to
 * `View.performHapticFeedback` constants, respect the system haptic setting and need no
 * VIBRATE permission. Honours the in-app Haptics switch, and drops repeats of the same moment
 * inside [MIN_REPEAT_MILLIS] so a burst of sends does not stack into a buzz.
 */
class Haptics internal constructor(
    private val performSignal: (HapticSignal) -> Unit,
    private val isEnabled: () -> Boolean,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private var lastMoment: HapticMoment? = null
    private var lastAtMillis: Long = Long.MIN_VALUE

    fun perform(moment: HapticMoment) {
        if (!isEnabled()) return
        val now = clock()
        if (moment == lastMoment && lastAtMillis != Long.MIN_VALUE && now - lastAtMillis < MIN_REPEAT_MILLIS) return
        lastMoment = moment
        lastAtMillis = now
        performSignal(moment.signal())
    }

    fun toggle(on: Boolean) = perform(if (on) HapticMoment.ToggleOn else HapticMoment.ToggleOff)

    fun selection() = perform(HapticMoment.Selection)

    fun swipeThreshold() = perform(HapticMoment.SwipeThreshold)

    fun success() = perform(HapticMoment.Success)

    fun error() = perform(HapticMoment.Error)

    fun warning() = perform(HapticMoment.Warning)

    fun longPress() = perform(HapticMoment.LongPress)

    companion object {
        const val MIN_REPEAT_MILLIS = 150L
    }
}

/** The Settings "Haptics" switch, provided by `WaiveraTheme`. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val feedback: HapticFeedback = LocalHapticFeedback.current
    val enabled = rememberUpdatedState(LocalHapticsEnabled.current)
    return remember(feedback) {
        Haptics(
            performSignal = { signal -> feedback.performHapticFeedback(signal.feedbackType()) },
            isEnabled = { enabled.value },
        )
    }
}
