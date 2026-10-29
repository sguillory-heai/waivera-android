package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isExtraLargeFontScale

/**
 * A row of stat tiles on the hero: equal widths and, as labels wrap at large font, equal heights.
 */
@Composable
fun WaiveraStatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        content = content,
    )
}

/**
 * One stat on the hero: the number large ("14") over its label ("events this week") on a faint
 * glass tile. TalkBack reads [contentDescription] (for example "14 events this week") as one item.
 * Use it inside [WaiveraStatRow]. [compact] (a short window at regular font) puts the label
 * beside the number on a shorter tile, so the sheet's first rows still show above the fold.
 */
@Composable
fun RowScope.WaiveraStatTile(
    value: String,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val hero = WaiveraHero.colors
    val tile = modifier
        .weight(1f)
        .fillMaxHeight()
        .background(hero.tile, WaiveraShapes.StatTile)
        .border(BorderStroke(WaiveraSpacing.Hairline, hero.tileBorder), WaiveraShapes.StatTile)
        .clearAndSetSemantics { this.contentDescription = contentDescription }
    if (compact) {
        Row(
            modifier = tile.padding(
                horizontal = WaiveraSpacing.Small + WaiveraSpacing.XxSmall,
                vertical = WaiveraSpacing.Small,
            ),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatNumber(value)
            Text(text = label, style = WaiveraTextStyles.StatLabel, color = hero.onHeaderSecondary, maxLines = 2)
        }
        return
    }
    Column(
        modifier = tile.padding(
            horizontal = WaiveraSpacing.MediumSmall,
            vertical = WaiveraSpacing.MediumSmall - WaiveraSpacing.Hairline,
        ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
    ) {
        StatNumber(value)
        Text(text = label, style = WaiveraTextStyles.StatLabel, color = hero.onHeaderSecondary)
    }
}

/**
 * A stat's number. A new value (the "–" while loading giving way to "4") swaps in place on the
 * shared swap motion instead of cutting (M3-04). TalkBack reads the tile's description, not this.
 */
@Composable
private fun StatNumber(value: String) {
    val hero = WaiveraHero.colors
    WaiveraSwap(targetState = value) { shown ->
        Text(text = shown, style = WaiveraTextStyles.StatNumeral, color = hero.onHeader, maxLines = 1)
    }
}

/** One stat for [WaiveraStatGroup]: its number, its label and what TalkBack reads for it. */
@Immutable
data class WaiveraStat(val value: String, val label: String, val contentDescription: String)

/**
 * The hero's stats, laid out for the text size: three equal tiles in a row, or, when a tile's
 * label would have to break inside a word ("handle / d" at 200% on a 360dp phone, UI10-04), one
 * glass card with a row per stat, the number and its label side by side across the full width.
 * At 150% font and up on a phone the card is used anyway: it keeps the hero short enough for
 * the sheet to start on the first screen (UI10-08). [compact] is the short-window tile, at
 * regular font only.
 */
@Composable
fun WaiveraStatGroup(stats: List<WaiveraStat>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val labelStyle = WaiveraTextStyles.StatLabel
    val extraLarge = isExtraLargeFontScale()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileLabelWidth = (maxWidth - WaiveraSpacing.Small * (stats.size - 1).coerceAtLeast(0)) /
            stats.size.coerceAtLeast(1) - WaiveraSpacing.MediumSmall * 2
        val tileLabelPx = with(LocalDensity.current) { tileLabelWidth.roundToPx() }
        // The widest single word of any label, as set: wider than a tile's label means a mid-word break.
        val widestWord = remember(stats, measurer, labelStyle) {
            stats.flatMap { it.label.split(' ', '\u00A0') }
                .filter(String::isNotEmpty)
                .maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
        }
        val phoneAtExtraLarge = extraLarge && maxWidth < WaiveraSizes.StatStackMaxWidth
        val stacked = !compact && (widestWord > tileLabelPx || phoneAtExtraLarge)
        if (stacked) {
            StatCard(stats)
        } else {
            WaiveraStatRow {
                stats.forEach { stat ->
                    WaiveraStatTile(
                        value = stat.value,
                        label = stat.label,
                        contentDescription = stat.contentDescription,
                        compact = compact,
                    )
                }
            }
        }
    }
}

/** The stats as rows on one glass card: "5/5 handled", each read by TalkBack as one item. */
@Composable
private fun StatCard(stats: List<WaiveraStat>) {
    val hero = WaiveraHero.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(hero.tile, WaiveraShapes.StatTile)
            .border(BorderStroke(WaiveraSpacing.Hairline, hero.tileBorder), WaiveraShapes.StatTile)
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.ExtraSmall),
    ) {
        stats.forEach { stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)
                    .clearAndSetSemantics { contentDescription = stat.contentDescription },
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatNumber(stat.value)
                Text(
                    text = stat.label,
                    style = WaiveraTextStyles.StatLabel,
                    color = hero.onHeaderSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraStatTilePreview() {
    WaiveraTheme {
        WaiveraHeroBackdrop(trail = WaiveraHeroTrail.None) {
            WaiveraStatGroup(
                stats = listOf(
                    WaiveraStat(value = "5/5", label = "handled", contentDescription = "5 of 5 handled today"),
                    WaiveraStat(value = "2", label = "unread chats", contentDescription = "2 unread chats"),
                    WaiveraStat(value = "14", label = "events this week", contentDescription = "14 events this week"),
                ),
                modifier = Modifier.padding(WaiveraSpacing.Medium),
            )
        }
    }
}
