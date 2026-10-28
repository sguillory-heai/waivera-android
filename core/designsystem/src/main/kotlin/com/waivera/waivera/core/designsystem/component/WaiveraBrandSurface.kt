package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A surface with the soft brand-gradient wash, for celebrations only (a birthday, an
 * anniversary, a new hire). Under v5's gradient rule B the gradient means "progress and
 * celebration" and nothing else: the plum hero, the progress and reward rings, the schedule's
 * "now" line and this wash. Anything else that wants to stand out uses [WaiveraSurfaceCard].
 * Text in `text` and `textSecondary` keeps 4.5:1 on every point of the wash in both themes
 * (`WaiveraColorsContrastTest`). Use it once or twice per screen, never for lists of rows.
 */
@Composable
fun WaiveraBrandSurface(
    modifier: Modifier = Modifier,
    shape: Shape = WaiveraShapes.Hero,
    contentPadding: PaddingValues = PaddingValues(WaiveraSpacing.Medium),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(WaiveraSpacing.Small),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .background(WaiveraBrand.wash())
            .border(WaiveraSpacing.Hairline, colors.border, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * A plain lifted block on the surface with a hairline edge: a gate's welcome, an event's title
 * block. The solid counterpart of [WaiveraBrandSurface] for anything that is not a celebration.
 */
@Composable
fun WaiveraSurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = WaiveraShapes.Hero,
    contentPadding: PaddingValues = PaddingValues(WaiveraSpacing.Medium),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(WaiveraSpacing.Small),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(WaiveraSpacing.Hairline, colors.border, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * "🎂 Happy birthday, Priya!" beside a person: the celebration emoji in its own tile and the
 * sentence on the brand wash. The emoji lives only in the tile, so [text] carries none
 * and TalkBack reads the sentence alone. [centered] is for a centred hero (a person's page).
 */
@Composable
fun WaiveraCelebrationNote(
    emoji: String,
    text: String,
    modifier: Modifier = Modifier,
    tone: WaiveraTileTone = WaiveraTileTone.Celebration,
    centered: Boolean = false,
) {
    WaiveraBrandSurface(
        modifier = modifier.semantics(mergeDescendants = true) { },
        shape = WaiveraShapes.Card,
        contentPadding = PaddingValues(WaiveraSpacing.MediumSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                WaiveraSpacing.MediumSmall,
                if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraEmojiTile(emoji = emoji, tone = tone)
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = WaiveraTheme.colors.text,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f, fill = !centered),
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraBrandSurfacePreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraSurfaceCard {
                Text(
                    text = "Care plan review",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WaiveraTheme.colors.text,
                )
                Text(
                    text = "Mon, Nov 2, 9:00 AM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                )
                WaiveraButton(text = "Send wishes", onClick = {}, variant = WaiveraButtonVariant.TonalOnWash)
            }
            WaiveraCelebrationNote(emoji = "🎂", text = "Happy birthday, Priya Raman!")
            WaiveraCelebrationNote(
                emoji = "🎉",
                text = "Happy 5-year work anniversary, Jordan Lee!",
                tone = WaiveraTileTone.Brand,
                centered = true,
            )
        }
    }
}
