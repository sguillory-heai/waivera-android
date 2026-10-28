package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTileTint

/**
 * Category tint of an icon tile. One meaning per tint, the same on every screen:
 *
 * - [Brand]: conversations, people and the app itself (settings, account).
 * - [Rose]: things about you: mentions, reactions, bookmarks.
 * - [Amber]: celebrations, announcements and notifications.
 * - [Teal]: time: schedule items, events, invitations.
 * - [Neutral]: read, past or inactive items, and files.
 * - [Danger]: something that went wrong (a message that wasn't sent).
 */
enum class WaiveraTileTone { Brand, Rose, Amber, Teal, Neutral, Danger }

/** Resolves a tone against the current colours. */
fun WaiveraTileTone.tint(colors: WaiveraColors): WaiveraTileTint = when (this) {
    WaiveraTileTone.Brand -> colors.tiles.brand
    WaiveraTileTone.Rose -> colors.tiles.rose
    WaiveraTileTone.Amber -> colors.tiles.amber
    WaiveraTileTone.Teal -> colors.tiles.teal
    WaiveraTileTone.Neutral -> colors.tiles.neutral
    WaiveraTileTone.Danger -> colors.tiles.danger
}

/**
 * The one leading-icon language: a soft tinted rounded tile holding an outlined glyph. Every
 * leading icon in rows, Today cards and the event screen uses it, so icons never mix purple
 * tiles with bare grey glyphs. Decorative: the row it leads names the thing.
 */
@Composable
fun WaiveraIconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tone: WaiveraTileTone = WaiveraTileTone.Brand,
    size: Dp = WaiveraSizes.IconTile,
) {
    val tint = tone.tint(WaiveraTheme.colors)
    TileFrame(tint = tint, size = size, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint.content,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
    }
}

/**
 * The same tile holding a real emoji instead of a glyph (a reaction, a celebration). The emoji
 * is drawn at icon size whatever the font scale, because the tile itself does not grow; the
 * text next to it carries the meaning for TalkBack.
 */
@Composable
fun WaiveraEmojiTile(
    emoji: String,
    modifier: Modifier = Modifier,
    tone: WaiveraTileTone = WaiveraTileTone.Amber,
    size: Dp = WaiveraSizes.IconTile,
) {
    val tint = tone.tint(WaiveraTheme.colors)
    val emojiSize = with(LocalDensity.current) { WaiveraSizes.EmojiInTile.toSp() }
    TileFrame(tint = tint, size = size, modifier = modifier.clearAndSetSemantics { }) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = emojiSize, lineHeight = emojiSize, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun TileFrame(tint: WaiveraTileTint, size: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.container, WaiveraShapes.Tile),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraIconTilePreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraIconTile(icon = WaiveraIcons.Chat)
                WaiveraIconTile(icon = WaiveraIcons.Bookmark, tone = WaiveraTileTone.Rose)
                WaiveraIconTile(icon = WaiveraIcons.Announcement, tone = WaiveraTileTone.Amber)
                WaiveraIconTile(icon = WaiveraIcons.Schedule, tone = WaiveraTileTone.Teal)
                WaiveraIconTile(icon = WaiveraIcons.Check, tone = WaiveraTileTone.Neutral)
                WaiveraIconTile(icon = WaiveraIcons.Error, tone = WaiveraTileTone.Danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraEmojiTile(emoji = "🎂")
                WaiveraEmojiTile(emoji = "🎉", tone = WaiveraTileTone.Brand)
                WaiveraEmojiTile(emoji = "👍", tone = WaiveraTileTone.Rose)
            }
        }
    }
}
