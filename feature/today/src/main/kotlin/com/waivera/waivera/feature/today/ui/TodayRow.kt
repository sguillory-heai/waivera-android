package com.waivera.waivera.feature.today.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraPopVisibility
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isShortWindow
import com.waivera.waivera.feature.today.R

/**
 * The shared Today card (concept A): a surface card on the sheet with an optional kicker
 * ("Not sent", "Mention · 5m"), a headline that wraps (up to [headlineMaxLines]), supporting
 * text, a leading tile and a trailing slot. TalkBack reads the tappable part as one element with
 * [description], so compact visuals like "5m" are spoken in full. Every Today card opens
 * something, so it ends in the same chevron by default. [quote] is what someone said (a
 * mention's snippet), on its own line behind a rule in [quoteRuleColor]. [below] holds separate actions inside
 * the card (an invitation's Decline and Accept), outside the card's own tap target.
 */
@Composable
internal fun TodayRow(
    headline: String,
    description: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowColor: Color = WaiveraTheme.colors.textSecondary,
    supporting: String? = null,
    quote: String? = null,
    quoteRuleColor: Color = WaiveraTheme.colors.accent,
    emphasized: Boolean = false,
    headlineMaxLines: Int = HEADLINE_MAX_LINES,
    showChevron: Boolean = true,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = WaiveraTheme.colors
    // An 8dp gap between cards on a short window, 10dp elsewhere: two whole Needs you cards fit
    // under the hero on a 640dp phone (UI10-01).
    val inset = if (isShortWindow()) WaiveraSpacing.ExtraSmall else WaiveraSpacing.ExtraSmall + WaiveraSpacing.Hairline
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = inset)
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .clip(WaiveraShapes.SheetCard)
            .background(colors.surface),
    ) {
        WaiveraPressable(
            depth = PressDepth.Card,
            onClick = onClick,
            onClickLabel = onClickLabel,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WaiveraSizes.ListRowMinHeight)
                    .padding(CardPadding)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(CardGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Column(modifier = Modifier.weight(1f)) {
                    if (eyebrow != null) {
                        Text(
                            text = eyebrow,
                            style = WaiveraTextStyles.Kicker,
                            color = eyebrowColor,
                            modifier = Modifier.padding(bottom = WaiveraSpacing.XxSmall),
                        )
                    }
                    // Hyphenated names wrap after a hyphen, never mid-word (UI4-02).
                    Text(
                        text = headline.withBreaksAfterHyphens(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                        color = colors.text,
                        maxLines = headlineMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (quote != null) QuoteLine(quote = quote, ruleColor = quoteRuleColor)
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = SUPPORTING_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = WaiveraSpacing.XxSmall),
                        )
                    }
                }
                if (trailing != null) trailing()
                if (showChevron) {
                    Icon(
                        painter = painterResource(WaiveraIcons.ChevronRight),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(WaiveraSizes.Icon),
                    )
                }
            }
        }
        if (below != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CardPadding + WaiveraSizes.CardTile + CardGap,
                        end = CardPadding,
                        bottom = CardPadding,
                    ),
            ) {
                below()
            }
        }
    }
}

/** A quote on its own line behind a thin rule, as the concept draws a mention's snippet. */
@Composable
private fun QuoteLine(quote: String, ruleColor: Color) {
    Row(
        modifier = Modifier
            .padding(top = WaiveraSpacing.ExtraSmall)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .width(WaiveraSizes.QuoteRule)
                .fillMaxHeight()
                .background(ruleColor, WaiveraShapes.Circle),
        )
        Text(
            text = quote,
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.text,
            maxLines = QUOTE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The card's leading tile: 44dp with a 22dp glyph, as in the concept. */
@Composable
internal fun TodayTile(@DrawableRes icon: Int, tone: WaiveraTileTone, modifier: Modifier = Modifier) {
    WaiveraIconTile(
        icon = icon,
        tone = tone,
        size = WaiveraSizes.CardTile,
        iconSize = WaiveraSizes.CardTileGlyph,
        modifier = modifier,
    )
}

/**
 * The unread dot. Never the only signal: rows also say "Unread" to TalkBack and bold their text.
 * Opening the row (which marks it read) shrinks the dot away instead of dropping it.
 */
@Composable
internal fun UnreadDot(visible: Boolean, modifier: Modifier = Modifier) {
    WaiveraPopVisibility(visible = visible, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(WaiveraSpacing.Small)
                .background(WaiveraTheme.colors.unread, WaiveraShapes.Circle),
        )
    }
}

/**
 * A sheet section header with optional trailing actions, marked as a heading for TalkBack
 * navigation. Sections with a single link use the design system's `WaiveraSectionHeader`; this
 * one takes any trailing content (Activity's count and Mark all read).
 */
@Composable
internal fun TodaySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .padding(start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall, end = WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        // The heading keeps its own width on one line; the trailing actions get what is left
        // (they turn into icons at large font), so a word like "Activity" never breaks (UI3-02).
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WaiveraTheme.colors.text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** Inner padding of a Today card (14dp) and the gap between its tile and text. */
internal val CardPadding = WaiveraSpacing.MediumSmall + WaiveraSpacing.XxSmall
internal val CardGap = WaiveraSpacing.MediumSmall + WaiveraSpacing.XxSmall

private const val HEADLINE_MAX_LINES = 3
private const val SUPPORTING_MAX_LINES = 2
private const val QUOTE_MAX_LINES = 2

@WaiveraPreviews
@Composable
private fun TodayRowPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            TodaySectionHeader(title = "Needs you")
            TodayRow(
                headline = "Priya Raman mentioned you",
                description = "Unread. Priya Raman mentioned you",
                onClick = {},
                onClickLabel = "Open message",
                eyebrow = "Mention · 5m",
                quote = "the Hughes visit moved to 10",
                emphasized = true,
                leading = { TodayTile(icon = R.drawable.feature_today_ic_mention, tone = WaiveraTileTone.Rose) },
            )
            TodayRow(
                headline = "Client home visit",
                description = "Client home visit, in 25 min",
                onClick = {},
                onClickLabel = "Open event",
                eyebrow = "in 25 min",
                eyebrowColor = WaiveraTheme.colors.accent,
                supporting = "11:00 AM – 12:00 PM · 1200 W Lake St",
                leading = { TodayTile(icon = WaiveraIcons.Schedule, tone = WaiveraTileTone.Teal) },
                showChevron = true,
            )
        }
    }
}
