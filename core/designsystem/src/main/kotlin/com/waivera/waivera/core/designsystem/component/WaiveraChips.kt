package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Filter chip (All, Mentions, Unread...). Selecting fires the selection haptic. [leadingIcon]
 * marks an on/off toggle (a check while on) so it reads apart from single-choice chips.
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
    val haptics = rememberHaptics()
    val colors = WaiveraTheme.colors
    FilterChip(
        selected = selected,
        onClick = {
            haptics.selection()
            onClick()
        },
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
        shape = WaiveraShapes.Chip,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface,
            labelColor = colors.text,
            selectedContainerColor = colors.accentSoft,
            selectedLabelColor = colors.onAccentSoft,
            selectedLeadingIconColor = colors.onAccentSoft,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = colors.border,
            selectedBorderColor = colors.accentBorderStrong,
        ),
    )
}

/**
 * Segmented control (Day | Week | Month, All | Unread | Channels). Each segment is a radio-style
 * selectable with a 48dp minimum height; the selected wash animates with the state token.
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
                .clip(WaiveraShapes.Button)
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
    val haptics = rememberHaptics()
    val colors = WaiveraTheme.colors
    val background by animateColorAsState(
        targetValue = if (selected) colors.surface else colors.surfaceMuted,
        animationSpec = WaiveraTheme.motion.state(),
        label = "segmentBackground",
    )
    Box(
        modifier = modifier
            .heightIn(min = WaiveraSizes.SegmentMinHeight)
            .clip(WaiveraShapes.Chip)
            .background(background)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = {
                    if (!selected) {
                        haptics.selection()
                        onSelect()
                    }
                },
            )
            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.Small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.text else colors.textSecondary,
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
    val style = MaterialTheme.typography.labelLarge
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
