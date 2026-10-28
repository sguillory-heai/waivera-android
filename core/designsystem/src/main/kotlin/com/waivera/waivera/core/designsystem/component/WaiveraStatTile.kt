package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

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
 * Use it inside [WaiveraStatRow].
 */
@Composable
fun RowScope.WaiveraStatTile(value: String, label: String, contentDescription: String, modifier: Modifier = Modifier) {
    val hero = WaiveraHero.colors
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .background(hero.tile, WaiveraShapes.StatTile)
            .border(BorderStroke(WaiveraSpacing.Hairline, hero.tileBorder), WaiveraShapes.StatTile)
            .padding(
                horizontal = WaiveraSpacing.MediumSmall,
                vertical = WaiveraSpacing.MediumSmall - WaiveraSpacing.Hairline,
            )
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
    ) {
        Text(text = value, style = WaiveraTextStyles.StatNumeral, color = hero.onHeader, maxLines = 1)
        Text(text = label, style = WaiveraTextStyles.StatLabel, color = hero.onHeaderSecondary)
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraStatTilePreview() {
    WaiveraTheme {
        WaiveraHeroBackdrop(trail = WaiveraHeroTrail.None) {
            WaiveraStatRow(modifier = Modifier.padding(WaiveraSpacing.Medium)) {
                WaiveraStatTile(value = "3", label = "events today", contentDescription = "3 events today")
                WaiveraStatTile(value = "2", label = "unread chats", contentDescription = "2 unread chats")
                WaiveraStatTile(value = "14", label = "events this week", contentDescription = "14 events this week")
            }
        }
    }
}
