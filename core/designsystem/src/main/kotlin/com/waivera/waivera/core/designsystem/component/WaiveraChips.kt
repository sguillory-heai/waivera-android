package com.waivera.waivera.core.designsystem.component

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Filter chip (All, Mentions, Unread...). Selecting fires the selection haptic. */
@Composable
fun WaiveraFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        shape = WaiveraShapes.Chip,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface,
            labelColor = colors.text,
            selectedContainerColor = colors.accentSoft,
            selectedLabelColor = colors.onAccentSoft,
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
 * Labels are always one line. When equal-width segments cannot fit the longest label (large
 * font scales), the segments size to their labels and the row scrolls sideways instead of
 * breaking a word.
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
        val scrolls = rememberSegmentsScroll(options, constraints.maxWidth)
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
                    modifier = if (scrolls) Modifier else Modifier.weight(1f),
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

/** Measures the labels once per option set, style and density, then applies [segmentsNeedScroll]. */
@Composable
private fun rememberSegmentsScroll(options: List<String>, availableWidthPx: Int): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val widestLabelPx = remember(options, style, density) {
        options.maxOfOrNull { measurer.measure(text = it, style = style).size.width } ?: 0
    }
    val segmentPaddingPx = with(density) { (WaiveraSpacing.Small * 2).roundToPx() }
    val chromePx = with(density) { (WaiveraSpacing.ExtraSmall * (options.size + 1)).roundToPx() }
    return segmentsNeedScroll(options.size, widestLabelPx + segmentPaddingPx, availableWidthPx - chromePx)
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
            }
            WaiveraSegmentedControl(options = listOf("Day", "Week", "Month"), selectedIndex = 1, onSelect = {})
        }
    }
}
