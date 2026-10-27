package com.waivera.waivera.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** A group of settings rows on a surface card. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter)
            .background(colors.surface, WaiveraShapes.ListCard)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.ListCard)
            .padding(vertical = WaiveraSpacing.ExtraSmall),
        content = content,
    )
}

/**
 * A radio-style row: title, subtitle and a check on the selected one. Announced as a radio
 * button with its selected state; picking fires the selection haptic.
 */
@Composable
internal fun SettingsRadioRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) haptics.selection()
                    onSelect()
                },
            )
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (selected) {
            Icon(
                painter = painterResource(WaiveraIcons.Check),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        } else {
            Spacer(Modifier.size(WaiveraSizes.Icon))
        }
    }
}

/** Divider inset to the row text, between rows in a [SettingsCard]. */
@Composable
internal fun SettingsRowDivider() {
    WaiveraDivider(modifier = Modifier.padding(start = WaiveraSpacing.Gutter))
}

@WaiveraPreviews
@Composable
private fun SettingsComponentsPreview() {
    WaiveraTheme {
        SettingsCard {
            SettingsRadioRow(title = "System", subtitle = "Match your device", selected = true, onSelect = {})
            SettingsRowDivider()
            SettingsRadioRow(title = "Dark", subtitle = null, selected = false, onSelect = {})
        }
    }
}
