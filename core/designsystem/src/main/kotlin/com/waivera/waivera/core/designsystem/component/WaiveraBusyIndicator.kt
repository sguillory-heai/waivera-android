package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The app's busy indicator: an indeterminate spinner, or a still ring when motion is reduced.
 * v5 decision 6: a solid violet arc on the lilac tonal track, never a gradient. Pass a [color]
 * and [trackColor] of the surface's own ink where it sits on a fill (a busy button).
 *
 * With "Remove animations" the system stops the spinner's infinite transition, which leaves a
 * frozen arc that reads like a tick or a broken icon. Under reduced motion this draws a full,
 * muted ring instead. It never carries meaning on its own: the label next to it (or the
 * control's state description) says what is happening.
 */
@Composable
fun WaiveraBusyIndicator(
    modifier: Modifier = Modifier,
    color: Color = WaiveraTheme.colors.accentFill,
    trackColor: Color = WaiveraTheme.colors.accentSoft,
    size: Dp = WaiveraSizes.ProgressSmall,
    strokeWidth: Dp = WaiveraSizes.ProgressStroke,
) {
    if (WaiveraTheme.motion.reduced) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            drawCircle(
                color = color.copy(alpha = STATIC_RING_ALPHA),
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
        }
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
        )
    }
}

/** A still ring is muted so it reads as "working", not as a selected or complete state. */
private const val STATIC_RING_ALPHA: Float = 0.4f

@WaiveraPreviews
@Composable
private fun WaiveraBusyIndicatorPreview() {
    WaiveraTheme {
        Row(
            modifier = Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraBusyIndicator()
            WaiveraBusyIndicator(size = WaiveraSizes.ProgressLarge, strokeWidth = WaiveraSizes.ProgressStrokeLarge)
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraBusyIndicatorReducedPreview() {
    WaiveraTheme(motion = WaiveraMotionScheme.Reduced) {
        Row(
            modifier = Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraBusyIndicator()
            WaiveraBusyIndicator(size = WaiveraSizes.ProgressLarge, strokeWidth = WaiveraSizes.ProgressStrokeLarge)
        }
    }
}
