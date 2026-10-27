package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Labelled text field: label above, outlined input, error text below in the danger text colour
 * (announced politely when it appears). The label is also the field's accessible name.
 */
@Composable
fun WaiveraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = if (placeholder != null) {
                { Text(text = placeholder, color = colors.textSecondary) }
            } else {
                null
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = errorText != null,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            shape = WaiveraShapes.Field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surfaceMuted,
                errorContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.borderStrong,
                errorBorderColor = colors.dangerText,
                cursorColor = colors.accent,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
                modifier = Modifier
                    .padding(horizontal = WaiveraSpacing.ExtraSmall)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraTextFieldPreview() {
    WaiveraTheme {
        Column(
            Modifier.padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
        ) {
            WaiveraTextField(value = "", onValueChange = {}, label = "Email", placeholder = "you@company.com")
            WaiveraTextField(
                value = "secret",
                onValueChange = {},
                label = "Password",
                errorText = "That password didn't work. Try again.",
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = { WaiveraIconButton(
                    icon = WaiveraIcons.Search,
                    contentDescription = "Show password",
                    onClick = {},
                ) },
            )
        }
    }
}
