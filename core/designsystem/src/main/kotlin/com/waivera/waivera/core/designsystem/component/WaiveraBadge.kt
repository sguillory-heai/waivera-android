package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Badge text for a count: nothing at 0 or below, the number up to [max], then `max+`. */
fun formatBadgeCount(count: Int, max: Int = BADGE_MAX): String? = when {
    count <= 0 -> null
    count > max -> "$max+"
    else -> count.toString()
}

const val BADGE_MAX = 99

/** Unread count pill. Hidden at 0. Announces "N unread" rather than a bare number. */
@Composable
fun WaiveraCountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = formatBadgeCount(count) ?: return
    val description = if (count > BADGE_MAX) {
        stringResource(R.string.core_designsystem_badge_count_overflow)
    } else {
        pluralStringResource(R.plurals.core_designsystem_badge_count, count, count)
    }
    val colors = WaiveraTheme.colors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = WaiveraSizes.BadgeMin, minHeight = WaiveraSizes.BadgeMin)
            .background(colors.dangerFill, WaiveraShapes.Pill)
            .padding(horizontal = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = WaiveraTextStyles.Badge, color = colors.onDangerFill, maxLines = 1)
    }
}

enum class BadgeTone { Neutral, Accent, Success, Warning, Danger }

/** A short text status label such as "Saved", "Thread reply" or "Pending". */
@Composable
fun WaiveraStatusBadge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral) {
    val (container, content) = tone.colorsFrom(WaiveraTheme.colors)
    Text(
        text = text,
        style = WaiveraTextStyles.Badge,
        color = content,
        modifier = modifier
            .background(container, WaiveraShapes.Chip)
            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.XxSmall),
    )
}

private fun BadgeTone.colorsFrom(colors: WaiveraColors): Pair<Color, Color> = when (this) {
    BadgeTone.Neutral -> colors.surfaceMuted to colors.textSecondary
    BadgeTone.Accent -> colors.accentSoft to colors.onAccentSoft
    BadgeTone.Success -> colors.anniversaryContainer to colors.onAnniversaryContainer
    BadgeTone.Warning -> colors.warningContainer to colors.onWarningContainer
    BadgeTone.Danger -> colors.dangerContainer to colors.onDangerContainer
}

@WaiveraPreviews
@Composable
private fun WaiveraBadgePreview() {
    WaiveraTheme {
        Row(
            Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraCountBadge(count = 3)
            WaiveraCountBadge(count = 120)
            WaiveraStatusBadge(text = "Saved", tone = BadgeTone.Accent)
            WaiveraStatusBadge(text = "Thread reply")
            WaiveraStatusBadge(text = "Pending", tone = BadgeTone.Warning)
        }
    }
}
