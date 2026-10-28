package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

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
    val corner by transition.animateDp(transitionSpec = { motion.navigate() }, label = "backPreviewCorner") { state ->
        if (topDuringTransition && state == EnterExitState.PostExit) {
            WaiveraSizes.BackPreviewCorner
        } else {
            WaiveraSpacing.None
        }
    }
    val scrim by transition.animateFloat(transitionSpec = { motion.navigate() }, label = "backPreviewScrim") { state ->
        if (!topDuringTransition && state == EnterExitState.PreEnter) 1f else 0f
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                shape = RoundedCornerShape(corner)
                clip = corner > WaiveraSpacing.None
            }
            .drawWithContent {
                drawContent()
                if (scrim > 0f) drawRect(color = scrimColor, alpha = scrim)
            },
    ) {
        content()
    }
}
