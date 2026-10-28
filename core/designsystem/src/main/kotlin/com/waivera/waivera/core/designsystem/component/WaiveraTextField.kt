package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.launch

/**
 * Labelled text field: label above, input below, error text below that in the danger text colour
 * (announced politely when it appears). The label is also the field's accessible name.
 *
 * Concept A's field: 56dp, 16dp corners, the soft field fill with a tinted edge at rest; on focus
 * the edge turns accent, the fill lifts to the raised step and a 4dp accent halo rings it. The
 * resting edge is darker than the concept's hairline so the field's boundary keeps 3:1.
 *
 * The whole field (label, input and error) scrolls into view when it gains focus or an error
 * appears, so in a scrolling form the field the person has to fix is never under the actions or
 * the keyboard.
 *
 * @param placeholder required: every field shows helpful example text while empty (for example
 *   "you@organization.com" or "Enter your password"), so the person knows what goes in it.
 * @param contentType autofill hint for password managers, set on the input itself (for example
 *   `ContentType.EmailAddress + ContentType.Username`, `ContentType.Password` or
 *   `ContentType.NewPassword`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaiveraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    contentType: ContentType? = null,
) {
    val colors = WaiveraTheme.colors
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val halo = when {
        !focused -> Color.Transparent
        errorText != null -> colors.dangerText.copy(alpha = FOCUS_HALO_ALPHA)
        else -> colors.accent.copy(alpha = FOCUS_HALO_ALPHA)
    }
    LaunchedEffect(errorText) {
        if (errorText != null) bringIntoView.bringIntoView()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSizes.FieldHeight)
                .focusHalo(color = halo)
                .onFocusChanged { focus ->
                    focused = focus.isFocused
                    if (focus.isFocused) scope.launch { bringIntoView.bringIntoView() }
                }
                .semantics {
                    contentDescription = label
                    if (contentType != null) this.contentType = contentType
                },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            // The placeholder lays out like the text that replaces it, in the same style and, for
            // a single-line field, on one line, so the field keeps its height on the first
            // keystroke. With the decoration's own style it was 9dp taller at 200% (UI7-02).
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
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
                focusedContainerColor = colors.raised,
                unfocusedContainerColor = colors.field,
                disabledContainerColor = colors.surfaceMuted,
                errorContainerColor = colors.raised,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.fieldBorder,
                disabledBorderColor = colors.border,
                focusedLeadingIconColor = colors.accent,
                unfocusedLeadingIconColor = colors.textSecondary,
                errorLeadingIconColor = colors.dangerText,
                focusedTrailingIconColor = colors.textSecondary,
                unfocusedTrailingIconColor = colors.textSecondary,
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

/**
 * The focus halo: a ring [WaiveraSizes.FieldHalo] wide just outside the field's edge. It is drawn
 * behind the field, so the half under the field is hidden by its fill.
 */
private fun Modifier.focusHalo(color: Color): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val reach = WaiveraSizes.FieldHalo.toPx()
    val outline = WaiveraShapes.Field.createOutline(
        size = Size(size.width + reach * 2, size.height + reach * 2),
        layoutDirection = layoutDirection,
        density = this,
    )
    translate(left = -reach, top = -reach) { drawOutline(outline = outline, color = color) }
}

private const val FOCUS_HALO_ALPHA = 0.16f

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
                placeholder = "Enter your password",
                errorText = "That password didn't work. Try again.",
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    WaiveraIconButton(
                        icon = WaiveraIcons.Search,
                        contentDescription = "Show password",
                        onClick = {},
                    )
                },
            )
        }
    }
}
