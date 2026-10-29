package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.delay

/**
 * Skeleton pulse position: 0 is the `skeletonBase` colour token, 1 is `skeletonHighlight`,
 * breathing on the eased [skeletonPulse][com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme.skeletonPulse]
 * (M1-16). Returned as state that the bones read in their draw blocks, so a pulsing skeleton
 * redraws without recomposing every frame. Reduced motion holds the base colour, so the static
 * frame is still clearly visible.
 */
@Composable
fun rememberSkeletonPulse(): State<Float> {
    val motion = WaiveraTheme.motion
    if (!motion.allowsLoopingMotion) return StaticPulse
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = motion.skeletonPulse,
        label = "skeletonPulse",
    )
}

/** The pulse with animations off: the base colour, held. */
private object StaticPulse : State<Float> {
    override val value: Float = SKELETON_STATIC_PULSE
}

/** A skeleton block. Compose these into shapes that match the final layout. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = WaiveraShapes.Chip,
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    val colors = WaiveraTheme.colors
    Box(
        modifier = modifier.drawBehind {
            val color = lerp(colors.skeletonBase, colors.skeletonHighlight, pulse.value)
            drawOutline(shape.createOutline(size, layoutDirection, this), color)
        },
    )
}

@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    SkeletonBlock(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(WaiveraSizes.SkeletonLine),
        shape = WaiveraShapes.Chip,
        pulse = pulse,
    )
}

/**
 * The height one line of [style] takes on screen, at the current font scale. Skeleton rows are
 * built from it so a loading row is as tall as the text row it stands in for, at 100% and 200%.
 */
@Composable
fun skeletonLineHeight(style: TextStyle): Dp {
    val density = LocalDensity.current
    return with(density) {
        when {
            style.lineHeight.isSp -> style.lineHeight.toDp()
            style.fontSize.isSp -> style.fontSize.toDp() * FALLBACK_LINE_HEIGHT_RATIO
            else -> WaiveraSizes.SkeletonLine
        }
    }
}

/**
 * A bone for one line of text in [style]: it takes the line's full height, so stacked bones keep
 * the real text's rhythm, and draws a rounded bar through the middle of it.
 */
@Composable
fun SkeletonText(
    style: TextStyle,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    val line = skeletonLineHeight(style)
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(line),
        contentAlignment = Alignment.CenterStart,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(line * TEXT_BONE_RATIO),
            shape = WaiveraShapes.Chip,
            pulse = pulse,
        )
    }
}

/** An avatar's bone, in the one avatar shape, so loading rows match what lands. */
@Composable
fun SkeletonAvatar(size: Dp, modifier: Modifier = Modifier, pulse: State<Float> = rememberSkeletonPulse()) {
    SkeletonBlock(modifier = modifier.size(size), shape = WaiveraShapes.avatar(size), pulse = pulse)
}

/** A list row skeleton (avatar, two lines), matching [WaiveraListRow] with a leading avatar. */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier, pulse: State<Float> = rememberSkeletonPulse()) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonAvatar(size = WaiveraSizes.AvatarMedium, pulse = pulse)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            SkeletonLine(widthFraction = HEADLINE_FRACTION, pulse = pulse)
            SkeletonLine(widthFraction = SUPPORTING_FRACTION, pulse = pulse)
        }
    }
}

/** A loading list made of [rows] skeleton rows that share one pulse, announced once as "Loading". */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = DEFAULT_ROWS) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.core_designsystem_loading)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = loading }) {
        repeat(rows) { SkeletonListRow(pulse = pulse) }
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
    delayMillis: Long = LocalWaiveraSkeletonDelay.current,
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

private const val SKELETON_STATIC_PULSE = 0f
private const val HEADLINE_FRACTION = 0.6f
private const val SUPPORTING_FRACTION = 0.85f
private const val DEFAULT_ROWS = 6

/** The bar fills this much of its line, like a run of lowercase and capital letters. */
private const val TEXT_BONE_RATIO = 0.6f

/** Line height for a style that only sets its font size: the platform default leading. */
private const val FALLBACK_LINE_HEIGHT_RATIO = 1.2f
const val SKELETON_DELAY_MILLIS = 300L
const val SKELETON_MIN_VISIBLE_MILLIS = 300L

/**
 * The default skeleton delay for [rememberSkeletonVisible]. Screenshot tests provide 0 so the
 * loading state under test is the skeleton, not the blank first 300 ms.
 */
val LocalWaiveraSkeletonDelay = staticCompositionLocalOf { SKELETON_DELAY_MILLIS }

@WaiveraPreviews
@Composable
private fun SkeletonPreview() {
    WaiveraTheme {
        SkeletonList(rows = 3)
    }
}
