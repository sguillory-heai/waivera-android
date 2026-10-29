package com.waivera.waivera.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import kotlin.math.roundToInt

/**
 * Gives a control drawn smaller than a touch target (a header's "3 members" line) a touch target
 * at least [minHeight] tall without changing the layout around it. The control after this modifier
 * is measured at least [minHeight] tall, so its press, ripple and hit area span the whole target,
 * while the parent is given only the content's own height and the target overhangs it evenly
 * above and below, so no gap opens next to it (v7 follow-up, item 3). Unlike
 * `minimumInteractiveComponentSize`, which grows the layout, nothing beside the control moves.
 *
 * Put it first in the control's modifier chain. The content should centre itself vertically in
 * the taller box; its own height is read as its minimum intrinsic height at the measured width.
 */
fun Modifier.overhangingTouchTarget(minHeight: Dp = WaiveraSpacing.MinTouchTarget): Modifier =
    layout { measurable, constraints ->
        val targetPx = minHeight.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = targetPx))
        val visual = measurable.minIntrinsicHeight(placeable.width).coerceIn(constraints.minHeight, placeable.height)
        // Rounded as a centred child is, so the content lands where it sits without the target.
        val overhang = ((placeable.height - visual) / 2f).roundToInt()
        layout(placeable.width, visual) { placeable.place(0, -overhang) }
    }
