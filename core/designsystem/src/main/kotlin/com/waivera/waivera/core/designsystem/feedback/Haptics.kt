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
 * The only moments in the whole app that vibrate (haptics policy, 2026-10-29): "avoid vibrations
 * where possible". Each one is a single Reject signal. Everything else is silent and motion
 * carries the feedback: taps, long presses, toggles, selection, swipe thresholds, successes
 * (copy, save, accept, bookmark, a message that sends, first time or after a retry), warnings
 * (destructive confirms, going offline), incoming messages, and every other error or failed
 * validation. A new buzz needs a new entry here and a policy change, never a call site.
 */
enum class HapticMoment {
    /** A message or reply failed to send. Once per failure, never for a send that works. */
    SendFailed,

    /** Sign-in credentials were rejected; the form shakes with it. Never for a network failure. */
    SignInRejected,
}

/** Platform-neutral haptic signal; one per meaning so each moment feels the same everywhere. */
internal enum class HapticSignal {
    Reject,
}

internal fun HapticMoment.signal(): HapticSignal = when (this) {
    HapticMoment.SendFailed -> HapticSignal.Reject
    HapticMoment.SignInRejected -> HapticSignal.Reject
}

private fun HapticSignal.feedbackType(): HapticFeedbackType = when (this) {
    HapticSignal.Reject -> HapticFeedbackType.Reject
}

/**
 * The only way screens trigger haptics, and only for a [HapticMoment]. Uses Compose's semantic
 * haptic types, which map to `View.performHapticFeedback` constants, respect the system haptic
 * setting and need no VIBRATE permission. Honours the in-app Haptics switch, and drops repeats of
 * the same moment inside [MIN_REPEAT_MILLIS] so a burst of failures does not stack into a buzz.
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

    companion object {
        const val MIN_REPEAT_MILLIS = 150L
    }
}

/** The Settings "Haptics" switch, provided by `WaiveraTheme`. It governs every [HapticMoment]. */
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
