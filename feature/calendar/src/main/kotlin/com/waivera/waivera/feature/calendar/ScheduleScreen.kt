package com.waivera.waivera.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraSwap
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.enterAfterExit
import com.waivera.waivera.core.designsystem.theme.exit
import com.waivera.waivera.core.designsystem.theme.isExtraLargeFontScale
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.calendar.model.AgendaNow
import com.waivera.waivera.feature.calendar.model.PeriodTitle
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.schedule.AgendaCard
import com.waivera.waivera.feature.calendar.schedule.AgendaCardSkeleton
import com.waivera.waivera.feature.calendar.schedule.AgendaRailEntry
import com.waivera.waivera.feature.calendar.schedule.AgendaState
import com.waivera.waivera.feature.calendar.schedule.GridCell
import com.waivera.waivera.feature.calendar.schedule.NowDivider
import com.waivera.waivera.feature.calendar.schedule.ScheduleEffect
import com.waivera.waivera.feature.calendar.schedule.ScheduleGrid
import com.waivera.waivera.feature.calendar.schedule.SchedulePreviewData
import com.waivera.waivera.feature.calendar.schedule.ScheduleUiState
import com.waivera.waivera.feature.calendar.schedule.ScheduleViewModel
import com.waivera.waivera.feature.calendar.schedule.usesCompactCalendar
import com.waivera.waivera.feature.calendar.ui.CalendarIcons
import com.waivera.waivera.feature.calendar.ui.currentLocale
import com.waivera.waivera.feature.calendar.ui.periodTitleText
import com.waivera.waivera.feature.calendar.ui.zoneNoticeText
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entry point for the schedule tab. Owned by `:feature:calendar`; routed by `:app`.
 */
@Composable
fun ScheduleScreen(
    onAccountClick: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScheduleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val openConversation by rememberUpdatedState(onOpenConversation)
    val wishesFailed = stringResource(R.string.feature_calendar_wishes_failed)
    val retry = stringResource(R.string.feature_calendar_retry)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // Keeps today's "Now" divider and the ended cards current while the screen stays open.
    LaunchedEffect(viewModel) {
        while (true) {
            delay(NOW_TICK_MILLIS)
            viewModel.onResume()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScheduleEffect.OpenConversation -> openConversation(effect.conversationId)
                is ScheduleEffect.WishesFailed -> launch {
                    snackbarHost.currentSnackbarData?.dismiss()
                    val result = snackbarHost.showSnackbar(message = wishesFailed, actionLabel = retry)
                    if (result == SnackbarResult.ActionPerformed) viewModel.sendWishes(effect.userId)
                }
            }
        }
    }
    PreferWeekAtLargeFont(view = state.view, onSelectView = viewModel::selectView)
    Box(modifier = modifier) {
        ScheduleContent(
            state = state,
            actions = ScheduleActions(
                onAccountClick = onAccountClick,
                onOpenEvent = onOpenEvent,
                onSelectView = viewModel::selectView,
                onSelectDate = viewModel::selectDate,
                onStep = viewModel::step,
                onToday = viewModel::goToToday,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onRetryMilestones = viewModel::retryMilestones,
                onSendWishes = viewModel::sendWishes,
            ),
        )
        WaiveraSnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Everything the schedule screen can ask for. */
@Immutable
class ScheduleActions(
    val onAccountClick: () -> Unit = {},
    val onOpenEvent: (String) -> Unit = {},
    val onSelectView: (ScheduleView) -> Unit = {},
    val onSelectDate: (LocalDate) -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onToday: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryMilestones: () -> Unit = {},
    /** "Send wishes" on a milestone card, with the person's user id. */
    val onSendWishes: (String) -> Unit = {},
)

/** Stateless schedule screen, for previews and screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(state: ScheduleUiState, actions: ScheduleActions, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val jumpToToday = stringResource(R.string.feature_calendar_go_to_today)
        val refreshLabel = stringResource(R.string.feature_calendar_refresh)
        WaiveraTopBar(
            title = stringResource(R.string.feature_calendar_schedule_title),
            // Refresh is pull-to-refresh (and a TalkBack action on the list), not a third header icon.
            actions = if (state.showsJumpToToday) {
                listOf(TopBarAction(WaiveraIcons.JumpToToday, jumpToToday, actions.onToday))
            } else {
                emptyList()
            },
            account = rememberTopBarAccount(
                onClick = actions.onAccountClick,
                fallbackName = "",
            ),
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(refreshLabel) {
                                actions.onRefresh()
                                true
                            },
                        )
                    },
                contentPadding = PaddingValues(bottom = WaiveraSpacing.Large),
            ) {
                item(key = "views") { ViewPicker(state, actions) }
                item(key = "banners") { ScheduleBanners(state, actions) }
                // The period row and the grid read as one calendar card (concept A's sheet card).
                val gridShown = state.view != ScheduleView.DAY && state.cells.isNotEmpty()
                item(key = "period") {
                    PeriodRow(
                        state = state,
                        actions = actions,
                        modifier = Modifier.calendarCard(
                            shape = if (gridShown) CalendarCardTop else WaiveraShapes.SheetCard,
                            surface = WaiveraTheme.colors.surface,
                        ),
                    )
                }
                if (gridShown) {
                    item(key = "grid") {
                        // The grid moves with its title: a step slides both a third of the way
                        // in the same direction on the layout token, and a view change fades,
                        // so the step never reads half done (M1-06). Picking a day keeps the
                        // same period, so the grid recomposes in place.
                        WaiveraSwap(
                            targetState = GridFrame(state.title, state.view, state.cells),
                            modifier = Modifier.calendarCard(CalendarCardBottom, WaiveraTheme.colors.surface),
                            contentKey = { it.title },
                            direction = { from, to -> periodStepDirection(from.title, to.title) },
                        ) { frame ->
                            ScheduleGrid(
                                view = frame.view,
                                cells = frame.cells,
                                onSelectDate = actions.onSelectDate,
                                modifier = Modifier.padding(bottom = WaiveraSpacing.Small),
                            )
                        }
                    }
                }
                // The Day view's period row already names the day (UI2-27); a second heading repeated it.
                if (state.agenda !is AgendaState.Error && state.view != ScheduleView.DAY) {
                    item(key = "agendaHeader") { AgendaHeader(state) }
                }
                agendaItems(state, actions)
            }
        }
    }
}

private fun LazyListScope.agendaItems(state: ScheduleUiState, actions: ScheduleActions) {
    val itemModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
    when (val agenda = state.agenda) {
        AgendaState.Loading -> agendaSkeletonItem()
        // The failure is the banner above the grid, where it is seen without scrolling.
        is AgendaState.Error -> Unit
        AgendaState.Empty -> agendaEmptyItem()
        is AgendaState.Content -> {
            val now = state.agendaNow
            val timeline = state.view == ScheduleView.DAY
            agenda.items.forEachIndexed { index, card ->
                if (now != null && now.beforeKey == card.key) nowItem(now)
                item(key = card.key) {
                    val motion = WaiveraTheme.motion
                    val ended = now != null && card.key in now.endedKeys
                    // A leaving card is gone before its neighbours slide through its place (M1-06).
                    val placed = itemModifier.animateItem(
                        fadeInSpec = motion.state(),
                        placementSpec = motion.layout(),
                        fadeOutSpec = motion.exit(),
                    )
                    val agendaCard: @Composable (Modifier) -> Unit = { cardModifier ->
                        AgendaCard(
                            item = card,
                            currentYear = state.today.year,
                            onOpenEvent = actions.onOpenEvent,
                            ended = ended,
                            myUserId = state.myUserId,
                            onSendWishes = actions.onSendWishes,
                            openingWishesFor = state.openingWishesFor,
                            modifier = cardModifier,
                        )
                    }
                    // The Day view reads as concept B's timeline: a time and a tone beside each card.
                    if (timeline) {
                        AgendaRailEntry(
                            item = card,
                            ended = ended,
                            isLast = index == agenda.items.lastIndex,
                            modifier = placed,
                        ) { agendaCard(Modifier) }
                    } else {
                        agendaCard(placed)
                    }
                }
            }
            if (now != null && now.beforeKey == null) nowItem(now)
        }
    }
}

private fun LazyListScope.agendaSkeletonItem() {
    item(key = "loading") {
        // The skeleton leaves faster than the cards fade in over it (M1-16).
        Box(
            Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = WaiveraTheme.motion.exit()),
        ) {
            AgendaSkeleton()
        }
    }
}

private fun LazyListScope.agendaEmptyItem() {
    item(key = "empty") {
        val motion = WaiveraTheme.motion
        // The empty state waits for the leaving cards to clear, so it never sits on top of them
        // (M1-06).
        WaiveraEmptyState(
            title = stringResource(R.string.feature_calendar_empty_title),
            message = stringResource(R.string.feature_calendar_empty_message),
            illustration = WaiveraIllustration.Schedule,
            modifier = Modifier
                .animateItem(
                    fadeInSpec = motion.enterAfterExit(),
                    placementSpec = motion.layout(),
                    fadeOutSpec = motion.exit(),
                )
                .padding(WaiveraSpacing.Gutter),
        )
    }
}

private fun LazyListScope.nowItem(now: AgendaNow) {
    item(key = NOW_KEY) {
        val motion = WaiveraTheme.motion
        NowDivider(
            time = now.time,
            modifier = Modifier
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
                .animateItem(
                    fadeInSpec = motion.state(),
                    placementSpec = motion.layout(),
                    fadeOutSpec = motion.exit(),
                ),
        )
    }
}

private const val NOW_KEY = "now"

/** What the grid shows for one period; the period's title keys the slide. */
@Immutable
private data class GridFrame(val title: PeriodTitle, val view: ScheduleView, val cells: List<GridCell>)

/** Insets a piece of the calendar card by the gutter and gives it the card's surface and corners. */
private fun Modifier.calendarCard(shape: Shape, surface: Color): Modifier = this
    .padding(horizontal = WaiveraSpacing.Gutter)
    .clip(shape)
    .background(surface)

private val CalendarCardTop: Shape = RoundedCornerShape(
    topStart = WaiveraShapes.SheetCardRadius,
    topEnd = WaiveraShapes.SheetCardRadius,
)
private val CalendarCardBottom: Shape = RoundedCornerShape(
    bottomStart = WaiveraShapes.SheetCardRadius,
    bottomEnd = WaiveraShapes.SheetCardRadius,
)

/**
 * One status signal under the view picker, above the grid, so a failure is seen without
 * scrolling: a failed first load wins over offline, offline wins over a failed refresh.
 */
@Composable
private fun ScheduleBanners(state: ScheduleUiState, actions: ScheduleActions) {
    val bannerModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
    val retry = stringResource(R.string.feature_calendar_retry)
    val agenda = state.agenda
    Column {
        when {
            agenda is AgendaState.Error -> WaiveraBanner(
                title = stringResource(R.string.feature_calendar_error_title),
                message = stringResource(agenda.error.messageRes),
                tone = BannerTone.Error,
                actionLabel = retry,
                onAction = actions.onRetry,
                actionIcon = WaiveraIcons.Refresh,
                modifier = bannerModifier,
            )
            state.isOffline -> WaiveraBanner(
                message = stringResource(R.string.feature_calendar_offline),
                tone = BannerTone.Offline,
                actionLabel = retry,
                onAction = actions.onRetry,
                actionIcon = WaiveraIcons.Refresh,
                modifier = bannerModifier,
            )
            // A stale-data notice, so it follows the status-banner rule: compact at large font,
            // with the title saying what happened and the detail a tap away (UI4-05).
            state.feedError != null -> WaiveraBanner(
                title = stringResource(R.string.feature_calendar_feed_refresh_failed_title),
                message = stringResource(R.string.feature_calendar_feed_refresh_failed_detail),
                // The saved schedule is still on screen, so this is a warning; red is for a failed action.
                tone = BannerTone.Warning,
                actionLabel = retry,
                onAction = actions.onRetry,
                actionIcon = WaiveraIcons.Refresh,
                compact = isLargeFontScale(),
                modifier = bannerModifier,
            )
        }
        if (state.milestonesError != null && !state.isOffline && agenda !is AgendaState.Error) {
            WaiveraBanner(
                message = stringResource(R.string.feature_calendar_milestones_failed),
                tone = BannerTone.Warning,
                actionLabel = retry,
                onAction = actions.onRetryMilestones,
                actionIcon = WaiveraIcons.Refresh,
                modifier = bannerModifier,
            )
        }
        val notice = state.zoneNotice
        if (notice != null && notice.isFallback) {
            WaiveraBanner(message = zoneNoticeText(notice), tone = BannerTone.Info, modifier = bannerModifier)
        }
    }
}

@Composable
private fun ViewPicker(state: ScheduleUiState, actions: ScheduleActions) {
    val views = ScheduleView.entries
    WaiveraSegmentedControl(
        options = views.map { stringResource(it.labelRes()) },
        selectedIndex = views.indexOf(state.view),
        onSelect = { actions.onSelectView(views[it]) },
        modifier = Modifier.padding(
            start = WaiveraSpacing.Gutter,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.Small,
            bottom = WaiveraSpacing.ExtraSmall,
        ),
    )
}

/**
 * Previous, the period title, next; the saved zone is a quiet caption under the title. In the Day
 * view the title is the only day heading, so it carries the Today chip. At large font the chip
 * goes under the title, so the title has the whole width between the arrows: beside the chip,
 * "Sun, November 1" had no room left and stranded the "1" (UI5-04). From 150% the Day title
 * abbreviates its month ("Sun, Nov 1"), so it stays on one line over the chip (UI10-09).
 */
@Composable
private fun PeriodRow(state: ScheduleUiState, actions: ScheduleActions, modifier: Modifier = Modifier) {
    val showToday = state.view == ScheduleView.DAY && state.isTodaySelected
    val todayUnderTitle = isLargeFontScale()
    Column(
        modifier = modifier.padding(
            horizontal = WaiveraSpacing.ExtraSmall,
            vertical = WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaiveraIconButton(
                icon = CalendarIcons.ChevronLeft,
                contentDescription = stringResource(R.string.feature_calendar_previous_period),
                onClick = { actions.onStep(-1) },
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The title slides the way the period moved, so a step reads as forward or back.
                WaiveraSwap(
                    targetState = state.title,
                    modifier = Modifier.weight(1f, fill = false),
                    contentAlignment = Alignment.Center,
                    direction = ::periodStepDirection,
                ) { title ->
                    Text(
                        text = periodTitleText(title, short = isExtraLargeFontScale()),
                        style = MaterialTheme.typography.titleMedium,
                        color = WaiveraTheme.colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                if (showToday && !todayUnderTitle) {
                    WaiveraStatusBadge(text = stringResource(R.string.feature_calendar_today), tone = BadgeTone.Accent)
                }
            }
            WaiveraIconButton(
                icon = WaiveraIcons.ChevronRight,
                contentDescription = stringResource(R.string.feature_calendar_next_period),
                onClick = { actions.onStep(1) },
            )
        }
        if (showToday && todayUnderTitle) {
            WaiveraStatusBadge(
                text = stringResource(R.string.feature_calendar_today),
                tone = BadgeTone.Accent,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        val notice = state.zoneNotice
        if (notice != null && !notice.isFallback) {
            Text(
                text = zoneNoticeText(notice),
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * At 150% font and above a month grid fills the first screen, so the first time the tab shows
 * Month at that size it switches to the week strip. Picking Month again afterwards sticks.
 */
@Composable
private fun PreferWeekAtLargeFont(view: ScheduleView, onSelectView: (ScheduleView) -> Unit) {
    val compact = usesCompactCalendar(LocalDensity.current.fontScale)
    var applied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(compact) {
        if (!applied && compact && view == ScheduleView.MONTH) onSelectView(ScheduleView.WEEK)
        applied = true
    }
}

/**
 * The selected day above its agenda: the day in a calendar tile, and the Today chip when it is
 * today, on a plain card. It used to wear the brand wash; v5's gradient rule keeps the wash for
 * celebrations, so the header is the same plain card on every day (UI8-14 kept one wash per screen).
 */
@Composable
private fun AgendaHeader(state: ScheduleUiState) {
    val colors = WaiveraTheme.colors
    Box(
        modifier = Modifier
            .padding(
                start = WaiveraSpacing.Gutter,
                end = WaiveraSpacing.Gutter,
                top = WaiveraSpacing.Medium,
                bottom = WaiveraSpacing.Small,
            )
            .fillMaxWidth()
            .clip(WaiveraShapes.SheetCard)
            .background(colors.surface)
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
    ) {
        AgendaDayHeading(date = state.selectedDate, isToday = state.isTodaySelected)
    }
}

/**
 * The Week and Month agenda's day heading. From 150% it reads "Sun, Nov 1", as the Day title
 * does (UI10-09), so at 200% beside the Today chip the day number never stands on a line of its
 * own ("Sunday, / November / 1", UI11-04); TalkBack still hears the whole day.
 */
@Composable
internal fun AgendaDayHeading(date: LocalDate, isToday: Boolean, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val spoken = ScheduleText.fullDay(date, locale)
    val shown = if (isExtraLargeFontScale()) ScheduleText.weekdayMonthDay(date, locale, shortMonth = true) else spoken
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraIconTile(icon = WaiveraIcons.Calendar, tone = WaiveraTileTone.Teal)
        Text(
            text = shown,
            style = MaterialTheme.typography.titleMedium,
            color = WaiveraTheme.colors.text,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics {
                    heading()
                    contentDescription = spoken
                },
        )
        if (isToday) {
            WaiveraStatusBadge(text = stringResource(R.string.feature_calendar_today), tone = BadgeTone.Accent)
        }
    }
}

@Composable
private fun AgendaSkeleton() {
    val visible = rememberSkeletonVisible(loading = true)
    val caption = stringResource(R.string.feature_calendar_loading)
    if (!visible) return
    val pulse = rememberSkeletonPulse()
    // The cards' own inset, so the bones sit exactly where the loaded cards land.
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = caption }) {
        repeat(SKELETON_ROWS) { index ->
            AgendaCardSkeleton(
                pulse = pulse,
                titleFraction = SKELETON_TITLE_FRACTIONS[index % SKELETON_TITLE_FRACTIONS.size],
                modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
            )
        }
    }
}

private fun ScheduleView.labelRes(): Int = when (this) {
    ScheduleView.DAY -> R.string.feature_calendar_view_day
    ScheduleView.WEEK -> R.string.feature_calendar_view_week
    ScheduleView.MONTH -> R.string.feature_calendar_view_month
}

private const val SKELETON_ROWS = 3
private val SKELETON_TITLE_FRACTIONS = listOf(0.7f, 0.55f, 0.8f)
private const val NOW_TICK_MILLIS = 60_000L

@WaiveraPreviews
@Composable
private fun ScheduleContentPreview() {
    WaiveraTheme {
        ScheduleContent(state = SchedulePreviewData.month(), actions = ScheduleActions())
    }
}

@WaiveraPreviews
@Composable
private fun ScheduleContentWeekPreview() {
    WaiveraTheme {
        ScheduleContent(state = SchedulePreviewData.week(), actions = ScheduleActions())
    }
}

/** Today in the Day view: the "Now" divider and the ended overnight shift (UI4-12). */
@WaiveraPreviews
@Composable
private fun ScheduleContentDayPreview() {
    WaiveraTheme {
        ScheduleContent(state = SchedulePreviewData.state(ScheduleView.DAY), actions = ScheduleActions())
    }
}

/** Which way the period moved: forward, back, or 0 when only the view changed. */
private fun periodStepDirection(from: PeriodTitle, to: PeriodTitle): Int =
    if (from::class != to::class) 0 else to.startDate().compareTo(from.startDate()).coerceIn(-1, 1)

private fun PeriodTitle.startDate(): LocalDate = when (this) {
    is PeriodTitle.Month -> month.atDay(1)
    is PeriodTitle.Day -> date
    is PeriodTitle.WeekInMonth -> start
    is PeriodTitle.WeekAcrossMonths -> start
}
