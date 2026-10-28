package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlin.math.abs

/**
 * The M3 back-preview frame around one navigation entry (UI3-10): while a predictive back swipe
 * shrinks the screen being dismissed, its corners round to [WaiveraSizes.BackPreviewCorner], and
 * the screen being revealed underneath is dimmed by a scrim that fades as the swipe goes on.
 *
 * [scope] is the entry's animated content scope (Navigation 3's `LocalNavAnimatedContentScope`).
 * [isTop] says whether the entry is the top of the back stack. During a swipe the stack has not
 * changed yet, so the top entry is the one leaving and the one below it is the one revealed. In
 * an ordinary push or pop neither of those holds, so the frame shows only mid-swipe. The roles
 * are read when a transition starts and kept until it settles, so committing the swipe (which
 * changes the stack) does not drop the corners while the screen finishes leaving.
 *
 * The corner and the scrim have no animations of their own. They are read, at draw time, from
 * the scale and the slide that [WaiveraMotionScheme.predictiveBackTransition] puts on the same
 * transition, so they move in step with the card frame by frame. Their own springs on the same
 * spec settled before the scale did: the layout-time scale and slide start later in a seek than
 * animations set up in composition, so halfway through a swipe the scrim was already gone and
 * the corner already full (UI6-01).
 *
 * Reduced motion holds the leaving screen still and full size, so there is nothing to frame.
 */
@Composable
fun BackPreviewFrame(
    scope: AnimatedVisibilityScope,
    isTop: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = WaiveraTheme.motion
    if (motion.reduced) {
        Box(modifier = modifier) { content() }
        return
    }
    val scrimColor = WaiveraTheme.colors.scrim
    val transition = scope.transition
    val settled = transition.currentState == transition.targetState
    val topDuringTransition = remember(settled) { isTop }
    Box(
        modifier = modifier
            .graphicsLayer {
                val corner = if (topDuringTransition) {
                    WaiveraSizes.BackPreviewCorner * transition.shrinkProgress()
                } else {
                    WaiveraSpacing.None
                }
                shape = RoundedCornerShape(corner)
                clip = corner > WaiveraSpacing.None
            }
            .drawWithContent {
                drawContent()
                val revealing = !topDuringTransition && transition.currentState == EnterExitState.PreEnter
                val scrim = if (revealing) 1f - transition.revealProgress() else 0f
                if (scrim > 0f) drawRect(color = scrimColor, alpha = scrim)
            },
    ) {
        content()
    }
}

/**
 * How far the leaving screen has shrunk, from 0 at full size to 1 at
 * [WaiveraMotionScheme.BACK_PREVIEW_SCALE], read from the transition's own scale animation.
 * 0 when there is none, as in a push, a fade or a settled screen.
 */
private fun Transition<EnterExitState>.shrinkProgress(): Float {
    val scale = animations.firstNotNullOfOrNull { animation ->
        val ends = listOf(animation.animation.initialValue, animation.animation.targetValue)
        val isPreviewScale = animation.typeConverter == Float.VectorConverter &&
            WaiveraMotionScheme.BACK_PREVIEW_SCALE in ends
        if (isPreviewScale) animation.value as? Float else null
    } ?: return 0f
    return backPreviewShrinkProgress(scale)
}

/**
 * How far the revealed screen has slid into place, from 0 at its start offset to 1 at rest,
 * read from the transition's own slide animation. 1 when there is none, so nothing is dimmed.
 */
private fun Transition<EnterExitState>.revealProgress(): Float {
    val slide = animations.firstOrNull { it.typeConverter == IntOffset.VectorConverter } ?: return 1f
    val offset = slide.value as? IntOffset ?: return 1f
    val from = slide.animation.initialValue as? IntOffset ?: return 1f
    val to = slide.animation.targetValue as? IntOffset ?: return 1f
    return backPreviewRevealProgress(offsetX = offset.x, extentX = maxOf(abs(from.x), abs(to.x)))
}

/** 0 at scale 1, 1 at the preview scale, clamped in between. */
internal fun backPreviewShrinkProgress(scale: Float): Float =
    ((1f - scale) / (1f - WaiveraMotionScheme.BACK_PREVIEW_SCALE)).coerceIn(0f, 1f)

/** 1 at rest, 0 at the full [extentX] away; 1 when there is no slide at all. */
internal fun backPreviewRevealProgress(offsetX: Int, extentX: Int): Float =
    if (extentX <= 0) 1f else (1f - abs(offsetX).toFloat() / extentX).coerceIn(0f, 1f)
