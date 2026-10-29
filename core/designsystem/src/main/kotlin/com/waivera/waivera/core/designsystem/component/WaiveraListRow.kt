package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/**
 * A list row: optional leading slot, a headline and supporting text that wrap at large font
 * scales, an optional trailing slot and an optional chevron. Clickable rows get the shared
 * press scale and read as one element to TalkBack. [detail] is one more line for an email
 * address or handle, shown whole as a [WaiveraAddressText]: it can't break at a dot mid-address
 * (UI3-14), and it isn't cut either (UI6-02). A [nameHeadline] is a person's name: one line, two
 * at large font, ending in "…" and never broken mid-word ([WaiveraNameText], wave 29 N5).
 */
@Composable
fun WaiveraListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    detail: String? = null,
    nameHeadline: Boolean = false,
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSizes.ListRowMinHeight)
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                // Level with the headline's first line when the row grows tall (v7, item 1).
                Box(
                    Modifier
                        .align(rememberLeadingTileAlignment())
                        .alpha(if (enabled) 1f else DISABLED_ALPHA)
                        .testTag(LIST_ROW_LEADING_TAG),
                ) { leading() }
            }
            RowText(
                title = headline,
                subtitle = supporting,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                detail = detail,
                nameTitle = nameHeadline,
            )
            if (trailing != null) trailing()
            if (showChevron) {
                Icon(
                    painter = painterResource(WaiveraIcons.ChevronRight),
                    contentDescription = null,
                    tint = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
        }
    }
    if (onClick != null) {
        WaiveraPressable(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick,
            depth = PressDepth.Row,
        ) { body() }
    } else {
        Box(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) { body() }
    }
}

/**
 * A switch row for settings. The whole row toggles: it presses in like any row, the thumb slides
 * on Material's own spring. Toggling never vibrates (haptics policy); the thumb is the feedback.
 * [leading] is the row's icon tile, so switch rows share the icon language of every other row.
 */
@Composable
fun WaiveraSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = WaiveraTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, depth = PressDepth.Row)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressFeedback(press, enabled = enabled)
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.align(rememberLeadingTileAlignment())) { leading() }
        RowText(title = title, subtitle = subtitle, enabled = enabled, modifier = Modifier.weight(1f))
        // The row owns the toggle semantics, so the switch itself is not separately focusable.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accentFill,
                uncheckedThumbColor = colors.borderStrong,
                uncheckedTrackColor = colors.surfaceMuted,
                uncheckedBorderColor = colors.borderStrong,
                // A blocked "on" switch recedes: a muted accent track, never a heavy slate one
                // that looks stronger than an enabled switch (UI3-12).
                disabledCheckedThumbColor = colors.surface,
                disabledCheckedTrackColor = colors.accent.copy(alpha = DISABLED_CHECKED_TRACK_ALPHA),
                disabledUncheckedThumbColor = colors.textSecondary,
                disabledUncheckedTrackColor = colors.surfaceMuted,
                disabledUncheckedBorderColor = colors.textSecondary,
            ),
        )
    }
}

/**
 * Title and supporting text of a row. Disabled rows stay readable (title in `textSecondary`,
 * supporting text slightly lighter) instead of fading the whole row to near-invisible.
 */
@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    nameTitle: Boolean = false,
) {
    val colors = WaiveraTheme.colors
    val subtitleColor = if (enabled) {
        colors.textSecondary
    } else {
        colors.textSecondary.copy(
            alpha = DISABLED_SUPPORTING_ALPHA,
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
        // Hyphenated names wrap after a hyphen, never mid-word, in every row (UI5-05).
        if (nameTitle) {
            WaiveraNameText(
                text = title,
                maxLines = if (isLargeFontScale()) NAME_LINES_LARGE_FONT else 1,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.text else colors.textSecondary,
                modifier = Modifier.testTag(LIST_ROW_NAME_TAG),
            )
        } else {
            Text(
                text = title.withBreaksAfterHyphens(),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.text else colors.textSecondary,
            )
        }
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        if (detail != null) {
            WaiveraAddressText(address = detail, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
    }
}

private const val NAME_LINES_LARGE_FONT = 2

/** Test tag for a row's leading slot ([WaiveraListRow]'s leading). */
const val LIST_ROW_LEADING_TAG: String = "list_row_leading"

/** Test tag for a row's name headline ([WaiveraListRow]'s nameHeadline). */
const val LIST_ROW_NAME_TAG: String = "list_row_name"

/**
 * Section title above a group of rows: B's mixed-case 17 / 600, the same title as the sections
 * on Today's sheet, so every screen's sections read alike. Marked as a heading for TalkBack.
 * The [first] section on a sheet sits right under the sheet's curve, which is its top margin,
 * as Today's first section does (UI10-01).
 */
@Composable
fun WaiveraSectionHeader(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = WaiveraTheme.colors.text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
                end = WaiveraSpacing.Gutter,
                top = if (first) WaiveraSpacing.None else WaiveraSpacing.MediumLarge,
                bottom = WaiveraSpacing.Small,
            )
            .semantics { heading() },
    )
}

/**
 * A group of rows on one card: the sheet card's surface, 20dp corners and soft lift, inset by the
 * screen gutter, with the rows edge to edge inside it. Settings, the profile's details and the
 * event's people use it, so grouped rows look the same on every screen. Put a [WaiveraInsetDivider]
 * between rows.
 */
@Composable
fun WaiveraCardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter)
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(WaiveraTheme.colors.surface, WaiveraShapes.SheetCard)
            .clip(WaiveraShapes.SheetCard),
        content = content,
    )
}

/**
 * One row of a [WaiveraCardGroup] laid out as lazy list items: inset by the gutter and clipped to
 * its part of the card (rounded top on the first of [count], rounded bottom on the last, square
 * between), on the card's surface. A lazy list composes rows one by one, so it cannot wrap them in
 * one group; each row draws its slice instead. The lift is left out, since a shadow per slice
 * would show seams between rows. Pass the theme's surface (`WaiveraTheme.colors.surface`) as
 * [surface]: a modifier factory reads no composition locals.
 */
fun Modifier.waiveraCardGroupItem(index: Int, count: Int, surface: Color): Modifier {
    val first = index == 0
    val last = index == count - 1
    val shape = when {
        first && last -> WaiveraShapes.SheetCard
        first -> CardGroupTop
        last -> CardGroupBottom
        else -> RectangleShape
    }
    return this
        .padding(horizontal = WaiveraSpacing.Gutter)
        .clip(shape)
        .background(surface)
}

private val CardGroupTop = RoundedCornerShape(
    topStart = WaiveraShapes.SheetCardRadius,
    topEnd = WaiveraShapes.SheetCardRadius,
)
private val CardGroupBottom = RoundedCornerShape(
    bottomStart = WaiveraShapes.SheetCardRadius,
    bottomEnd = WaiveraShapes.SheetCardRadius,
)

/** The hairline between rows on a [WaiveraCardGroup], starting where the row's text starts. */
@Composable
fun WaiveraInsetDivider(modifier: Modifier = Modifier, inset: Dp = WaiveraSpacing.Gutter) {
    WaiveraDivider(modifier = modifier.padding(start = inset))
}

/** Hairline divider in the border colour. */
@Composable
fun WaiveraDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = WaiveraSpacing.Hairline, color = WaiveraTheme.colors.border)
}

private const val DISABLED_ALPHA = 0.5f
private const val DISABLED_CHECKED_TRACK_ALPHA = 0.38f
private const val DISABLED_SUPPORTING_ALPHA = 0.8f

@WaiveraPreviews
@Composable
private fun WaiveraListRowPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.surface)) {
            WaiveraSectionHeader(text = "Settings")
            WaiveraListRow(
                headline = "Notifications",
                supporting = "Messages, schedule and reminders",
                onClick = {},
                showChevron = true,
                leading = { WaiveraIconTile(icon = WaiveraIcons.Notifications) },
            )
            WaiveraDivider()
            WaiveraListRow(
                headline = "Jordan Lee",
                supporting = "5-year anniversary",
                detail = "jordan.lee@example.com",
                onClick = {},
                showChevron = true,
            )
            WaiveraDivider()
            WaiveraSwitchRow(
                title = "Haptics",
                subtitle = "Vibrate for failed sends, errors and toggles",
                checked = true,
                onCheckedChange = {},
            )
            WaiveraSwitchRow(title = "Mentions", checked = true, onCheckedChange = {}, enabled = false)
            WaiveraSwitchRow(title = "Digest", checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}
