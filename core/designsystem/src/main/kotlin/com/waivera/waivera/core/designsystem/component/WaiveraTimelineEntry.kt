package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
 * A [kicker] ("Until", for an overnight item that began the day before) sits over the time as a
 * line of its own, and the time stays on one line, so "Until 6:00 AM" never wraps as "Until 6:00
 * / AM" in the column (UI10-11). At large font both go on the one line above the card.
 *
 * The time and the rail are decoration for TalkBack: the card carries its own time in words.
 *
 * The row is a small custom layout rather than a `Row` at `IntrinsicSize.Min`: the card is
 * measured first, at its real width, and the time and the rail then take its height. So the row
 * is exactly as tall as the card it draws, even for a card that picks its layout from its own
 * measured width (the Day celebration card), and every gap along the rail is the same (UI11-02).
 * Nothing in the row asks its content for intrinsic measurements, which a SubcomposeLayout
 * inside a card (such as BoxWithConstraints) does not support.
 */
@Composable
fun WaiveraTimelineEntry(
    time: String,
    tone: WaiveraTimelineTone?,
    modifier: Modifier = Modifier,
    kicker: String? = null,
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
                if (kicker != null) {
                    Text(text = kicker, style = WaiveraTextStyles.TimelineTime, color = colors.textSecondary)
                }
                Text(text = time, style = WaiveraTextStyles.TimelineTime, color = colors.text)
            }
            Box(modifier = Modifier.testTag(TIMELINE_ENTRY_CONTENT_TEST_TAG)) { content() }
        }
        return
    }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Column(
                modifier = Modifier
                    .padding(top = WaiveraSpacing.MediumSmall)
                    .clearAndSetSemantics { },
                horizontalAlignment = Alignment.End,
            ) {
                if (kicker != null) {
                    Text(
                        text = kicker,
                        style = WaiveraTextStyles.TimelineTime,
                        color = colors.textSecondary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                Text(
                    text = time,
                    style = WaiveraTextStyles.TimelineTime,
                    color = if (tone == null) colors.textSecondary else colors.text,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(
                modifier = Modifier.clearAndSetSemantics { },
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
            Box(modifier = Modifier.testTag(TIMELINE_ENTRY_CONTENT_TEST_TAG)) { content() }
        },
    ) { measurables, constraints ->
        val (timeColumn, railColumn, card) = measurables
        val gap = WaiveraSpacing.Small.roundToPx()
        val timeWidth = WaiveraSizes.TimelineTimeColumn.roundToPx()
        val railWidth = WaiveraSizes.TimelineRailBlock.roundToPx()
        val cardX = timeWidth + railWidth + 2 * gap
        val cardWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - cardX).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        // The card first, at its real width, so the row is as tall as the card it draws.
        val cardPlaceable = card.measure(
            Constraints(
                minWidth = if (cardWidth == Constraints.Infinity) 0 else cardWidth,
                maxWidth = cardWidth,
                maxHeight = constraints.maxHeight,
            ),
        )
        val timePlaceable = timeColumn.measure(
            Constraints.fixedWidth(timeWidth).copy(maxHeight = constraints.maxHeight),
        )
        // The time and the rail's block never clip: a card shorter than them grows the row.
        val railMinimum = (WaiveraSpacing.MediumSmall + WaiveraSizes.TimelineRailBlockTall).roundToPx()
        val height = maxOf(cardPlaceable.height, timePlaceable.height, railMinimum)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val railPlaceable = railColumn.measure(Constraints.fixed(railWidth, height))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else cardX + cardPlaceable.width
        layout(width, height) {
            timePlaceable.placeRelative(0, 0)
            railPlaceable.placeRelative(timeWidth + gap, 0)
            cardPlaceable.placeRelative(cardX, 0)
        }
    }
}

/** Tags the card slot of each [WaiveraTimelineEntry], for layout tests that measure the rail. */
const val TIMELINE_ENTRY_CONTENT_TEST_TAG = "waiveraTimelineEntryContent"

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
            WaiveraTimelineEntry(time = "6:00 AM", kicker = "Until", tone = WaiveraTimelineTone.Violet) {
                WaiveraSheetCard { Text(text = "Overnight respite shift") }
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
