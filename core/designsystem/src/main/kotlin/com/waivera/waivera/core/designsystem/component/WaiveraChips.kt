package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Filter chip (All, Mentions, Unread...). Selecting never vibrates; the fill and check answer it. [leadingIcon]
 * is shown at rest; a selected chip always leads with a check.
 *
 * v5 decision 5, option A (owner pick), shared with [WaiveraSegmentedControl] and the tab pill:
 * the selected chip is the solid violet selected fill with white ink, the bold weight and the
 * check, so the choice reads by its fill, its weight and its glyph (UI8-13). It is flat, with
 * none of a button's sheen or glow. At rest the chip is the surface with a hairline. It draws
 * 36dp tall and keeps a 48dp target.
 */
@Composable
fun WaiveraFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val shape = WaiveraShapes.FilterChip
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    val filled = selected && enabled
    // Picking or clearing a chip moves its fill, outline and ink on the state token, and the check
    // pops in; at rest each state draws exactly as before.
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textSecondary
            filled -> colors.onSelectedFill
            else -> colors.text
        },
        animationSpec = motion.state(),
        label = "chipContent",
    )
    val fillColor by animateColorAsState(
        targetValue = when {
            filled -> colors.selectedFill
            selected -> colors.surfaceMuted
            else -> colors.surface
        },
        animationSpec = motion.state(),
        label = "chipFill",
    )
    val outlineColor by animateColorAsState(
        targetValue = when {
            filled -> Color.Transparent
            selected -> colors.borderStrong
            else -> colors.border
        },
        animationSpec = motion.state(),
        label = "chipOutline",
    )
    val outlineWidth = if (selected && !filled) WaiveraSizes.SelectedOutline else WaiveraSpacing.Hairline
    val icon = if (filled) WaiveraIcons.Check else leadingIcon
    val iconPop = rememberPopScale(key = icon, enabled = filled)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressFeedback(press, enabled = enabled)
            .heightIn(min = WaiveraSizes.FilterChipHeight)
            .background(color = fillColor, shape = shape)
            .border(width = outlineWidth, color = outlineColor, shape = shape)
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = WaiveraSpacing.MediumSmall + WaiveraSpacing.XxSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(WaiveraSizes.FilterChipIcon)
                    .popScale(iconPop),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (filled) FontWeight.Bold else null,
            color = content,
            modifier = Modifier.padding(vertical = WaiveraSpacing.ExtraSmall),
        )
    }
}

/**
 * Segmented control (Day | Week | Month, All | Unread | Channels). Each segment is a radio-style
 * selectable with a 48dp minimum height; the selected solid violet fill animates with the state token.
 *
 * Labels are always one line. Segments share the width equally while the longest label fits.
 * When it does not (large font) but all labels fit side by side, each segment's share follows
 * its label, so the row still fills edge to edge (UI2-25). Only when even that overflows do the
 * segments size to their labels and the row scroll sideways instead of breaking a word.
 */
@Composable
fun WaiveraSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fit = rememberSegmentFit(options, constraints.maxWidth)
        val scrolls = fit is SegmentFit.Scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WaiveraShapes.SegmentTrack)
                .background(colors.surfaceMuted)
                .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(WaiveraSpacing.ExtraSmall)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        ) {
            options.forEachIndexed { index, option ->
                Segment(
                    label = option,
                    selected = index == selectedIndex,
                    onSelect = { onSelect(index) },
                    modifier = when (fit) {
                        SegmentFit.Equal -> Modifier.weight(1f)
                        is SegmentFit.Proportional -> Modifier.weight(fit.weights[index])
                        SegmentFit.Scroll -> Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val pill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = WaiveraTheme.motion.state(),
        label = "segmentPill",
    )
    val fill = colors.selectedFill
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    Box(
        modifier = modifier
            .pressFeedback(press)
            .heightIn(min = WaiveraSizes.SegmentMinHeight)
            .clip(WaiveraShapes.Segment)
            // The chosen segment is the solid violet with white ink in the heavier weight. In dark the
            // fill is 3.0:1 on the raised track, a hair under the line, so the ink and weight carry it
            // too. It fades in on the state token and snaps under reduced motion.
            .drawBehind { if (pill > 0f) drawRect(color = fill, alpha = pill) }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = { if (!selected) onSelect() },
            )
            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.Small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) colors.onSelectedFill else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Measures the labels once per option set, style and density, then applies [segmentFit]. */
@Composable
private fun rememberSegmentFit(options: List<String>, availableWidthPx: Int): SegmentFit {
    val measurer = rememberTextMeasurer()
    // Measured at the selected weight, the widest, so a chosen label never outgrows its share.
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
    val density = LocalDensity.current
    val labelWidthsPx = remember(options, style, density) {
        options.map { measurer.measure(text = it, style = style).size.width }
    }
    val segmentPaddingPx = with(density) { (WaiveraSpacing.Small * 2).roundToPx() }
    val chromePx = with(density) { (WaiveraSpacing.ExtraSmall * (options.size + 1)).roundToPx() }
    return segmentFit(labelWidthsPx.map { it + segmentPaddingPx }, availableWidthPx - chromePx)
}

/** How the segments share the row; [Proportional] weights follow each segment's own width. */
internal sealed interface SegmentFit {
    data object Equal : SegmentFit

    data class Proportional(val weights: List<Float>) : SegmentFit

    data object Scroll : SegmentFit
}

/** Equal while the widest segment fits its equal share, proportional while all fit, else scroll. */
internal fun segmentFit(segmentWidthsPx: List<Int>, availableWidthPx: Int): SegmentFit {
    val widest = segmentWidthsPx.maxOrNull() ?: return SegmentFit.Equal
    return when {
        !segmentsNeedScroll(segmentWidthsPx.size, widest, availableWidthPx) -> SegmentFit.Equal
        segmentWidthsPx.sumOf(Int::toLong) <= availableWidthPx ->
            SegmentFit.Proportional(segmentWidthsPx.map { it.coerceAtLeast(1).toFloat() })
        else -> SegmentFit.Scroll
    }
}

/** True when [count] equal segments of [segmentWidthPx] do not fit in [availableWidthPx]. */
internal fun segmentsNeedScroll(count: Int, segmentWidthPx: Int, availableWidthPx: Int): Boolean =
    count > 0 && segmentWidthPx.toLong() * count > availableWidthPx

@WaiveraPreviews
@Composable
private fun WaiveraChipsPreview() {
    WaiveraTheme {
        Column(
            Modifier.padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraFilterChip(label = "All", selected = true, onClick = {})
                WaiveraFilterChip(label = "Mentions", selected = false, onClick = {})
                WaiveraFilterChip(label = "Unread", selected = true, onClick = {}, leadingIcon = WaiveraIcons.Check)
            }
            WaiveraSegmentedControl(options = listOf("Day", "Week", "Month"), selectedIndex = 1, onSelect = {})
        }
    }
}
