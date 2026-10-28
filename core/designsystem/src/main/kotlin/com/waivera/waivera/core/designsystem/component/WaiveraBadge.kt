package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

/**
 * What a count is for (v5 decision 6): [Attention] is something waiting on the person (unread,
 * Needs you), solid rose with white; [Info] is a plain total, a neutral chip.
 */
enum class WaiveraCountTone { Attention, Info }

/**
 * Unread count pill in the attention rose (`unread`, spec 10 §1), so red keeps its "failed or
 * urgent" meaning. Hidden at 0. Announces "N unread" rather than a bare number.
 */
@Composable
fun WaiveraCountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = formatBadgeCount(count) ?: return
    val description = if (count > BADGE_MAX) {
        stringResource(R.string.core_designsystem_badge_count_overflow)
    } else {
        pluralStringResource(R.plurals.core_designsystem_badge_count, count, count)
    }
    WaiveraCountBadge(text = text, contentDescription = description, modifier = modifier)
}

/**
 * The count badge with caller-made copy, for counts that aren't exact (for example "30+" when
 * only part of a feed is loaded). Looks the same as the numeric badge; [tone] [WaiveraCountTone.Info]
 * draws a plain total as the neutral chip.
 */
@Composable
fun WaiveraCountBadge(
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tone: WaiveraCountTone = WaiveraCountTone.Attention,
) {
    val colors = WaiveraTheme.colors
    val (container, content) = tone.colorsFrom(colors)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = WaiveraSizes.BadgeMin, minHeight = WaiveraSizes.BadgeMin)
            .background(container, WaiveraShapes.Pill)
            .padding(horizontal = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = WaiveraTextStyles.Badge, color = content, maxLines = 1, softWrap = false)
    }
}

/** A count's fill and its ink. */
internal fun WaiveraCountTone.colorsFrom(colors: WaiveraColors): Pair<Color, Color> = when (this) {
    WaiveraCountTone.Attention -> colors.unread to colors.onAccent
    WaiveraCountTone.Info -> colors.chipNeutral to colors.textSecondary
}

enum class BadgeTone { Neutral, Accent, Success, Warning, Danger }

/**
 * A short text status label such as "Thread reply" or "Pending". Always one line: it never
 * breaks inside a word at large font scales. Put several side by side in a `FlowRow` so whole
 * chips wrap instead.
 */
@Composable
fun WaiveraStatusBadge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral) {
    val (container, content) = tone.colorsFrom(WaiveraTheme.colors)
    Text(
        text = text,
        style = WaiveraTextStyles.Badge,
        color = content,
        maxLines = 1,
        softWrap = false,
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

@OptIn(ExperimentalLayoutApi::class)
@WaiveraPreviews
@Composable
private fun WaiveraBadgePreview() {
    WaiveraTheme {
        FlowRow(
            Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraCountBadge(count = 3)
            WaiveraCountBadge(count = 120)
            WaiveraCountBadge(text = "14", contentDescription = "14 events", tone = WaiveraCountTone.Info)
            WaiveraStatusBadge(text = "Saved", tone = BadgeTone.Accent)
            WaiveraStatusBadge(text = "Thread reply")
            WaiveraStatusBadge(text = "Pending", tone = BadgeTone.Warning)
        }
    }
}
