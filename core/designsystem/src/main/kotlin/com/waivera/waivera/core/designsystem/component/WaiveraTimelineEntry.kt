package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/** The tone's block fill, top to bottom, as on the "Your day" track. */
val WaiveraTimelineTone.brush: Brush
    get() = Brush.verticalGradient(listOf(top, bottom))

/**
 * One entry of a day read as a timeline (concept B's language, in a list): the start time in a
 * column of its own, a rail with the tone's gradient block beside it and a hairline running on
 * to the next entry, then [content] (the entry's card). A [tone] of null is an ended or quiet
 * entry: its block takes the border colour, so what is still ahead stands out.
 *
 * At large font the time no longer fits a column beside the card, so it moves above the card
 * on a line of its own with the block in front of it, and the rail is left out.
 *
 * The time and the rail are decoration for TalkBack: the card carries its own time in words.
 */
@Composable
fun WaiveraTimelineEntry(
    time: String,
    tone: WaiveraTimelineTone?,
    modifier: Modifier = Modifier,
    isLast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = WaiveraTheme.colors
    val block = tone?.brush ?: Brush.verticalGradient(listOf(colors.borderStrong, colors.borderStrong))
    if (isLargeFontScale()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        ) {
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = WaiveraSizes.TimelineRailBlock, height = WaiveraSizes.TimelineRailBlockShort)
                        .background(block, WaiveraShapes.TimelineBlock),
                )
                Text(text = time, style = WaiveraTextStyles.TimelineTime, color = colors.text)
            }
            content()
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Text(
            text = time,
            style = WaiveraTextStyles.TimelineTime,
            color = if (tone == null) colors.textSecondary else colors.text,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(WaiveraSizes.TimelineTimeColumn)
                .padding(top = WaiveraSpacing.MediumSmall)
                .clearAndSetSemantics { },
        )
        Column(
            modifier = Modifier
                .width(WaiveraSizes.TimelineRailBlock)
                .fillMaxHeight()
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = WaiveraSpacing.MediumSmall)
                    .size(width = WaiveraSizes.TimelineRailBlock, height = WaiveraSizes.TimelineRailBlockTall)
                    .background(block, WaiveraShapes.TimelineBlock),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .padding(top = WaiveraSpacing.ExtraSmall)
                        .width(WaiveraSizes.TimelineTick)
                        .weight(1f)
                        .background(colors.border),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraTimelineEntryPreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
        ) {
            WaiveraTimelineEntry(time = "9:00 AM", tone = null) {
                WaiveraSheetCard { Text(text = "Team check-in") }
            }
            WaiveraTimelineEntry(time = "11:00 AM", tone = WaiveraTimelineTone.Teal) {
                WaiveraSheetCard { Text(text = "Client home visit") }
            }
            WaiveraTimelineEntry(time = "All day", tone = WaiveraTimelineTone.Gold, isLast = true) {
                WaiveraSheetCard { Text(text = "Jordan's work anniversary") }
            }
        }
    }
}
