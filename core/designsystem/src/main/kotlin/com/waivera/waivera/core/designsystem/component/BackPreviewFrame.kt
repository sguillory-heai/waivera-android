package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.lerp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * The M3 back preview around one navigation entry (UI3-10, UI7-01). While a predictive back
 * swipe is in progress the screen being dismissed shrinks to
 * [WaiveraMotionScheme.BACK_PREVIEW_SCALE], moves away from the swipe's edge, rounds its
 * corners to [WaiveraSizes.BackPreviewCorner] and gets a hairline outline, so its edge reads
 * on a light theme where both screens share one background. The screen being revealed sits a
 * little towards the swipe's edge and is dimmed by a scrim that lifts as the swipe goes on.
 *
 * Everything is drawn from [gesture], mapped through [GestureMotion.progress],
 * so the preview follows the finger evenly; the transition Navigation 3 seeks only keeps both
 * screens composed ([WaiveraMotionScheme.predictiveBackTransition]). Reading the preview back
 * from a seeked transition gave values that depended on when each animation was set up: the
 * springs finished early in the swipe on a device, and the Robolectric capture showed only end
 * values (UI6-01, UI7-01). After the finger lifts, [GestureMotion.settle] takes
 * the preview from where it was to its end: on a commit, the leaving card to its full shrink
 * and the revealed screen to rest; on a cancel, both back to where they started.
 *
 * [isTop] says whether the entry is the top of the back stack. During a swipe the stack has not
 * changed yet, so the top entry is the one leaving and the one below it is the one revealed.
 * The role is taken when the swipe starts and kept through the settle, so committing (which
 * changes the stack) does not drop the frame while the screen finishes leaving. An entry that
 * was not on screen when the swipe started, or any entry in an ordinary push or pop, draws
 * nothing extra.
 *
 * [gesture] is read in effects and at draw time, so a swipe redraws the frame without
 * recomposing [content]. Reduced motion holds the leaving screen still and full size, so there
 * is nothing to frame.
 */
@Composable
fun BackPreviewFrame(
    gesture: () -> BackGesture,
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
    val outlineColor = WaiveraTheme.colors.border
    val currentIsTop by rememberUpdatedState(isTop)
    val currentGesture by rememberUpdatedState(gesture)
    val preview = remember {
        // An entry composed mid-swipe is the screen being revealed; take its role now, so its
        // first frame is already dimmed and offset.
        BackPreviewState(initial = Snapshot.withoutReadObservation { gesture() }, isTop = isTop)
    }
    LaunchedEffect(preview, motion) {
        snapshotFlow { currentGesture() }.collectLatest { next -> preview.follow(next, currentIsTop, motion) }
    }
    // The preview wraps the caller's modifier, so everything it draws (and the bounds tests and
    // accessibility read from it) is the card where it is drawn.
    Box(
        modifier = Modifier
            .graphicsLayer {
                val geometry = preview.geometry(currentGesture(), motion)
                scaleX = geometry.scale
                scaleY = geometry.scale
                translationX = geometry.shift * size.width
                val corner = lerp(WaiveraSpacing.None, WaiveraSizes.BackPreviewCorner, geometry.corner)
                shape = RoundedCornerShape(corner)
                clip = corner > WaiveraSpacing.None
            }
            .drawWithContent {
                drawContent()
                val geometry = preview.geometry(currentGesture(), motion)
                if (geometry.scrim > 0f) drawRect(color = scrimColor, alpha = geometry.scrim)
                if (geometry.outlined) {
                    val stroke = WaiveraSpacing.Hairline.toPx()
                    val radius = lerp(WaiveraSpacing.None, WaiveraSizes.BackPreviewCorner, geometry.corner).toPx()
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius((radius - stroke / 2).coerceAtLeast(0f)),
                        style = Stroke(width = stroke),
                    )
                }
            }
            .then(modifier),
    ) {
        content()
    }
}

/** Which screen of a back swipe a frame is showing, taken when the swipe starts. */
internal enum class BackPreviewRole { None, Leaving, Revealed }

/**
 * The preview's role and its settle after the finger lifts. While the finger is down the
 * preview is read straight from the gesture, so it never lags a frame behind it.
 */
@Stable
private class BackPreviewState(initial: BackGesture, isTop: Boolean) {
    var role by mutableStateOf(if (initial is BackGesture.Dragging) roleFor(isTop) else BackPreviewRole.None)
        private set
    var swipeFromRight by mutableStateOf((initial as? BackGesture.Dragging)?.swipeFromRight ?: false)
        private set
    private val settle = Animatable(0f)
    private var lastFraction = 0f
    private var dragging = initial is BackGesture.Dragging

    fun geometry(gesture: BackGesture, motion: WaiveraMotionScheme): BackPreviewGeometry {
        val fraction = if (gesture is BackGesture.Dragging && role != BackPreviewRole.None) {
            motion.gesture.progress.transform(gesture.progress.coerceIn(0f, 1f))
        } else {
            settle.value
        }
        return backPreviewGeometry(role = role, fraction = fraction, swipeFromRight = swipeFromRight)
    }

    suspend fun follow(gesture: BackGesture, isTop: Boolean, motion: WaiveraMotionScheme) {
        when (gesture) {
            is BackGesture.Dragging -> {
                // A new swipe takes the role afresh, even over the settle of the last one: after
                // a commit, the screen that was revealed is the top and leaves next.
                if (!dragging) {
                    dragging = true
                    role = roleFor(isTop)
                    swipeFromRight = gesture.swipeFromRight
                }
                lastFraction = motion.gesture.progress.transform(gesture.progress.coerceIn(0f, 1f))
                settle.snapTo(lastFraction)
            }
            BackGesture.Committed -> release(target = 1f, motion = motion)
            BackGesture.Cancelled -> release(target = 0f, motion = motion)
            BackGesture.Idle -> dragging = false
        }
    }

    private suspend fun release(target: Float, motion: WaiveraMotionScheme) {
        val wasDragging = dragging
        dragging = false
        if (!wasDragging || role == BackPreviewRole.None) return
        settle.snapTo(lastFraction)
        val remaining = if (target == 1f) 1f - lastFraction else lastFraction
        settle.animateTo(target, motion.gesture.settle(remaining))
        // Where the end is the screen at rest, the frame lets go. A card that committed stays at
        // its full shrink until Navigation 3 removes it, and a cancelled revealed screen stays
        // dimmed under the full-size card until it goes, so neither jumps back for a frame.
        val atRest = (role == BackPreviewRole.Leaving && target == 0f) ||
            (role == BackPreviewRole.Revealed && target == 1f)
        if (atRest) {
            role = BackPreviewRole.None
            settle.snapTo(0f)
        }
    }

    private fun roleFor(isTop: Boolean) = if (isTop) BackPreviewRole.Leaving else BackPreviewRole.Revealed
}

/**
 * The preview's shape at one moment: [scale] of the screen, [shift] as a share of its width
 * (positive is rightward), [corner] from 0 (square) to 1 ([WaiveraSizes.BackPreviewCorner]),
 * [scrim] as a share of the theme scrim, and whether the card is [outlined].
 */
internal data class BackPreviewGeometry(
    val scale: Float,
    val shift: Float,
    val corner: Float,
    val scrim: Float,
    val outlined: Boolean,
) {
    companion object {
        val Rest = BackPreviewGeometry(scale = 1f, shift = 0f, corner = 0f, scrim = 0f, outlined = false)
    }
}

/**
 * The back preview at [fraction] of the way (0 when the swipe starts, 1 at its full travel).
 * The leaving card shrinks to [WaiveraMotionScheme.BACK_PREVIEW_SCALE] and moves away from the
 * swipe's edge by half the scale gap, so its far side stays on the display edge; its corners
 * round and it is outlined as soon as it has moved. The revealed screen starts a tenth of its
 * width towards the swipe's edge, fully dimmed, and reaches rest at the end.
 */
internal fun backPreviewGeometry(role: BackPreviewRole, fraction: Float, swipeFromRight: Boolean): BackPreviewGeometry {
    val f = fraction.coerceIn(0f, 1f)
    val away = if (swipeFromRight) -1f else 1f
    return when (role) {
        BackPreviewRole.None -> BackPreviewGeometry.Rest
        BackPreviewRole.Leaving -> BackPreviewGeometry(
            scale = 1f - (1f - WaiveraMotionScheme.BACK_PREVIEW_SCALE) * f,
            shift = away * f * (1f - WaiveraMotionScheme.BACK_PREVIEW_SCALE) / 2f,
            corner = f,
            scrim = 0f,
            outlined = f > 0f,
        )
        BackPreviewRole.Revealed -> BackPreviewGeometry(
            scale = 1f,
            // Adding 0 turns -0.0 at rest into 0.0, so the settled screen equals Rest.
            shift = -away * (1f - f) * BACK_PARALLAX + 0f,
            corner = 0f,
            scrim = 1f - f,
            outlined = false,
        )
    }
}

/** How far the revealed screen starts towards the swipe's edge, as a share of its width. */
private const val BACK_PARALLAX = 0.1f
