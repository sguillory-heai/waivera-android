package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A celebration on a hero (a profile's "Happy birthday, Alex!"): the emoji and the line on the
 * hero's glass, in the hero inks. Celebration content is one of the two places emoji appear. The
 * emoji is decoration for TalkBack; the line says it in words.
 */
@Composable
fun WaiveraHeroCelebration(emoji: String, text: String, modifier: Modifier = Modifier) {
    val hero = WaiveraHero.colors
    Row(
        modifier = modifier
            .background(hero.tile, WaiveraShapes.StatTile)
            .border(BorderStroke(WaiveraSpacing.Hairline, hero.tileBorder), WaiveraShapes.StatTile)
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, style = WaiveraTextStyles.HeroTitle, modifier = Modifier.clearAndSetSemantics { })
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = hero.onHeader)
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraHeroCelebrationPreview() {
    WaiveraTheme {
        WaiveraHeroBackdrop(trail = WaiveraHeroTrail.None) {
            WaiveraHeroCelebration(
                emoji = "🎂",
                text = "Happy birthday, Alex!",
                modifier = Modifier.padding(WaiveraSpacing.Medium),
            )
        }
    }
}
