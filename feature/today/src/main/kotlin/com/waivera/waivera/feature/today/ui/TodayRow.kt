package com.waivera.waivera.feature.today.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R

/**
 * The shared Today row: optional eyebrow, a headline that wraps (up to [headlineMaxLines]),
 * supporting text, a leading slot and a trailing slot. TalkBack reads it as one element with
 * [description], so compact visuals like "5m" are spoken in full. Every Today row opens
 * something, so every row ends in the same chevron by default.
 */
@Composable
internal fun TodayRow(
    headline: String,
    description: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowColor: Color = WaiveraTheme.colors.textSecondary,
    supporting: String? = null,
    emphasized: Boolean = false,
    headlineMaxLines: Int = HEADLINE_MAX_LINES,
    showChevron: Boolean = true,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = WaiveraTheme.colors
    WaiveraPressable(
        onClick = onClick,
        onClickLabel = onClickLabel,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSizes.ListRowMinHeight)
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                if (eyebrow != null) {
                    Text(text = eyebrow, style = MaterialTheme.typography.labelMedium, color = eyebrowColor)
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                    color = colors.text,
                    maxLines = headlineMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (emphasized) colors.text else colors.textSecondary,
                        maxLines = SUPPORTING_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
            if (showChevron) {
                Icon(
                    painter = painterResource(WaiveraIcons.ChevronRight),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
        }
    }
}

/**
 * A 40dp icon tile: accent wash for things that need attention, muted once read, danger for
 * something that went wrong (a message that wasn't sent).
 */
@Composable
internal fun TodayIconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    danger: Boolean = false,
) {
    val colors = WaiveraTheme.colors
    val fill = when {
        danger -> colors.dangerContainer
        muted -> colors.surfaceMuted
        else -> colors.accentSoft
    }
    val tint = when {
        danger -> colors.onDangerContainer
        muted -> colors.textSecondary
        else -> colors.onAccentSoft
    }
    Box(
        modifier = modifier
            .size(WaiveraSizes.IconTile)
            .background(fill, WaiveraShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
    }
}

/** The unread dot. Never the only signal: rows also say "Unread" to TalkBack and bold their text. */
@Composable
internal fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(WaiveraSpacing.Small)
            .background(WaiveraTheme.colors.unread, WaiveraShapes.Circle),
    )
}

/** Section header with an optional trailing action, marked as a heading for TalkBack navigation. */
@Composable
internal fun TodaySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.Small, top = WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Text(
            text = title.uppercase(),
            style = WaiveraTextStyles.Eyebrow,
            color = WaiveraTheme.colors.textSecondary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) trailing()
    }
}

private const val HEADLINE_MAX_LINES = 3
private const val SUPPORTING_MAX_LINES = 2

@WaiveraPreviews
@Composable
private fun TodayRowPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            TodaySectionHeader(title = "Needs you")
            TodayRow(
                headline = "Priya Raman mentioned you: \"the Hughes visit moved to 10\"",
                description = "Unread. Priya Raman mentioned you",
                onClick = {},
                onClickLabel = "Open message",
                supporting = "Mention · 5m",
                emphasized = true,
                leading = { TodayIconTile(icon = R.drawable.feature_today_ic_mention) },
            )
            TodayRow(
                headline = "Client home visit",
                description = "Client home visit, in 25 min",
                onClick = {},
                onClickLabel = "Open event",
                eyebrow = "in 25 min",
                eyebrowColor = WaiveraTheme.colors.accent,
                supporting = "11:00 AM – 12:00 PM · 1200 W Lake St",
                leading = { TodayIconTile(icon = WaiveraIcons.Schedule) },
                showChevron = true,
            )
        }
    }
}
