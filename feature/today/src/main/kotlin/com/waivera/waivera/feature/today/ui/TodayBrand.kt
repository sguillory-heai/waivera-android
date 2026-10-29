package com.waivera.waivera.feature.today.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCelebrationBurst
import com.waivera.waivera.core.designsystem.component.WaiveraEmojiTile
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isExtraLargeFontScale
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.CelebrationItem
import com.waivera.waivera.feature.today.model.DayPart
import com.waivera.waivera.feature.today.model.Greeting

/** "Good morning, Alex", in the hero. No emoji: they belong to celebrations only (`docs/design/voice.md`). */
@Composable
internal fun greetingText(greeting: Greeting): String {
    val name = greeting.firstName
    return when (greeting.part) {
        DayPart.MORNING -> name?.let { stringResource(R.string.feature_today_brand_greeting_morning_named, it) }
            ?: stringResource(R.string.feature_today_brand_greeting_morning)
        DayPart.AFTERNOON -> name?.let { stringResource(R.string.feature_today_brand_greeting_afternoon_named, it) }
            ?: stringResource(R.string.feature_today_brand_greeting_afternoon)
        DayPart.EVENING -> name?.let { stringResource(R.string.feature_today_brand_greeting_evening_named, it) }
            ?: stringResource(R.string.feature_today_brand_greeting_evening)
    }
}

/**
 * A birthday or work anniversary today, as a small moment rather than a list row: the person's
 * plain avatar, the one celebration emoji in its tile, the sentence on a soft brand-gradient
 * card, and an easy "Send wishes" that opens a direct chat with them. Confetti bursts over the
 * card the first time it shows (nothing with reduced motion). The card body opens the schedule,
 * as the row did; the button is its own 48dp target so TalkBack reaches it separately.
 */
@Composable
internal fun CelebrationCard(
    item: CelebrationItem,
    onOpen: () -> Unit,
    onSendWishes: () -> Unit,
    modifier: Modifier = Modifier,
    burstSeed: Int = 0,
    openingWishes: Boolean = false,
) {
    val colors = WaiveraTheme.colors
    val sentence = celebrationText(item)
    val name = item.name ?: stringResource(R.string.feature_today_celebration_name_fallback)
    val label = stringResource(celebrationLabel(item))
    // At large font, or on a narrow card, indenting Send wishes under the sentence leaves too
    // little for its two words; it then starts at the card's content start, as on the Day
    // card (UI11-03). The card is always full width, so its width settles in one frame.
    val indentMinPx = with(LocalDensity.current) { WishesIndentMinCardWidth.roundToPx() }
    var narrow by remember { mutableStateOf(false) }
    val indentWishes = !narrow && !isExtraLargeFontScale()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = WaiveraSpacing.Gutter,
                vertical = WaiveraSpacing.ExtraSmall + WaiveraSpacing.Hairline,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { narrow = it.width < indentMinPx }
                .clip(WaiveraShapes.UpNextCard)
                .background(colors.surface)
                .background(WaiveraBrand.wash()),
        ) {
            WaiveraPressable(
                onClick = onOpen,
                onClickLabel = stringResource(R.string.feature_today_open_schedule),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = sentence },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WaiveraSpacing.Medium)
                        .clearAndSetSemantics { },
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaiveraAvatar(
                        name = name,
                        imageModel = AvatarModel.of(item.userId),
                        size = WaiveraSizes.AvatarMedium,
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Text(text = sentence, style = MaterialTheme.typography.titleSmall, color = colors.text)
                    }
                    // The same emoji tile as Schedule and Profile: one celebration treatment (UI9-07).
                    WaiveraEmojiTile(emoji = stringResource(celebrationEmoji(item)))
                }
            }
            if (!item.isMe) {
                val wishesLabel = stringResource(R.string.feature_today_brand_send_wishes_a11y, name)
                WaiveraButton(
                    text = stringResource(R.string.feature_today_brand_send_wishes),
                    onClick = onSendWishes,
                    variant = WaiveraButtonVariant.TonalOnWash,
                    leadingIcon = WaiveraIcons.Chat,
                    // The chat opens over the network: the button says so from the tap, at its size.
                    loadingText = stringResource(R.string.feature_today_brand_send_wishes_opening)
                        .takeIf { openingWishes },
                    maxLines = 1,
                    modifier = Modifier
                        .padding(
                            start = if (indentWishes) {
                                WaiveraSpacing.Medium + WaiveraSizes.AvatarMedium + WaiveraSpacing.MediumSmall
                            } else {
                                WaiveraSpacing.Medium
                            },
                            end = WaiveraSpacing.Medium,
                            bottom = WaiveraSpacing.Medium,
                        )
                        .semantics { contentDescription = wishesLabel },
                )
            }
        }
        WaiveraCelebrationBurst(trigger = item.userId, seed = burstSeed, modifier = Modifier.matchParentSize())
    }
}

/** Below this card width Send wishes starts at the card's content start, not under the sentence. */
private val WishesIndentMinCardWidth: Dp = 300.dp

@StringRes
private fun celebrationEmoji(item: CelebrationItem): Int = when {
    item.isBirthday && item.anniversaryYears != null -> R.string.feature_today_brand_celebration_emoji_both
    item.isBirthday -> R.string.feature_today_brand_celebration_emoji_birthday
    else -> R.string.feature_today_brand_celebration_emoji_anniversary
}

@StringRes
private fun celebrationLabel(item: CelebrationItem): Int = when {
    item.isBirthday && item.anniversaryYears != null -> R.string.feature_today_brand_celebration_label_both
    item.isBirthday -> R.string.feature_today_brand_celebration_label_birthday
    else -> R.string.feature_today_brand_celebration_label_anniversary
}

@WaiveraPreviews
@Composable
private fun TodayBrandPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            CelebrationCard(
                item = CelebrationItem("u1", "Jordan Lee", isBirthday = false, anniversaryYears = 5),
                onOpen = {},
                onSendWishes = {},
            )
            CelebrationCard(
                item = CelebrationItem("u2", "Sam Ortiz", isBirthday = true, anniversaryYears = null),
                onOpen = {},
                onSendWishes = {},
            )
        }
    }
}
