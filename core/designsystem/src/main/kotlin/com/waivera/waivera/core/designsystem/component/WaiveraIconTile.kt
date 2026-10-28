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
 * Category tint of an icon tile. One meaning per tint, the same on every screen (UI8-11):
 *
 * - [Brand]: conversations, people and the app itself (settings, account, theme). A call inside
 *   a chat stays Brand, because chat is Brand.
 * - [Rose]: things about you: mentions, reactions, bookmarks, and how the app feels (haptics).
 * - [Amber]: celebrations, announcements and notifications.
 * - [Teal]: the schedule, whatever the timing: schedule items, events, invitations, time off.
 *   "Starting soon" is said by the accent time label, never by the tile.
 * - [Neutral]: read, past or inactive items, files, and a quiet "didn't load" line.
 * - [Danger]: something that went wrong that you can act on (a message that wasn't sent).
 * - [Celebration]: the emoji tile on a celebration (a birthday, an anniversary, a new hire). The
 *   soft gold of [Amber] in light; a warm plum in dark, because it sits on the brand wash and the
 *   amber container read khaki there (UI9-06).
 */
enum class WaiveraTileTone { Brand, Rose, Amber, Teal, Neutral, Danger, Celebration }

/** Resolves a tone against the current colours. */
fun WaiveraTileTone.tint(colors: WaiveraColors): WaiveraTileTint = when (this) {
    WaiveraTileTone.Brand -> colors.tiles.brand
    WaiveraTileTone.Rose -> colors.tiles.rose
    WaiveraTileTone.Amber -> colors.tiles.amber
    WaiveraTileTone.Teal -> colors.tiles.teal
    WaiveraTileTone.Neutral -> colors.tiles.neutral
    WaiveraTileTone.Danger -> colors.tiles.danger
    WaiveraTileTone.Celebration -> colors.tiles.celebration
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
    iconSize: Dp = WaiveraSizes.Icon,
) {
    val tint = tone.tint(WaiveraTheme.colors)
    TileFrame(tint = tint, size = size, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint.content,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The same tile holding a real emoji instead of a glyph: for celebrations only (a birthday, an
 * anniversary, a new hire). Everything else takes an outlined glyph in [WaiveraIconTile]
 * (`docs/design/voice.md`, "Emoji"). The emoji
 * is drawn at icon size whatever the font scale, because the tile itself does not grow; the
 * text next to it carries the meaning for TalkBack.
 */
@Composable
fun WaiveraEmojiTile(
    emoji: String,
    modifier: Modifier = Modifier,
    tone: WaiveraTileTone = WaiveraTileTone.Celebration,
    size: Dp = WaiveraSizes.IconTile,
    emojiSize: Dp = WaiveraSizes.EmojiInTile,
) {
    val tint = tone.tint(WaiveraTheme.colors)
    val emojiSp = with(LocalDensity.current) { emojiSize.toSp() }
    TileFrame(tint = tint, size = size, modifier = modifier.clearAndSetSemantics { }) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = emojiSp, lineHeight = emojiSp, textAlign = TextAlign.Center),
        )
    }
}

/**
 * The Waivera mark in a Brand tile: the leading image for the Waivera bot, which is the app
 * itself talking, so it wears the logo rather than a generic robot (UI8-08). Decorative; the
 * caller names it for TalkBack.
 */
@Composable
fun WaiveraLogoTile(
    modifier: Modifier = Modifier,
    size: Dp = WaiveraSizes.IconTile,
    logoHeight: Dp = WaiveraSizes.Icon,
) {
    TileFrame(tint = WaiveraTileTone.Brand.tint(WaiveraTheme.colors), size = size, modifier = modifier) {
        WaiveraLogo(variant = WaiveraLogoVariant.Mark, height = logoHeight)
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
                WaiveraLogoTile()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraEmojiTile(emoji = "🎂")
                WaiveraEmojiTile(emoji = "🎉", tone = WaiveraTileTone.Brand)
                WaiveraEmojiTile(emoji = "👋", tone = WaiveraTileTone.Brand)
            }
        }
    }
}
