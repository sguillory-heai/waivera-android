package com.waivera.waivera.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import kotlin.math.roundToInt

/** The motion vocabulary from the UX research. Ad-hoc animation specs fail review. */
enum class MotionToken {
    /** Button and row press: a quick spatial spring to 0.96 scale. */
    Press,

    /** Colour, opacity, badge and toggle changes. Never overshoots. */
    State,

    /** Expand/collapse, list insert/remove, reorder. */
    Layout,

    /** Screen changes. Predictive back and system-like transitions. */
    Navigate,

    /** Rare, earned moments only: task completed, all caught up. */
    Celebrate,
}

/**
 * Resolved motion specs. Normal values mirror Material 3's standard motion scheme springs
 * (fast/default spatial, fast effects) and its expressive slow spatial spring for [MotionToken.Celebrate].
 * When [reduced] is true (animator scale 0 / "Remove animations"), spatial motion snaps and
 * effects become a short cross-fade, so nothing moves and nothing blocks input.
 */
@Immutable
class WaiveraMotionScheme internal constructor(val reduced: Boolean) {

    /** Target scale while a control is pressed. */
    val pressScale: Float = if (reduced) 1f else PRESS_SCALE

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
        const val PRESS_SCALE = 0.96f
        const val REDUCED_FADE_MILLIS = 150
        internal const val SPATIAL_DAMPING = 0.9f
        internal const val FAST_SPATIAL_STIFFNESS = 1400f
        internal const val DEFAULT_SPATIAL_STIFFNESS = 700f
        internal const val EFFECTS_DAMPING = 1f
        internal const val FAST_EFFECTS_STIFFNESS = 3800f
        internal const val CELEBRATE_DAMPING = 0.8f
        internal const val CELEBRATE_STIFFNESS = 200f
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
