package com.waivera.waivera.feature.today.ui

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraCelebrationBurst
import com.waivera.waivera.core.designsystem.component.WaiveraHeroCard
import com.waivera.waivera.core.designsystem.component.WaiveraHeroCardContent
import com.waivera.waivera.core.designsystem.component.WaiveraHeroSection
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTopBar
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTrail
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraProgressRing
import com.waivera.waivera.core.designsystem.component.WaiveraRewardRing
import com.waivera.waivera.core.designsystem.component.WaiveraStat
import com.waivera.waivera.core.designsystem.component.WaiveraStatGroup
import com.waivera.waivera.core.designsystem.component.rememberLatestNonNull
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.component.skeletonLineHeight
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isExtraLargeFontScale
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.designsystem.theme.isShortWindow
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.NeedsYouKind
import com.waivera.waivera.feature.today.model.NeedsYouKinds
import com.waivera.waivera.feature.today.model.NextUpItem
import com.waivera.waivera.feature.today.model.TodayProgress
import com.waivera.waivera.feature.today.model.TodayStats
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.model.UpcomingLabel

/**
 * The top of Today (concept A): the plum hero with the bar, the date and greeting, the glass
 * summary card (ring, what needs you, what's next) and the three stat tiles. When everything is
 * handled it becomes the reward: the big solid ring and "You're all caught up 🎉".
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

/**
 * On a short window (a 640dp phone) the greeting, ring, card and stat tiles compact, so at least
 * two Needs you rows show under the hero instead of one.
 */
@Composable
private fun DayHeroBody(state: TodayUiState, onOpenEvent: (String) -> Unit) {
    val formats = rememberTodayFormats()
    val hero = WaiveraHero.colors
    val compact = isShortWindow()
    val compactTiles = compact && !isLargeFontScale()
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
                style = if (compact) WaiveraTextStyles.HeroGreetingCompact else WaiveraTextStyles.HeroGreeting,
                color = hero.onHeader,
                modifier = Modifier
                    .padding(top = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)
                    .semantics { heading() },
            )
        }
    }
    Spacer(Modifier.height(if (compact) WaiveraSpacing.Small else WaiveraSpacing.Medium + WaiveraSpacing.XxSmall))
    val progress = state.progress
    val shownProgress = rememberLatestNonNull(progress)
    val loading = progress == null
    // The placeholder hands over to the summary on the shared load swap, so the first screen
    // settles in place instead of cutting on the frame the day loads (M3-04). The glass card is
    // one surface around the swap and only what is inside it swaps, so the card itself never
    // blinks out between the two (M4-02). A failed first load shows no card, as before.
    if (!loading || state.loadError == null) {
        val placeholderSpeech = stringResource(R.string.feature_today_brand_loading_summary)
        val speech = if (loading) Modifier.clearAndSetSemantics { contentDescription = placeholderSpeech } else Modifier
        WaiveraHeroCard(
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter).then(speech),
            compact = compact,
        ) {
            WaiveraLoadSwap(loading = loading, modifier = Modifier.fillMaxWidth()) { showsPlaceholder ->
                WaiveraHeroCardContent(compact = compact) {
                    when {
                        showsPlaceholder -> SummaryPlaceholder(compact = compact)
                        // It keeps the day it showed if the summary goes back to loading, while it fades out.
                        shownProgress != null -> SummaryCard(
                            progress = shownProgress,
                            kinds = state.needsYou.kinds,
                            next = state.nextUp.featured,
                            onOpenEvent = onOpenEvent,
                            compact = compact,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(if (compact) WaiveraSpacing.Small else WaiveraSpacing.Small + WaiveraSpacing.XxSmall))
    WaiveraStatGroup(
        stats = listOf(
            countStat(state.stats.eventsToday, R.plurals.feature_today_brand_stat_events_today),
            countStat(state.stats.unreadChats, R.plurals.feature_today_brand_stat_unread_chats),
            countStat(state.stats.eventsThisWeek, R.plurals.feature_today_brand_stat_events_week),
        ),
        compact = compactTiles,
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
    )
    Spacer(Modifier.height(WaiveraSpacing.MediumSmall))
}

/**
 * The ring with what needs the person beside it, and the next item under a hairline: the summary
 * card's content, drawn inside the shared glass card. At large font the ring sits above the
 * words so the headline keeps the card's full width.
 */
@Composable
private fun ColumnScope.SummaryCard(
    progress: TodayProgress,
    kinds: NeedsYouKinds,
    next: NextUpItem?,
    onOpenEvent: (String) -> Unit,
    compact: Boolean = false,
) {
    // The count inside the ring grows to 130%, which the compact ring has no room for.
    val smallRing = compact && !isLargeFontScale()
    val ring: @Composable () -> Unit = {
        WaiveraProgressRing(
            size = if (smallRing) WaiveraSizes.RingCompact else WaiveraSizes.Ring,
            stroke = if (smallRing) WaiveraSizes.RingStrokeCompact else WaiveraSizes.RingStroke,
            compact = smallRing,
            done = progress.handled,
            total = progress.total,
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
            SummaryText(progress, kinds)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium + WaiveraSpacing.XxSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ring()
            Column(modifier = Modifier.weight(1f)) { SummaryText(progress, kinds) }
        }
    }
    if (next != null) {
        NextRow(item = next, compact = compact, onClick = { onOpenEvent(next.eventId) })
    }
}

/**
 * "4 things need you" and, under it, what is waiting by kind ("2 messages · 1 invite · 1
 * ticket"). The ring already shows how many are handled, so the line under the headline says
 * something new instead of repeating it (owner's pick, option A). A kind with nothing waiting is
 * left out, and with nothing waiting there is no line at all.
 */
@Composable
private fun SummaryText(progress: TodayProgress, kinds: NeedsYouKinds) {
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
    val breakdown = needsYouBreakdown(kinds) ?: return
    Text(
        text = breakdown,
        style = WaiveraTextStyles.HeroBody,
        color = hero.onHeaderSecondary,
        modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
    )
}

/** The kinds waiting, each as its own plural, joined with " · "; null when nothing is waiting. */
@Composable
private fun needsYouBreakdown(kinds: NeedsYouKinds): String? {
    val parts = kinds.parts
    if (parts.isEmpty()) return null
    val separator = stringResource(R.string.feature_today_brand_kind_separator)
    return parts.map { (kind, count) -> pluralStringResource(kind.pluralRes, count, count) }.joinToString(separator)
}

/** The plural that names [this] kind with its count ("2 messages"). */
@get:PluralsRes
internal val NeedsYouKind.pluralRes: Int
    get() = when (this) {
        NeedsYouKind.NotSent -> R.plurals.feature_today_brand_kind_not_sent
        NeedsYouKind.Message -> R.plurals.feature_today_brand_kind_messages
        NeedsYouKind.Mention -> R.plurals.feature_today_brand_kind_mentions
        NeedsYouKind.Invitation -> R.plurals.feature_today_brand_kind_invites
        NeedsYouKind.Ticket -> R.plurals.feature_today_brand_kind_tickets
    }

/**
 * "NEXT · 11:00 AM / Client home visit" with a countdown chip ("in 30 min") while it is close.
 * Later items name their day in the kicker instead. It opens the event.
 */
@Composable
private fun NextRow(item: NextUpItem, compact: Boolean, onClick: () -> Unit) {
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
            NextLine(
                kicker = kicker,
                title = title,
                countdown = upcoming.takeIf { countdown },
                modifier = Modifier
                    .padding(top = if (compact) WaiveraSpacing.Small else WaiveraSpacing.MediumSmall)
                    .clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The Next row's clock icon, kicker, title and [countdown] chip, drawn without semantics of
 * their own: the row around it speaks for them. Below 150% the chip sits at the row's end.
 *
 * From 150% the chip moves under the title, so the kicker and the title get the whole width
 * beside the icon (UI11-01). The kicker is one unbreakable unit ("NEXT · 11:00 AM": the string
 * and the time join their parts with no-break spaces), on its own line, and the title starts
 * on the next line with up to two lines of its own, so a tall font never spends the lines on
 * the kicker and cuts the title short.
 */
@Composable
internal fun NextLine(kicker: String, title: String, countdown: String?, modifier: Modifier = Modifier) {
    val formats = rememberTodayFormats()
    val hero = WaiveraHero.colors
    val stacked = isExtraLargeFontScale()
    val chip: @Composable (Modifier) -> Unit = { chipModifier ->
        if (countdown != null) {
            Text(
                text = countdown,
                style = WaiveraTextStyles.Chip,
                color = hero.onChip,
                maxLines = 1,
                softWrap = false,
                modifier = chipModifier
                    .background(hero.chip, WaiveraShapes.CountBadge)
                    .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.XxSmall),
            )
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
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
                maxLines = NEXT_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (stacked) chip(Modifier.padding(top = WaiveraSpacing.ExtraSmall))
        }
        if (!stacked) chip(Modifier)
    }
}

/**
 * The card's shape while Needs you loads: the ring's track, the headline and what-is-waiting lines, and
 * the Next row under its hairline, without numbers. Each line reserves its text style's height,
 * so the card is as tall as the summary that replaces it. TalkBack hears the card's "loading"
 * description instead, set on the shared glass card.
 */
@Composable
private fun ColumnScope.SummaryPlaceholder(compact: Boolean = false) {
    val hero = WaiveraHero.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium + WaiveraSpacing.XxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact && !isLargeFontScale()) WaiveraSizes.RingCompact else WaiveraSizes.Ring)
                .background(hero.tile, WaiveraShapes.Circle),
        )
        Column(modifier = Modifier.weight(1f)) {
            PlaceholderLine(fraction = PLACEHOLDER_TITLE, style = WaiveraTextStyles.HeroTitle)
            PlaceholderLine(
                fraction = PLACEHOLDER_BODY,
                style = WaiveraTextStyles.HeroBody,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaiveraSpacing.Hairline)
                .background(hero.cardBorder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (compact) WaiveraSpacing.Small else WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(WaiveraSizes.IconTileSmallGlyph)
                    .background(hero.tile, WaiveraShapes.Circle),
            )
            Column(modifier = Modifier.weight(1f)) {
                PlaceholderLine(fraction = PLACEHOLDER_KICKER, style = WaiveraTextStyles.HeroEyebrow)
                PlaceholderLine(fraction = PLACEHOLDER_NEXT, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** One line of [style]'s height with a glass bar through it. */
@Composable
private fun PlaceholderLine(fraction: Float, style: TextStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(skeletonLineHeight(style)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaiveraSizes.SkeletonLine)
                .background(WaiveraHero.colors.tile, WaiveraShapes.CountBadge),
        )
    }
}

/** A stat for a count; a dash (and "still loading" for TalkBack) until its source loads. */
@Composable
private fun countStat(value: Int?, @PluralsRes label: Int): WaiveraStat {
    val words = pluralStringResource(label, value ?: 0)
    return WaiveraStat(
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
    WaiveraStatGroup(
        stats = listOf(
            handledStat(progress),
            countStat(value = state.stats.eventsToday, label = R.plurals.feature_today_brand_stat_events_today),
            countStat(value = state.stats.eventsThisWeek, label = R.plurals.feature_today_brand_stat_events_week),
        ),
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
    )
    Spacer(Modifier.height(WaiveraSpacing.MediumSmall))
}

@Composable
private fun handledStat(progress: TodayProgress?): WaiveraStat {
    val label = stringResource(R.string.feature_today_brand_stat_handled)
    return WaiveraStat(
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
private const val NEXT_TITLE_LINES = 2
private const val PLACEHOLDER_BODY = 0.55f
private const val PLACEHOLDER_KICKER = 0.35f
private const val PLACEHOLDER_NEXT = 0.6f

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
