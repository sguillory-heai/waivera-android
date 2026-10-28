package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A list row: optional leading slot, a headline and supporting text that wrap at large font
 * scales, an optional trailing slot and an optional chevron. Clickable rows get the shared
 * press scale and read as one element to TalkBack. [detail] is one more line for an email
 * address or handle, shown whole as a [WaiveraAddressText]: it can't break at a dot mid-address
 * (UI3-14), and it isn't cut either (UI6-02).
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
                Box(Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) { leading() }
            }
            RowText(
                title = headline,
                subtitle = supporting,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                detail = detail,
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
        ) { body() }
    } else {
        Box(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) { body() }
    }
}

/** A switch row for settings. The whole row toggles, and toggling fires the toggle haptic. */
@Composable
fun WaiveraSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { value ->
                    haptics.toggle(value)
                    onCheckedChange(value)
                },
            )
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title = title, subtitle = subtitle, enabled = enabled, modifier = Modifier.weight(1f))
        // The row owns the toggle semantics, so the switch itself is not separately focusable.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
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
        Text(
            text = title.withBreaksAfterHyphens(),
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) colors.text else colors.textSecondary,
        )
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        if (detail != null) {
            WaiveraAddressText(address = detail, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
    }
}

/** A rounded tile holding a leading icon, as on settings and account rows. */
@Composable
fun WaiveraIconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(WaiveraSizes.IconTile)
            .background(WaiveraTheme.colors.accentSoft, WaiveraShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = WaiveraTheme.colors.onAccentSoft,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
    }
}

/** Section label above a group of rows. Marked as a heading for TalkBack navigation. */
@Composable
fun WaiveraSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = WaiveraTextStyles.Eyebrow,
        color = WaiveraTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = WaiveraSpacing.Gutter,
                end = WaiveraSpacing.Gutter,
                top = WaiveraSpacing.Medium,
                bottom = WaiveraSpacing.ExtraSmall,
            )
            .semantics { heading() },
    )
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
