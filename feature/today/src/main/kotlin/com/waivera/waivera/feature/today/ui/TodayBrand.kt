package com.waivera.waivera.feature.today.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCelebrationBurst
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.CelebrationItem
import com.waivera.waivera.feature.today.model.DayPart
import com.waivera.waivera.feature.today.model.Greeting

/**
 * "Good morning, Alex" at the top of Today, a heading for TalkBack. No emoji: they belong to
 * celebrations only (`docs/design/voice.md`).
 */
@Composable
internal fun TodayGreeting(greeting: Greeting, modifier: Modifier = Modifier) {
    Text(
        text = greetingText(greeting),
        style = WaiveraTextStyles.Greeting,
        color = WaiveraTheme.colors.text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = WaiveraSpacing.Gutter,
                end = WaiveraSpacing.Gutter,
                top = WaiveraSpacing.MediumLarge,
                bottom = WaiveraSpacing.ExtraSmall,
            )
            .semantics { heading() },
    )
}

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
 * plain avatar, the one celebration emoji, the sentence on a soft brand-gradient card, and an easy "Send wishes"
 * that opens a direct chat with them. Confetti bursts over the card the first time it shows
 * (nothing with reduced motion). The card body opens the schedule, as the row did; the button
 * is its own 48dp target so TalkBack reaches it separately.
 */
@Composable
internal fun CelebrationCard(
    item: CelebrationItem,
    onOpen: () -> Unit,
    onSendWishes: () -> Unit,
    modifier: Modifier = Modifier,
    burstSeed: Int = 0,
) {
    val colors = WaiveraTheme.colors
    val sentence = celebrationText(item)
    val name = item.name ?: stringResource(R.string.feature_today_celebration_name_fallback)
    val label = stringResource(celebrationLabel(item))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WaiveraShapes.Hero)
                .background(colors.surface)
                .background(WaiveraBrand.wash())
                .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Hero),
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
                    Text(text = stringResource(celebrationEmoji(item)), style = WaiveraTextStyles.CelebrationEmoji)
                }
            }
            if (!item.isMe) {
                val wishesLabel = stringResource(R.string.feature_today_brand_send_wishes_a11y, name)
                WaiveraButton(
                    text = stringResource(R.string.feature_today_brand_send_wishes),
                    onClick = onSendWishes,
                    variant = WaiveraButtonVariant.Tonal,
                    leadingIcon = WaiveraIcons.Chat,
                    modifier = Modifier
                        .padding(
                            start = WaiveraSpacing.Medium + WaiveraSizes.AvatarMedium + WaiveraSpacing.MediumSmall,
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
            TodayGreeting(Greeting(DayPart.MORNING, firstName = "Alex"))
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
