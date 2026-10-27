package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.waivera.waivera.core.designsystem.component.SKELETON_DELAY_MILLIS
import com.waivera.waivera.core.designsystem.component.SkeletonLine
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** A titled card on the profile screens. The title is a heading for TalkBack navigation. */
@Composable
internal fun ProfileCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter)
            .background(colors.surface, WaiveraShapes.ListCard)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.ListCard)
            .padding(vertical = WaiveraSpacing.MediumSmall),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                modifier = Modifier
                    .padding(horizontal = WaiveraSpacing.Medium)
                    .semantics { heading() },
            )
        }
        content()
    }
}

/**
 * A read-only label and value. Missing values show an em dash so every row keeps its place,
 * and TalkBack reads the pair as one item ("Phone, not provided").
 */
@Composable
internal fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val shown = value?.takeIf { it.isNotBlank() }
    val missing = stringResource(R.string.feature_profile_value_missing)
    val spoken = stringResource(R.string.feature_profile_value_missing_a11y)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.ExtraSmall)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, ${shown ?: spoken}"
            },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
    ) {
        Text(text = label.uppercase(), style = WaiveraTextStyles.Eyebrow, color = colors.textSecondary)
        Text(text = shown ?: missing, style = MaterialTheme.typography.bodyLarge, color = colors.text)
    }
}

/** Joins a list for one row; empty lists read as missing. */
internal fun List<String>.joinedOrNull(): String? =
    map(String::trim).filter(String::isNotEmpty).takeIf { it.isNotEmpty() }?.joinToString(", ")

/**
 * How long a load runs before its skeleton shows (spec 10 §3). Screenshot tests set 0 so the
 * loading state can be captured without waiting on a clock.
 */
internal val LocalSkeletonDelayMillis = staticCompositionLocalOf { SKELETON_DELAY_MILLIS }

/** [rememberSkeletonVisible] with this module's delay. */
@Composable
internal fun rememberDelayedSkeleton(loading: Boolean): Boolean =
    rememberSkeletonVisible(loading = loading, delayMillis = LocalSkeletonDelayMillis.current)

/** Placeholder lines for a section still loading (shown only after the skeleton delay). */
@Composable
internal fun SectionSkeleton(modifier: Modifier = Modifier, lines: Int = 3) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_profile_loading_section)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Medium)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        repeat(lines) { index ->
            SkeletonLine(widthFraction = if (index % 2 == 0) WIDE_LINE else NARROW_LINE, pulse = pulse)
        }
    }
}

/** Search field for the people and bookmark lists, with a clear button once there is text. */
@Composable
internal fun ProfileSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
            .semantics { contentDescription = placeholder },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(text = placeholder, color = colors.textSecondary) },
        leadingIcon = {
            Icon(
                painter = painterResource(WaiveraIcons.Search),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                WaiveraIconButton(
                    icon = WaiveraIcons.Close,
                    contentDescription = clearDescription,
                    onClick = { onValueChange("") },
                    tint = colors.textSecondary,
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = WaiveraShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            cursorColor = colors.accent,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
        ),
    )
}

private const val WIDE_LINE = 0.8f
private const val NARROW_LINE = 0.55f

@WaiveraPreviews
@Composable
private fun ProfileComponentsPreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            ProfileSearchField(
                value = "pri",
                onValueChange = {},
                placeholder = "Search people",
                clearDescription = "Clear",
            )
            ProfileCard(title = "Personal information") {
                InfoRow(label = "Email", value = "alex.rivera@example.com")
                InfoRow(label = "Phone", value = null)
                SectionSkeleton()
            }
        }
    }
}
