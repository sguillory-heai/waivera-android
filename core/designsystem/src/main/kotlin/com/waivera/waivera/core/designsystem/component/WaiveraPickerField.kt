package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A field that opens a chooser (a type list, a date or time picker): it looks like
 * [WaiveraTextField] at rest and reads as a button named by its label and value. [value] null
 * shows [placeholder]; [errorText] turns the edge to the danger colour and shows below.
 */
@Composable
fun WaiveraPickerField(
    label: String,
    value: String?,
    placeholder: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall,
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        WaiveraPressable(
            onClick = onClick,
            enabled = enabled,
            onClickLabel = label,
            role = Role.Button,
            shape = WaiveraShapes.Field,
            depth = PressDepth.Card,
            modifier = Modifier.semantics { contentDescription = "$label, ${value ?: placeholder}" },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WaiveraSizes.FieldHeight)
                    .background(colors.field, WaiveraShapes.Field)
                    .border(
                        WaiveraSpacing.Hairline,
                        if (errorText != null) colors.dangerText else colors.fieldBorder,
                        WaiveraShapes.Field,
                    )
                    // Vertical room keeps a value that wraps at large font clear of the border.
                    .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) colors.textSecondary else colors.text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
        }
        if (errorText != null) {
            Text(text = errorText, style = MaterialTheme.typography.bodySmall, color = colors.dangerText)
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraPickerFieldPreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
            WaiveraPickerField(
                label = "When",
                value = "Today, 2:30 PM",
                placeholder = "Pick a time",
                icon = WaiveraIcons.Schedule,
                onClick = {},
            )
            WaiveraPickerField(
                label = "Activity type",
                value = null,
                placeholder = "Choose a type",
                icon = WaiveraIcons.ExpandMore,
                errorText = "Choose an activity type",
                onClick = {},
            )
        }
    }
}
