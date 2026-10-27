package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.delay

/** Skeleton pulse: opacity 0.45 to 0.85 over 650ms, looped. Static at the midpoint when motion is reduced. */
@Composable
fun rememberSkeletonAlpha(): Float {
    val motion = WaiveraTheme.motion
    if (!motion.allowsLoopingMotion) return SKELETON_STATIC_ALPHA
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = SKELETON_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    return alpha
}

/** A skeleton block. Compose these into shapes that match the final layout. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = WaiveraShapes.Chip,
    alpha: Float = rememberSkeletonAlpha(),
) {
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .background(WaiveraTheme.colors.surfaceMuted, shape),
    )
}

@Composable
fun SkeletonLine(modifier: Modifier = Modifier, widthFraction: Float = 1f, alpha: Float = rememberSkeletonAlpha()) {
    SkeletonBlock(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(WaiveraSizes.SkeletonLine),
        shape = WaiveraShapes.Chip,
        alpha = alpha,
    )
}

@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier, alpha: Float = rememberSkeletonAlpha()) {
    SkeletonBlock(modifier = modifier.size(size), shape = WaiveraShapes.Circle, alpha = alpha)
}

/** A list row skeleton (avatar, two lines), matching [WaiveraListRow] with a leading avatar. */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier, alpha: Float = rememberSkeletonAlpha()) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(size = WaiveraSizes.AvatarMedium, alpha = alpha)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            SkeletonLine(widthFraction = HEADLINE_FRACTION, alpha = alpha)
            SkeletonLine(widthFraction = SUPPORTING_FRACTION, alpha = alpha)
        }
    }
}

/** A loading list made of [rows] skeleton rows that share one pulse, announced once as "Loading". */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = DEFAULT_ROWS) {
    val alpha = rememberSkeletonAlpha()
    val loading = stringResource(R.string.core_designsystem_loading)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = loading }) {
        repeat(rows) { SkeletonListRow(alpha = alpha) }
    }
}

/**
 * Whether a skeleton should be on screen for [loading]: only after [delayMillis] (fast loads
 * never flash one) and then for at least [minVisibleMillis] (no flicker when data lands just
 * after it appears).
 */
@Composable
fun rememberSkeletonVisible(
    loading: Boolean,
    delayMillis: Long = SKELETON_DELAY_MILLIS,
    minVisibleMillis: Long = SKELETON_MIN_VISIBLE_MILLIS,
): Boolean {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading) {
            if (!visible) {
                delay(delayMillis)
                visible = true
                shownAt = System.currentTimeMillis()
            }
        } else if (visible) {
            val remaining = minVisibleMillis - (System.currentTimeMillis() - shownAt)
            if (remaining > 0) delay(remaining)
            visible = false
        }
    }
    return visible
}

private const val SKELETON_MIN_ALPHA = 0.45f
private const val SKELETON_MAX_ALPHA = 0.85f
private const val SKELETON_STATIC_ALPHA = 0.65f
private const val SKELETON_PULSE_MILLIS = 650
private const val HEADLINE_FRACTION = 0.6f
private const val SUPPORTING_FRACTION = 0.85f
private const val DEFAULT_ROWS = 6
const val SKELETON_DELAY_MILLIS = 300L
const val SKELETON_MIN_VISIBLE_MILLIS = 300L

@WaiveraPreviews
@Composable
private fun SkeletonPreview() {
    WaiveraTheme {
        SkeletonList(rows = 3)
    }
}
