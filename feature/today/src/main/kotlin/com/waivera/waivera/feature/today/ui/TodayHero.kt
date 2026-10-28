package com.waivera.waivera.feature.today.ui

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraCelebrationBurst
import com.waivera.waivera.core.designsystem.component.WaiveraHeroCard
import com.waivera.waivera.core.designsystem.component.WaiveraHeroSection
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTopBar
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTrail
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraProgressRing
import com.waivera.waivera.core.designsystem.component.WaiveraRewardRing
import com.waivera.waivera.core.designsystem.component.WaiveraStatRow
import com.waivera.waivera.core.designsystem.component.WaiveraStatTile
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.NextUpItem
import com.waivera.waivera.feature.today.model.TodayProgress
import com.waivera.waivera.feature.today.model.TodayStats
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.model.UpcomingLabel

/**
 * The top of Today (concept A): the plum hero with the bar, the date and greeting, the glass
 * summary card (ring, what needs you, what's next) and the three stat tiles. When everything is
 * handled it becomes the reward: the big gradient ring and "You're all caught up 🎉".
 */
@Composable
internal fun TodayHero(state: TodayUiState, callbacks: TodayCallbacks, modifier: Modifier = Modifier) {
    val caughtUp = state.isAllCaughtUp
    WaiveraHeroSection(
        modifier = modifier,
        trail = if (caughtUp) WaiveraHeroTrail.Reward else WaiveraHeroTrail.Tab,
        topBar = {
            WaiveraHeroTopBar(
                title = stringResource(R.string.feature_today_today_title),
                account = rememberTopBarAccount(onClick = callbacks.onAccountClick, fallbackName = state.accountName),
            )
        },
    ) {
        if (caughtUp) {
            RewardHeroBody(state)
        } else {
            DayHeroBody(state, onOpenEvent = callbacks.onOpenEvent)
        }
    }
}

@Composable
private fun DayHeroBody(state: TodayUiState, onOpenEvent: (String) -> Unit) {
    val formats = rememberTodayFormats()
    val hero = WaiveraHero.colors
    Column(modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall)) {
        state.date?.let { date ->
            val spoken = formats.longDay(date)
            Text(
                text = formats.eyebrowDay(date),
                style = WaiveraTextStyles.HeroEyebrow,
                color = hero.onHeaderSecondary,
                modifier = Modifier
                    .padding(top = WaiveraSpacing.ExtraSmall)
                    .semantics { contentDescription = spoken },
            )
        }
        state.greeting?.let { greeting ->
            Text(
                text = greetingText(greeting),
                style = WaiveraTextStyles.HeroGreeting,
                color = hero.onHeader,
                modifier = Modifier
                    .padding(top = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)
                    .semantics { heading() },
            )
        }
    }
    Spacer(Modifier.height(WaiveraSpacing.Medium + WaiveraSpacing.XxSmall))
    val progress = state.progress
    val cardModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter)
    when {
        progress != null -> SummaryCard(
            progress = progress,
            next = state.nextUp.items.firstOrNull(),
            onOpenEvent = onOpenEvent,
            modifier = cardModifier,
        )
        state.loadError == null -> SummaryPlaceholder(modifier = cardModifier)
        else -> Unit
    }
    Spacer(Modifier.height(WaiveraSpacing.Small + WaiveraSpacing.XxSmall))
    WaiveraStatRow(modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter)) {
        CountTile(value = state.stats.eventsToday, label = R.plurals.feature_today_brand_stat_events_today)
        CountTile(value = state.stats.unreadChats, label = R.plurals.feature_today_brand_stat_unread_chats)
        CountTile(value = state.stats.eventsThisWeek, label = R.plurals.feature_today_brand_stat_events_week)
    }
    Spacer(Modifier.height(WaiveraSpacing.MediumSmall))
}

/**
 * The ring with what needs the person beside it, and the next item under a hairline. At large
 * font the ring sits above the words so the headline keeps the card's full width.
 */
@Composable
private fun SummaryCard(
    progress: TodayProgress,
    next: NextUpItem?,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraHeroCard(modifier = modifier) {
        val ring: @Composable () -> Unit = {
            WaiveraProgressRing(
                done = progress.handled,
                total = progress.total,
                caption = stringResource(R.string.feature_today_brand_ring_caption),
                contentDescription = stringResource(
                    R.string.feature_today_brand_ring_a11y,
                    progress.handled,
                    progress.total,
                ),
            )
        }
        if (isLargeFontScale()) {
            Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
                ring()
                SummaryText(progress)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium + WaiveraSpacing.XxSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ring()
                Column(modifier = Modifier.weight(1f)) { SummaryText(progress) }
            }
        }
        if (next != null) {
            NextRow(item = next, onClick = { onOpenEvent(next.eventId) })
        }
    }
}

@Composable
private fun SummaryText(progress: TodayProgress) {
    val hero = WaiveraHero.colors
    Text(
        text = if (progress.open > 0) {
            pluralStringResource(R.plurals.feature_today_brand_needs_you_count, progress.open, progress.open)
        } else {
            stringResource(R.string.feature_today_brand_needs_you_none)
        },
        style = WaiveraTextStyles.HeroTitle,
        color = hero.onHeader,
    )
    Text(
        text = if (progress.handled > 0) {
            stringResource(R.string.feature_today_brand_handled_since, progress.handled)
        } else {
            stringResource(R.string.feature_today_brand_handled_none)
        },
        style = WaiveraTextStyles.HeroBody,
        color = hero.onHeaderSecondary,
        modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
    )
}

/**
 * "NEXT · 11:00 AM / Client home visit" with a countdown chip ("in 30 min") while it is close.
 * Later items name their day in the kicker instead. It opens the event.
 */
@Composable
private fun NextRow(item: NextUpItem, onClick: () -> Unit) {
    val formats = rememberTodayFormats()
    val hero = WaiveraHero.colors
    val title = item.title ?: stringResource(R.string.feature_today_invitation_untitled)
    val upcoming = upcomingText(item.time, formats)
    val start = item.start
    val countdown = item.time is UpcomingLabel.InMinutes || item.time is UpcomingLabel.InHours
    val kicker = when {
        item.isHappeningNow -> upcoming
        countdown && start != null -> stringResource(R.string.feature_today_brand_next, formats.time(start))
        else -> stringResource(R.string.feature_today_brand_next, upcoming)
    }
    val description = stringResource(R.string.feature_today_brand_next_a11y, title, upcoming)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaiveraSpacing.Hairline)
                .background(hero.cardBorder),
        )
        WaiveraPressable(
            onClick = onClick,
            onClickLabel = stringResource(R.string.feature_today_open_event),
            shape = WaiveraShapes.Chip,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WaiveraSpacing.MediumSmall)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(WaiveraIcons.Schedule),
                    contentDescription = null,
                    tint = hero.nextIcon,
                    modifier = Modifier.size(WaiveraSizes.IconTileSmallGlyph),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formats.upper(kicker),
                        style = WaiveraTextStyles.HeroEyebrow,
                        color = hero.onHeaderSecondary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = hero.onHeader,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (countdown) {
                    Text(
                        text = upcoming,
                        style = WaiveraTextStyles.Chip,
                        color = hero.onChip,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .background(hero.chip, WaiveraShapes.CountBadge)
                            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.XxSmall),
                    )
                }
            }
        }
    }
}

/** The card's shape while Needs you loads: the ring's track and two faint lines, without numbers. */
@Composable
private fun SummaryPlaceholder(modifier: Modifier = Modifier) {
    val hero = WaiveraHero.colors
    val loading = stringResource(R.string.feature_today_brand_loading_summary)
    WaiveraHeroCard(modifier = modifier.clearAndSetSemantics { contentDescription = loading }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium + WaiveraSpacing.XxSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(WaiveraSizes.Ring)
                    .background(hero.tile, WaiveraShapes.Circle),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                PlaceholderLine(fraction = PLACEHOLDER_TITLE)
                PlaceholderLine(fraction = PLACEHOLDER_BODY)
            }
        }
    }
}

@Composable
private fun PlaceholderLine(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(WaiveraSizes.SkeletonLine)
            .background(WaiveraHero.colors.tile, WaiveraShapes.CountBadge),
    )
}

/** A stat tile for a count; a dash (and "still loading" for TalkBack) until its source loads. */
@Composable
private fun RowScope.CountTile(value: Int?, @PluralsRes label: Int) {
    val words = pluralStringResource(label, value ?: 0)
    WaiveraStatTile(
        value = value?.toString() ?: stringResource(R.string.feature_today_brand_stat_unknown),
        label = words,
        contentDescription = value?.let { stringResource(R.string.feature_today_brand_stat_a11y, it, words) }
            ?: stringResource(R.string.feature_today_brand_stat_loading_a11y, words),
    )
}

/** All caught up: the reward ring, the one 🎉 headline, the handled count and the day's stats. */
@Composable
private fun RewardHeroBody(state: TodayUiState) {
    val hero = WaiveraHero.colors
    val progress = state.progress
    val handled = progress?.handled ?: 0
    val name = state.greeting?.firstName
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaiveraRewardRing(
                contentDescription = null,
                modifier = Modifier.padding(top = WaiveraSizes.RewardHalo / 2),
            )
            Text(
                text = stringResource(R.string.feature_today_all_caught_up_title),
                style = WaiveraTextStyles.HeroReward,
                color = hero.onHeader,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = WaiveraSpacing.Large)
                    .semantics { heading() },
            )
            Text(
                text = when {
                    handled <= 0 -> stringResource(R.string.feature_today_all_caught_up_message)
                    name != null -> pluralStringResource(
                        R.plurals.feature_today_brand_caught_up_named,
                        handled,
                        handled,
                        name,
                    )
                    else -> pluralStringResource(R.plurals.feature_today_brand_caught_up, handled, handled)
                },
                style = WaiveraTextStyles.HeroBody,
                color = hero.onHeaderSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
            )
        }
        // Once per day, not on every scroll back; nothing with reduced motion.
        WaiveraCelebrationBurst(trigger = state.date ?: Unit, modifier = Modifier.matchParentSize())
    }
    Spacer(Modifier.height(WaiveraSpacing.MediumLarge + WaiveraSpacing.XxSmall))
    WaiveraStatRow(modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter)) {
        HandledTile(progress)
        CountTile(value = state.stats.eventsToday, label = R.plurals.feature_today_brand_stat_events_today)
        CountTile(value = state.stats.eventsThisWeek, label = R.plurals.feature_today_brand_stat_events_week)
    }
    Spacer(Modifier.height(WaiveraSpacing.MediumSmall))
}

@Composable
private fun RowScope.HandledTile(progress: TodayProgress?) {
    val label = stringResource(R.string.feature_today_brand_stat_handled)
    WaiveraStatTile(
        value = progress?.let {
            stringResource(R.string.feature_today_brand_stat_fraction, it.handled, it.total)
        } ?: stringResource(R.string.feature_today_brand_stat_unknown),
        label = label,
        contentDescription = progress?.let {
            stringResource(R.string.feature_today_brand_ring_a11y, it.handled, it.total)
        } ?: stringResource(R.string.feature_today_brand_stat_loading_a11y, label),
    )
}

private const val PLACEHOLDER_TITLE = 0.8f
private const val PLACEHOLDER_BODY = 0.55f

@WaiveraPreviews
@Composable
private fun TodayHeroPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            TodayHero(state = TodayPreviewStates.populated(), callbacks = TodayPreviewStates.callbacks())
        }
    }
}

@WaiveraPreviews
@Composable
private fun TodayRewardHeroPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            TodayHero(state = TodayPreviewStates.caughtUp(), callbacks = TodayPreviewStates.callbacks())
        }
    }
}

@WaiveraPreviews
@Composable
private fun TodayHeroLoadingPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            TodayHero(
                state = TodayPreviewStates.populated().copy(progress = null, stats = TodayStats()),
                callbacks = TodayPreviewStates.callbacks(),
            )
        }
    }
}
