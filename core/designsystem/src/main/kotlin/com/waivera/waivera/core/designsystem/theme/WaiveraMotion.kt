package com.waivera.waivera.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

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

    /**
     * The predictive back preview (M3 "back preview"). Nothing fades, so two screens' text never
     * overlaps mid-gesture: the leaving screen stays opaque on top, shrinks to [BACK_PREVIEW_SCALE]
     * and follows the finger away from [swipeFromRight]'s edge, while the screen underneath shows
     * around it with a slight parallax. Reduced motion holds the leaving screen still and full
     * size, then swaps on commit: a shrink without a frame exposed a cut sliver of the screen
     * underneath at the bottom edge, which read as a rendering glitch (UI3-10).
     */
    fun predictiveBackTransition(swipeFromRight: Boolean): ContentTransform {
        val direction = if (swipeFromRight) -1 else 1
        val transform = if (reduced) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            slideInHorizontally(navigate<IntOffset>()) { -direction * it / BACK_PARALLAX_DIVISOR } togetherWith
                scaleOut(navigate(), targetScale = BACK_PREVIEW_SCALE, transformOrigin = TransformOrigin.Center) +
                slideOutHorizontally(navigate<IntOffset>()) { direction * it / BACK_SHIFT_DIVISOR }
        }
        // The screen being revealed sits underneath the one being dismissed.
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

        /** Half the scale gap, so the shrunken screen's far side stays on the display edge. */
        private const val BACK_SHIFT_DIVISOR = 20
        private const val BACK_PARALLAX_DIVISOR = 10
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
