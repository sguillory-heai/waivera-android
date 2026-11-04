package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.pressFeedback
import com.waivera.waivera.core.designsystem.component.rememberPressFeedback
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The casework Delays chip. Its label names the current choice and it fills when delays are
 * hidden or isolated; a tap opens the three choices, and picking one closes the sheet.
 */
@Composable
internal fun DelaysChip(selected: DelayFilter, onSelect: (DelayFilter) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = selected.chipLabel()
    val description = stringResource(R.string.feature_tasks_delays_description, selected.title())
    WaiveraFilterChip(
        label = label,
        selected = selected != DelayFilter.Show,
        onClick = { open = true },
        leadingIcon = WaiveraIcons.Schedule,
        modifier = modifier.semantics { contentDescription = description },
    )
    if (open) {
        DelaysSheet(
            selected = selected,
            onSelect = {
                open = false
                onSelect(it)
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
internal fun DelaysSheet(selected: DelayFilter, onSelect: (DelayFilter) -> Unit, onDismiss: () -> Unit) {
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.feature_tasks_delays_sheet_title),
    ) {
        DelaysOptions(selected = selected, onSelect = onSelect)
    }
}

@Composable
internal fun DelaysOptions(selected: DelayFilter, onSelect: (DelayFilter) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().selectableGroup().padding(bottom = WaiveraSpacing.Medium)) {
        DelayFilter.entries.forEach { filter ->
            DelayOptionRow(
                title = filter.title(),
                note = filter.note(),
                selected = filter == selected,
                onSelect = { onSelect(filter) },
            )
        }
    }
}

/** A single-choice row as the Settings theme picker draws it; picking never vibrates. */
@Composable
private fun DelayOptionRow(title: String, note: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = WaiveraTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactions, depth = PressDepth.Row)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .pressFeedback(press)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun DelayFilter.chipLabel(): String = stringResource(
    when (this) {
        DelayFilter.Show -> R.string.feature_tasks_delays_show
        DelayFilter.Hide -> R.string.feature_tasks_delays_hide
        DelayFilter.Only -> R.string.feature_tasks_delays_only
    },
)

@Composable
private fun DelayFilter.title(): String = stringResource(
    when (this) {
        DelayFilter.Show -> R.string.feature_tasks_delays_show_title
        DelayFilter.Hide -> R.string.feature_tasks_delays_hide_title
        DelayFilter.Only -> R.string.feature_tasks_delays_only_title
    },
)

@Composable
private fun DelayFilter.note(): String = stringResource(
    when (this) {
        DelayFilter.Show -> R.string.feature_tasks_delays_show_note
        DelayFilter.Hide -> R.string.feature_tasks_delays_hide_note
        DelayFilter.Only -> R.string.feature_tasks_delays_only_note
    },
)

@WaiveraPreviews
@Composable
private fun DelaysOptionsPreview() {
    WaiveraTheme { DelaysOptions(selected = DelayFilter.Show, onSelect = {}) }
}
