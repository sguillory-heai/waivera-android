package com.waivera.waivera.feature.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.SKELETON_DELAY_MILLIS
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraAddressText
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.rememberLeadingTileAlignment
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A titled group on the profile screens, as concept A groups rows everywhere: the shared
 * mixed-case section title (a heading for TalkBack) above one lifted sheet card. The [first]
 * group on the sheet starts right under its curve.
 */
@Composable
internal fun ProfileCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) WaiveraSectionHeader(text = title, first = first)
        WaiveraCardGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = WaiveraSpacing.MediumSmall),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                content = content,
            )
        }
    }
}

/** One read-only field of a grouped details card: its label, value and leading glyph. */
@Immutable
internal data class InfoField(
    val label: String,
    val value: String?,
    @param:DrawableRes val icon: Int,
    val address: Boolean = false,
)

/**
 * The grouped details card's fields (v5 decision 7): each on a leading icon tile, with inset
 * dividers between them that start under the text, not under the tile.
 */
@Composable
internal fun InfoFields(fields: List<InfoField>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        fields.forEachIndexed { index, field ->
            if (index > 0) FieldDivider()
            InfoRow(field)
        }
    }
}

/**
 * A read-only label and value on a leading icon tile: a muted 13sp label over a bold 17sp value,
 * so the value carries the row (v5 decision 7). Missing values show an em dash so every row keeps
 * its place, and TalkBack reads the pair as one item ("Phone, not provided"); the tile is
 * decoration. An [InfoField.address] value (an email) is shown whole, wrapping only after its "@"
 * ([WaiveraAddressText]). At 200% the text wraps beside the tile, which stays at the top.
 */
@Composable
internal fun InfoRow(field: InfoField, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val shown = field.value?.takeIf { it.isNotBlank() }
    val missing = stringResource(R.string.feature_profile_value_missing)
    val spoken = stringResource(R.string.feature_profile_value_missing_a11y)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small)
            .semantics(mergeDescendants = true) {
                contentDescription = "${field.label}, ${shown ?: spoken}"
            },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraIconTile(
            icon = field.icon,
            tone = WaiveraTileTone.Brand,
            size = WaiveraSizes.FieldTile,
            iconSize = WaiveraSizes.FieldTileGlyph,
            modifier = Modifier.align(rememberLeadingTileAlignment()),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        ) {
            Text(text = field.label, style = WaiveraTextStyles.FieldLabel, color = colors.textSecondary)
            if (field.address && shown != null) {
                WaiveraAddressText(address = shown, style = WaiveraTextStyles.FieldValue, color = colors.text)
            } else {
                Text(text = shown ?: missing, style = WaiveraTextStyles.FieldValue, color = colors.text)
            }
        }
    }
}

/** The hairline between two fields, inset to start under the label and stop at the card's padding. */
@Composable
private fun FieldDivider() {
    WaiveraInsetDivider(
        inset = WaiveraSpacing.Medium + WaiveraSizes.FieldDividerInset,
        modifier = Modifier.padding(end = WaiveraSpacing.Medium),
    )
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

/**
 * Placeholder [InfoRow]s for a section still loading (shown only after the skeleton delay): each
 * bone row has the row's padding, its tile and its label and value line heights, with the same
 * divider between rows, so the card is as tall as the [rows] fields that replace it, at every
 * font scale.
 */
@Composable
internal fun SectionSkeleton(modifier: Modifier = Modifier, rows: Int = 3) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_profile_loading_section)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading },
    ) {
        repeat(rows) { index ->
            if (index > 0) FieldDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(WaiveraSizes.FieldTile),
                    shape = WaiveraShapes.Tile,
                    pulse = pulse,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
                ) {
                    SkeletonText(style = WaiveraTextStyles.FieldLabel, widthFraction = LABEL_LINE, pulse = pulse)
                    SkeletonText(
                        style = WaiveraTextStyles.FieldValue,
                        widthFraction = if (index % 2 == 0) WIDE_LINE else NARROW_LINE,
                        pulse = pulse,
                    )
                }
            }
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
        // One line like the query that replaces it, so the field keeps its height at 200%.
        placeholder = {
            Text(text = placeholder, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
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
            unfocusedBorderColor = colors.fieldBorder,
            cursorColor = colors.accent,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
        ),
    )
}

private const val LABEL_LINE = 0.3f
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
                placeholder = "Search by name or email",
                clearDescription = "Clear",
            )
            ProfileCard(title = "Personal information") {
                InfoFields(
                    listOf(
                        InfoField(label = "Email", value = "alex.rivera@example.com", icon = WaiveraIcons.Mail),
                        InfoField(label = "Phone", value = null, icon = WaiveraIcons.Phone),
                    ),
                )
                SectionSkeleton()
            }
        }
    }
}
