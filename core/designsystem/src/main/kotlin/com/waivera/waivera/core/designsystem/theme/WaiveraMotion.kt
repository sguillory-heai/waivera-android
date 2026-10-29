package com.waivera.waivera.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** The motion vocabulary from the UX research. Ad-hoc animation specs fail review. */
enum class MotionToken {
    /** Button and row press: a quick spatial spring to the control's [PressDepth]. */
    Press,

    /** Colour, opacity, badge and toggle changes. Never overshoots. */
    State,

    /** Expand/collapse, list insert/remove, reorder. */
    Layout,

    /** Screen changes. Predictive back and system-like transitions. */
    Navigate,

    /**
     * Rare, earned moments only: all caught up, a celebration. A bouncy spring for the thing
     * that arrives, plus [WaiveraMotionScheme.celebrateBurst] for the confetti around it.
     */
    Celebrate,
}

/**
 * How far a control sinks under the finger, by its size (M1-04). One depth for everything pulled a
 * full-bleed row's edges in about 7dp, enough to shift its text, while a 40dp icon moved under 1dp
 * and the press was not seen. Large surfaces dip a little; small targets dip more. [scale] is the
 * press itself and [longPressScale] how far a control with a long press keeps sinking while the
 * hold arms (M1-02): the Button pair is the critic's 0.96 to 0.93, and the others keep the same
 * proportion so a full-bleed row never sinks enough to shift its text. Reduced motion uses 1 for
 * every depth ([WaiveraMotionScheme.pressScale]).
 */
enum class PressDepth(val scale: Float, val longPressScale: Float) {
    /** Full-bleed list rows: settings, switch and radio rows, conversation rows. */
    Row(scale = 0.985f, longPressScale = 0.97f),

    /** Cards with a margin: agenda cards, bookmark cards, the Today celebration card. */
    Card(scale = 0.97f, longPressScale = 0.95f),

    /** Buttons, chips, segments and message bubbles. */
    Button(scale = 0.96f, longPressScale = 0.93f),

    /** Small targets: icon buttons, the tab icon box, the send glyph, a search clear. */
    Compact(scale = 0.90f, longPressScale = 0.86f),
}

/**
 * Resolved motion specs. Normal values mirror Material 3's standard motion scheme springs
 * (fast/default spatial, fast effects) and its expressive slow spatial spring for [MotionToken.Celebrate].
 * When [reduced] is true (animator scale 0 / "Remove animations"), spatial motion snaps and
 * effects become a short cross-fade, so nothing moves and nothing blocks input.
 */
@Immutable
class WaiveraMotionScheme internal constructor(val reduced: Boolean) {

    /** Target scale while a [PressDepth.Button] control is pressed; see [pressScaleFor]. */
    val pressScale: Float = if (reduced) 1f else PressDepth.Button.scale

    /** Whether loops such as the skeleton pulse should animate at all. */
    val allowsLoopingMotion: Boolean = !reduced

    fun <T> spec(token: MotionToken): FiniteAnimationSpec<T> = when (token) {
        MotionToken.Press -> press()
        MotionToken.State -> state()
        MotionToken.Layout -> layout()
        MotionToken.Navigate -> navigate()
        MotionToken.Celebrate -> celebrate()
    }

    fun <T> press(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = SPATIAL_DAMPING, stiffness = FAST_SPATIAL_STIFFNESS)

    fun <T> state(): FiniteAnimationSpec<T> =
        if (reduced) reducedFade() else spring(dampingRatio = EFFECTS_DAMPING, stiffness = FAST_EFFECTS_STIFFNESS)

    fun <T> layout(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = SPATIAL_DAMPING, stiffness = DEFAULT_SPATIAL_STIFFNESS)

    fun <T> navigate(): FiniteAnimationSpec<T> =
        if (reduced) reducedFade() else spring(dampingRatio = SPATIAL_DAMPING, stiffness = DEFAULT_SPATIAL_STIFFNESS)

    fun <T> celebrate(): FiniteAnimationSpec<T> =
        if (reduced) reducedFade() else spring(dampingRatio = CELEBRATE_DAMPING, stiffness = CELEBRATE_STIFFNESS)

    /**
     * Timeline of the celebrate token's confetti burst, from 0 (launch) to 1 (gone). Confetti
     * follows time rather than a spring so every piece finishes falling. Reduced motion snaps
     * straight to the end, and `WaiveraCelebrationBurst` draws nothing at all.
     */
    val celebrateBurst: FiniteAnimationSpec<Float> =
        if (reduced) snap() else tween(durationMillis = CELEBRATE_BURST_MILLIS, easing = LinearEasing)

    /**
     * Your own message landing in the conversation: a quick, slightly springy pop up from the
     * composer. It finishes well inside a tap, so it never slows a run of sends, and it never
     * vibrates. Reduced motion snaps.
     */
    val sendPop: FiniteAnimationSpec<Float> =
        if (reduced) snap() else spring(dampingRatio = SEND_POP_DAMPING, stiffness = SEND_POP_STIFFNESS)

    /**
     * A control that just changed state on its own spot: the selected tab's icon, a count that
     * moved, a check or a bookmark that filled. A short, bouncy spring that settles well inside
     * a tap, so it adds punch without delaying anything. Reduced motion snaps.
     * It ends on [POP_VISIBILITY_THRESHOLD], not the default 0.01 (M2-03): a long press's pop
     * from 0.93 peaks near 1.011 and then snapped to rest in one frame, and the Row depth's
     * smaller overshoot never played at all.
     */
    val pop: FiniteAnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS, visibilityThreshold = POP_VISIBILITY_THRESHOLD)
    }

    /**
     * A wrong sign-in: the form moves side to side once and settles, as a head shake says "no".
     * Values are fractions of [WaiveraSpacing.ShakeAmplitude], starting and ending at 0, and the
     * swing shrinks each time so it reads as one small shake rather than a wobble. Each segment
     * eases in and out, so the form slows into every turn instead of bouncing off it as a
     * triangle wave (M1-15). With the failed-send vibration it is the only feedback that pairs
     * with a buzz (haptics policy, 2026-10-29). Reduced motion snaps, and `rememberRejectShake`
     * does not run it at all.
     */
    val rejectShake: FiniteAnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        keyframes {
            durationMillis = REJECT_SHAKE_MILLIS
            0f at 0 using FastOutSlowInEasing
            -1f at SHAKE_FIRST_MILLIS using FastOutSlowInEasing
            1f at SHAKE_SECOND_MILLIS using FastOutSlowInEasing
            -SHAKE_THIRD_SWING at SHAKE_THIRD_MILLIS using FastOutSlowInEasing
            SHAKE_FOURTH_SWING at SHAKE_FOURTH_MILLIS using FastOutSlowInEasing
        }
    }

    /**
     * The skeleton's breathing between the base and highlight colours: eased, so it slows at each
     * end like a breath instead of bouncing between them (M1-16). Only used while
     * [allowsLoopingMotion]; with animations off the skeleton holds its base colour.
     */
    val skeletonPulse: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = SKELETON_PULSE_MILLIS, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
    )

    /** Forward screen change: content slides in from the end, or cross-fades when reduced. */
    fun forwardTransition(): ContentTransform = if (reduced) {
        fadeIn(reducedFade()) togetherWith fadeOut(reducedFade())
    } else {
        slideInHorizontally(navigate<IntOffset>()) { it / SLIDE_DIVISOR } + fadeIn(state()) togetherWith
            slideOutHorizontally(navigate<IntOffset>()) { -it / SLIDE_DIVISOR } + fadeOut(state())
    }

    /** Back screen change, the mirror of [forwardTransition]. */
    fun backTransition(): ContentTransform = if (reduced) {
        fadeIn(reducedFade()) togetherWith fadeOut(reducedFade())
    } else {
        slideInHorizontally(navigate<IntOffset>()) { -it / SLIDE_DIVISOR } + fadeIn(state()) togetherWith
            slideOutHorizontally(navigate<IntOffset>()) { it / SLIDE_DIVISOR } + fadeOut(state())
    }

    /** Motion that follows a gesture instead of running on its own: the predictive back preview. */
    val gesture: GestureMotion = GestureMotion(reduced)

    /**
     * The transition Navigation 3 seeks during a predictive back swipe. It moves nothing itself:
     * `BackPreviewFrame` draws the whole preview (shrink, slide, corner, outline and dim) from
     * the gesture's progress through [GestureMotion.progress]. A seeked transition reports
     * values that depend on when each animation was set up, not on the finger (UI6-01, UI7-01).
     * What this transition does is keep both screens composed for [BACK_GESTURE_MILLIS] of
     * seekable time, through a fade that stays at full opacity, so the frame's
     * [GestureMotion.settle] has the screen underneath to land on. Nothing fades, so two
     * screens' text never overlaps mid-gesture.
     * Reduced motion holds the leaving screen still and full size, then swaps on commit: a
     * shrink without a frame exposed a cut sliver of the screen underneath at the bottom edge,
     * which read as a rendering glitch (UI3-10).
     */
    fun predictiveBackTransition(): ContentTransform {
        val transform = if (reduced) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            val hold = tween<Float>(durationMillis = BACK_GESTURE_MILLIS, easing = LinearEasing)
            fadeIn(hold, initialAlpha = OPAQUE) togetherWith fadeOut(hold, targetAlpha = OPAQUE)
        }
        // The screen being revealed sits underneath the one being dismissed. NavDisplay replaces
        // this with its own z-index (one below the leaving scene); a plain AnimatedContent has to
        // keep its settled content at 0, or both screens get -1 and the revealed one draws on top.
        transform.targetContentZIndex = BACK_TARGET_Z_INDEX
        return transform
    }

    private fun <T> reducedFade(): FiniteAnimationSpec<T> = tween(REDUCED_FADE_MILLIS, easing = LinearEasing)

    override fun equals(other: Any?): Boolean = other is WaiveraMotionScheme && other.reduced == reduced

    override fun hashCode(): Int = reduced.hashCode()

    companion object {
        /** The [PressDepth.Button] press scale, the one most controls use. */
        const val PRESS_SCALE = 0.96f
        internal const val PRESS_RELEASE_DAMPING = 0.6f
        internal const val PRESS_RELEASE_STIFFNESS = 800f
        internal const val PRESS_CANCEL_STIFFNESS = 2400f

        /** How long a held press waits before it starts sinking toward its long press (M1-02). */
        const val LONG_PRESS_ARM_DELAY_MILLIS = 150L

        /** The shortest sink, for a system long-press timeout barely past the arm delay. */
        const val LONG_PRESS_MIN_SINK_MILLIS = 100

        /**
         * With animations off, a press answers with a state-layer tint at once instead of a scale
         * (M1-12): Material's pressed alpha, and its dragged alpha while a long press arms.
         */
        const val PRESSED_TINT_ALPHA = 0.10f
        const val LONG_PRESS_TINT_ALPHA = 0.16f

        /** [exit]: the leaving side of a swap, gone before the arriving side is halfway (M1-01, M2-02). */
        const val EXIT_MILLIS = 60

        /** How long a swap's arriving side waits before it starts to fade in ([swapEnterFade], M2-02). */
        const val SWAP_ENTER_DELAY_MILLIS = 40

        /** One half of a [skeletonPulse] breath. */
        const val SKELETON_PULSE_MILLIS = 900

        /** How long "Synced" shows, its check popped, before the sync line folds away (M1-07). */
        const val SYNCED_BEAT_MILLIS = 900L

        /**
         * How long a send waits before it says "Sending…" (M1-09). A good connection acks inside
         * this, so the footer goes straight to the time in one swap instead of flashing twice.
         */
        const val SENDING_LABEL_DELAY_MILLIS = 400L

        /** How long the send button keeps its enabled fill after a send, so its pop is seen (M1-08). */
        const val SEND_PAYOFF_HOLD_MILLIS = 180L

        /** How long a removed item shows its control's change before it starts to leave (M1-14). */
        const val REMOVAL_BEAT_MILLIS = 120L

        /**
         * How long after its removal was asked for an item may still be on screen before the
         * removal counts as not held (failed or undone), and its control goes back.
         */
        const val REMOVAL_HELD_MILLIS = 1_000L

        /** Where the selected tab's pill starts across: grown out from the glyph (M1-11). */
        const val TAB_PILL_START_SCALE_X = 0.55f
        const val REDUCED_FADE_MILLIS = 150
        internal const val SPATIAL_DAMPING = 0.9f
        internal const val FAST_SPATIAL_STIFFNESS = 1400f
        internal const val DEFAULT_SPATIAL_STIFFNESS = 700f
        internal const val EFFECTS_DAMPING = 1f
        internal const val FAST_EFFECTS_STIFFNESS = 3800f
        internal const val CELEBRATE_DAMPING = 0.8f
        internal const val CELEBRATE_STIFFNESS = 200f
        const val CELEBRATE_BURST_MILLIS = 1_400
        internal const val SEND_POP_DAMPING = 0.62f
        internal const val SEND_POP_STIFFNESS = 520f
        internal const val POP_DAMPING = 0.5f
        internal const val POP_STIFFNESS = 900f

        /** Where a [pop] may stop: fine enough for a Row long press's 0.005 overshoot to play. */
        const val POP_VISIBILITY_THRESHOLD = 0.001f

        /** The whole [rejectShake], from the first move to rest. */
        const val REJECT_SHAKE_MILLIS = 300
        private const val SHAKE_FIRST_MILLIS = 40
        private const val SHAKE_SECOND_MILLIS = 110
        private const val SHAKE_THIRD_MILLIS = 180
        private const val SHAKE_FOURTH_MILLIS = 245
        private const val SHAKE_THIRD_SWING = 0.6f
        private const val SHAKE_FOURTH_SWING = 0.25f

        /** Where a [pop] starts: a little small, so the spring's overshoot reads as a pop. */
        const val POP_START_SCALE = 0.8f
        private const val SLIDE_DIVISOR = 4
        const val BACK_PREVIEW_SCALE = 0.9f

        /** Seekable length of the predictive back transition, and so the longest settle. */
        const val BACK_GESTURE_MILLIS = 300
        private const val OPAQUE = 1f
        private const val BACK_TARGET_Z_INDEX = -1f

        val Standard = WaiveraMotionScheme(reduced = false)
        val Reduced = WaiveraMotionScheme(reduced = true)

        /** Android has no separate reduce-motion switch: "Remove animations" sets the animator scale to 0. */
        fun forAnimatorScale(animatorScale: Float): WaiveraMotionScheme = if (animatorScale <= 0f) Reduced else Standard
    }
}

/**
 * The arriving side of a swap (M2-02), Material's fade-through order: its fade waits
 * [WaiveraMotionScheme.SWAP_ENTER_DELAY_MILLIS], by when the leaving side is nearly gone, then
 * eases in. The leaving side has cleared before this one is halfway, so two texts never sit on
 * top of each other. Only the fade waits: a swap's slide or pop starts on the first frame, so it
 * still answers at once. Reduced motion is the short fade, at once.
 */
fun <T> WaiveraMotionScheme.swapEnterFade(): FiniteAnimationSpec<T> = if (reduced) {
    tween(WaiveraMotionScheme.REDUCED_FADE_MILLIS, easing = LinearEasing)
} else {
    tween(
        durationMillis = SWAP_ENTER_FADE_MILLIS,
        delayMillis = WaiveraMotionScheme.SWAP_ENTER_DELAY_MILLIS,
        easing = LinearOutSlowInEasing,
    )
}

/**
 * The leaving side of a swap: [exit], and with animations off gone at once, so the short fade of
 * what arrives never sits over what leaves (M2-02, verifier note c).
 */
fun <T> WaiveraMotionScheme.swapExitFade(): FiniteAnimationSpec<T> = if (reduced) snap() else exit()

/** Which way a directional [swapTransition] moves: across, as a period step, or up and down, as a count roll. */
enum class SwapAxis { Horizontal, Vertical }

/**
 * Content replacing other content in the same place (a status line, a period title). A
 * [direction] above 0 slides the new content in from the end side (or from below on the
 * [SwapAxis.Vertical] [axis]), below 0 from the start side (or from above), as when stepping to
 * the next or previous period or a count going up or down; 0 fades it in with a small pop.
 *
 * The fades run in Material's fade-through order (M2-02): the leaving side drops off on
 * [swapExitFade] while the arriving side's fade waits ([swapEnterFade]), so two texts never sit on
 * top of each other at half strength. The slide or pop starts on the first frame, so the swap
 * still answers at once. Reduced motion has no slide or pop: the leaving side goes at once and
 * the new one takes the short fade.
 *
 * An in-place swap's slot follows the new content's size on the layout token without clipping
 * (M1-01): the default size transform clipped the arriving content to the growing bounds, so
 * "Discard" showed as "iscard". A slide is masked by its slot instead (M2-04), so a sliding month
 * or count never draws past the card or the line it belongs to. Reduced motion jumps to the new
 * size.
 */
fun WaiveraMotionScheme.swapTransition(direction: Int = 0, axis: SwapAxis = SwapAxis.Horizontal): ContentTransform {
    val slides = !reduced && direction != 0
    val transform = when {
        reduced -> fadeIn(swapEnterFade()) togetherWith fadeOut(swapExitFade())
        direction == 0 ->
            fadeIn(swapEnterFade()) + scaleIn(pop, initialScale = SWAP_START_SCALE) togetherWith
                fadeOut(swapExitFade())
        else -> {
            val sign = if (direction > 0) 1 else -1
            swapSlideIn(axis, sign) + fadeIn(swapEnterFade()) togetherWith
                swapSlideOut(axis, sign) + fadeOut(swapExitFade())
        }
    }
    return transform.withSize(swapSizeTransform(clip = slides))
}

private fun WaiveraMotionScheme.swapSlideIn(axis: SwapAxis, sign: Int): EnterTransition = when (axis) {
    SwapAxis.Horizontal -> slideInHorizontally(layout<IntOffset>()) { sign * it / SWAP_DIVISOR }
    SwapAxis.Vertical -> slideInVertically(layout<IntOffset>()) { sign * it / SWAP_ROLL_DIVISOR }
}

private fun WaiveraMotionScheme.swapSlideOut(axis: SwapAxis, sign: Int): ExitTransition = when (axis) {
    SwapAxis.Horizontal -> slideOutHorizontally(layout<IntOffset>()) { -sign * it / SWAP_DIVISOR }
    SwapAxis.Vertical -> slideOutVertically(layout<IntOffset>()) { -sign * it / SWAP_ROLL_DIVISOR }
}

/**
 * The swap's size change on the layout token (a snap with animations off). Unclipped unless
 * [clip], which a slide asks for so its slot masks it.
 */
fun WaiveraMotionScheme.swapSizeTransform(clip: Boolean = false): SizeTransform =
    SizeTransform(clip = clip) { _, _ -> layout<IntSize>() }

/**
 * Skeleton to content, or content back to a skeleton (M1-16): the same fade-through as a swap
 * (M2-02), with no slide or scale, so loaded content settles in place instead of cutting. Reduced
 * motion drops the old side at once and fades the new one in.
 */
fun WaiveraMotionScheme.loadTransition(): ContentTransform =
    (fadeIn(swapEnterFade()) togetherWith fadeOut(swapExitFade())).withSize(swapSizeTransform())

/**
 * `using` is only declared inside AnimatedContentTransitionScope, and these transitions are built
 * outside one, so the size transform goes in through the constructor.
 */
private fun ContentTransform.withSize(sizeTransform: SizeTransform): ContentTransform =
    ContentTransform(targetContentEnter, initialContentExit, targetContentZIndex, sizeTransform)

private const val SWAP_START_SCALE = 0.92f
private const val SWAP_DIVISOR = 3
private const val SWAP_ROLL_DIVISOR = 2
private const val SWAP_ENTER_FADE_MILLIS = 150

val LocalWaiveraMotion = staticCompositionLocalOf { WaiveraMotionScheme.Standard }

/**
 * Reads the system animator duration scale and follows changes while composed, so turning on
 * "Remove animations" takes effect without restarting the app.
 */
@Composable
fun rememberSystemMotionScheme(): WaiveraMotionScheme {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var scheme by remember(resolver) {
        mutableStateOf(WaiveraMotionScheme.forAnimatorScale(readAnimatorScale(resolver)))
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheme = WaiveraMotionScheme.forAnimatorScale(readAnimatorScale(resolver))
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scheme
}

private fun readAnimatorScale(resolver: android.content.ContentResolver): Float =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * Motion driven by a gesture's progress rather than by time, used by the predictive back preview
 * (UI7-01). Springs stay for everything that runs on its own, including the back button's pop.
 */
@Immutable
class GestureMotion internal constructor(private val reduced: Boolean) {
    /**
     * Maps gesture progress (0 when the swipe starts, 1 at the system's full travel) to how far
     * the preview has moved. Linear, so the card shrinks, the screens slide, the corner rounds
     * and the dim lifts evenly with the finger: a quarter of the swipe is a quarter of the
     * preview. The springs that used to drive the preview front-loaded about 97% of the change
     * into the first fifth of the swipe. Reduced motion maps every progress to 0, so nothing
     * moves.
     */
    val progress: Easing = if (reduced) Easing { 0f } else LinearEasing

    /**
     * How the preview finishes after the finger lifts, from wherever the swipe left it. It takes
     * the share of [WaiveraMotionScheme.BACK_GESTURE_MILLIS] the swipe had left
     * ([remainingFraction] of the way), which is when Navigation 3 removes the screen the preview
     * is leaving behind, so the preview lands as that screen goes: no bare edge on a cancel, no
     * lingering card on a commit. A spring settles in a fixed time wherever the finger lifted, so
     * it would run past that removal or stop short of it. Ease-out, so it starts at the finger's
     * speed and slows into place.
     */
    fun settle(remainingFraction: Float): FiniteAnimationSpec<Float> {
        if (reduced) return snap()
        val millis = remainingFraction.coerceIn(0f, 1f) * WaiveraMotionScheme.BACK_GESTURE_MILLIS
        return tween(durationMillis = millis.roundToInt(), easing = LinearOutSlowInEasing)
    }
}
