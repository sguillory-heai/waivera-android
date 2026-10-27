package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
 */
@Composable
fun WaiveraSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(WaiveraShapes.Button)
            .background(colors.surfaceMuted)
            .padding(WaiveraSpacing.ExtraSmall)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) colors.surface else colors.surfaceMuted,
                animationSpec = WaiveraTheme.motion.state(),
                label = "segmentBackground",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = WaiveraSizes.SegmentMinHeight)
                    .clip(WaiveraShapes.Chip)
                    .background(background)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) {
                                haptics.selection()
                                onSelect(index)
                            }
                        },
                    )
                    .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.text else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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
