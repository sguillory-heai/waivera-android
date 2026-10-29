package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraExpandVisibility
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraPullToRefreshBox
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraSwap
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.showUndo
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.SwapAxis
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.exit
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Entry point for the bookmarks screen. Owned by `:feature:profile`; routed by `:app`. */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenMessage: (conversationId: Long, messageId: Long, threadRootMessageId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BookmarksViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val words = BookmarkSearchWords(
        threadReply = stringResource(R.string.feature_profile_bookmarks_thread_reply),
        thread = stringResource(R.string.feature_profile_bookmarks_search_word_thread),
        message = stringResource(R.string.feature_profile_bookmarks_search_word_message),
        photo = stringResource(R.string.feature_profile_bookmarks_photo),
        file = stringResource(R.string.feature_profile_bookmarks_search_word_file),
    )
    LaunchedEffect(words) { viewModel.setSearchWords(words) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BookmarksEffect.Removed -> snackbar.showUndo(
                    message = resources.getString(R.string.feature_profile_bookmarks_removed),
                    undoLabel = resources.getString(R.string.feature_profile_undo),
                    onUndo = { viewModel.undoRemove(effect.messageId) },
                )
                is BookmarksEffect.RemoveFailed -> {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(R.string.feature_profile_bookmarks_remove_failed),
                        actionLabel = resources.getString(R.string.feature_profile_try_again),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.remove(effect.messageId)
                    }
                }
                is BookmarksEffect.RestoreFailed ->
                    snackbar.showSnackbar(resources.getString(R.string.feature_profile_bookmarks_restore_failed))
            }
        }
    }
    BookmarksContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onRefresh = viewModel::refresh,
        onRetryConnection = viewModel::retryConnection,
        onOpen = { row -> onOpenMessage(row.conversationId, row.messageId, row.threadRootId) },
        onRemove = { row -> viewModel.remove(row.messageId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksContent(
    state: BookmarksUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (BookmarkFilter) -> Unit,
    onRefresh: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpen: (BookmarkRow) -> Unit,
    onRemove: (BookmarkRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            WaiveraTopBar(title = stringResource(R.string.feature_profile_bookmarks_title), onBack = onBack)
            ConnectionBanner(
                state = bannerFor(state.connection, showsLoadError = state.phase == ListPhase.Error),
                onRetry = onRetryConnection,
                offlineMessage = stringResource(R.string.feature_profile_bookmarks_offline_saved),
            )
            ProfileSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.feature_profile_bookmarks_search),
                clearDescription = stringResource(R.string.feature_profile_bookmarks_search_clear),
            )
            // Nothing to narrow until something is saved; a chip still on stays visible to turn off.
            // The row arrives with the first load, outside the load swap: it opens on the layout
            // token, so the list below is pushed down as the skeleton fades instead of being shoved
            // about 40dp in one frame (M2-08). Already showing on first composition, it is simply there.
            WaiveraExpandVisibility(visible = state.totalCount > 0 || state.filter != BookmarkFilter.All) {
                BookmarkFilterChips(selected = state.filter, onSelect = onFilterChange)
            }
            if (showsRefreshFailed(state.connection, state.error != null && state.phase == ListPhase.Content)) {
                WaiveraBanner(
                    message = stringResource(R.string.feature_profile_bookmarks_refresh_failed),
                    tone = BannerTone.Warning,
                    actionLabel = stringResource(R.string.feature_profile_try_again),
                    onAction = onRefresh,
                    actionIcon = WaiveraIcons.Refresh,
                )
            }
            WaiveraPullToRefreshBox(
                isRefreshing = state.isRefreshing && state.phase != ListPhase.Loading,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                // What loads settles in over the skeleton instead of cutting (M1-16).
                WaiveraLoadSwap(
                    loading = state.phase == ListPhase.Loading,
                    modifier = Modifier.fillMaxSize(),
                ) { loading ->
                    when {
                        loading -> if (rememberDelayedSkeleton(loading = true)) BookmarksSkeleton()
                        // The raw error is never shown here (spec 06 §B3); the copy is fixed.
                        state.phase == ListPhase.Error -> WaiveraErrorState(
                            title = stringResource(R.string.feature_profile_bookmarks_error_title),
                            message = stringResource(R.string.feature_profile_bookmarks_error_message),
                            onRetry = onRefresh,
                            modifier = Modifier.padding(top = WaiveraSpacing.Large),
                        )
                        state.phase == ListPhase.Empty -> BookmarksEmpty(
                            reason = state.emptyReason,
                            filter = state.filter,
                            onClearSearch = { onQueryChange("") },
                            onShowAll = { onFilterChange(BookmarkFilter.All) },
                        )
                        else -> BookmarksList(state, onOpen, onRemove)
                    }
                }
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues()),
        )
    }
}

/** Single choice: All, Messages or Threads. Scrolls sideways at 200% instead of wrapping a label. */
@Composable
private fun BookmarkFilterChips(selected: BookmarkFilter, onSelect: (BookmarkFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = WaiveraSpacing.Gutter)
            .padding(bottom = WaiveraSpacing.Small)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookmarkFilter.entries.forEach { option ->
            WaiveraFilterChip(
                label = stringResource(option.labelRes()),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

private fun BookmarkFilter.labelRes(): Int = when (this) {
    BookmarkFilter.All -> R.string.feature_profile_bookmarks_filter_all
    BookmarkFilter.Messages -> R.string.feature_profile_bookmarks_filter_messages
    BookmarkFilter.Threads -> R.string.feature_profile_bookmarks_filter_threads
}

@Composable
private fun BookmarksEmpty(
    reason: BookmarksEmptyReason,
    filter: BookmarkFilter,
    onClearSearch: () -> Unit,
    onShowAll: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            val modifier = Modifier.padding(top = WaiveraSpacing.Large)
            when (reason) {
                BookmarksEmptyReason.NoMatch -> WaiveraEmptyState(
                    title = stringResource(R.string.feature_profile_bookmarks_no_match_title),
                    message = stringResource(R.string.feature_profile_bookmarks_no_match_message),
                    illustration = WaiveraIllustration.Search,
                    actionLabel = stringResource(R.string.feature_profile_clear_search),
                    onAction = onClearSearch,
                    modifier = modifier,
                )
                BookmarksEmptyReason.NoneInFilter -> {
                    val threads = filter == BookmarkFilter.Threads
                    WaiveraEmptyState(
                        title = stringResource(
                            if (threads) {
                                R.string.feature_profile_bookmarks_empty_threads_title
                            } else {
                                R.string.feature_profile_bookmarks_empty_messages_title
                            },
                        ),
                        message = stringResource(
                            if (threads) {
                                R.string.feature_profile_bookmarks_empty_threads_message
                            } else {
                                R.string.feature_profile_bookmarks_empty_messages_message
                            },
                        ),
                        illustration = WaiveraIllustration.Bookmarks,
                        actionLabel = stringResource(R.string.feature_profile_bookmarks_show_all),
                        onAction = onShowAll,
                        modifier = modifier,
                    )
                }
                BookmarksEmptyReason.NoBookmarks -> WaiveraEmptyState(
                    title = stringResource(R.string.feature_profile_bookmarks_empty_title),
                    message = stringResource(R.string.feature_profile_bookmarks_empty_message),
                    illustration = WaiveraIllustration.Bookmarks,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun BookmarksList(state: BookmarksUiState, onOpen: (BookmarkRow) -> Unit, onRemove: (BookmarkRow) -> Unit) {
    val motion = WaiveraTheme.motion
    val listState = rememberLazyListState()
    // A card composed after the list's first frame, while it is not scrolling, joined it: an undo.
    val listShown = remember(listState) { ListShown(listState) }
    LaunchedEffect(listShown) {
        // The first frame's layout composes the cards already there; anything after joined.
        withFrameNanos { }
        listShown.shown = true
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WaiveraSpacing.Gutter,
            end = WaiveraSpacing.Gutter,
            bottom = bottom + WaiveraSpacing.Medium,
        ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        item(key = "meta") {
            val text = if (!state.isFiltered) {
                pluralStringResource(R.plurals.feature_profile_bookmarks_count, state.totalCount, state.totalCount)
            } else {
                pluralStringResource(
                    R.plurals.feature_profile_bookmarks_count_filtered,
                    state.totalCount,
                    state.rows.size,
                    state.totalCount,
                )
            }
            // The count rolls as a bookmark goes: down when it falls, up when it grows, masked by
            // its line so the old count never slides through the gutter (M1-14, M2-07).
            WaiveraSwap(
                targetState = CountLine(count = state.rows.size, text = text),
                contentKey = { it.text },
                direction = { from, to -> to.count.compareTo(from.count) },
                axis = SwapAxis.Vertical,
            ) { line ->
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = WaiveraSpacing.ExtraSmall),
                )
            }
        }
        items(state.rows, key = { it.messageId }) { row ->
            // A removed bookmark is gone before the rest close up through its place on the
            // shared springs, so no card ever slides over a fading one (M1-14). A card that comes
            // back (an undo) draws above its neighbours while they slide down out of its place,
            // so it is never hidden under the card still moving through it (M3-06).
            val returning = rememberReturningCard(listShown)
            BookmarkCard(
                row = row,
                onOpen = { onOpen(row) },
                onRemove = { onRemove(row) },
                modifier = Modifier
                    .zIndex(if (returning) RETURNING_Z_INDEX else 0f)
                    .animateItem(
                        fadeInSpec = motion.state(),
                        placementSpec = motion.layout(),
                        fadeOutSpec = motion.exit(),
                    ),
            )
        }
    }
}

/** Whether the bookmark list has shown a frame yet, and so whether a new card joined it. */
private class ListShown(private val listState: LazyListState) {
    var shown: Boolean = false

    /** Read once, when a card first composes, without following the scroll afterwards. */
    fun cardJoins(): Boolean = shown && !Snapshot.withoutReadObservation { listState.isScrollInProgress }
}

/**
 * True while a card that joined an already showing list settles: long enough for the cards it
 * pushes down to finish sliding on the layout spring. One recomposition when it ends, once per
 * returning card; cards there from the start, or scrolled into view, never raise. Reduced motion moves nothing, so
 * nothing needs raising.
 */
@Composable
private fun rememberReturningCard(listShown: ListShown): Boolean {
    val motion = WaiveraTheme.motion
    var raised by remember { mutableStateOf(!motion.reduced && listShown.cardJoins()) }
    if (raised) {
        LaunchedEffect(Unit) {
            delay(RETURNING_RAISE_MILLIS)
            raised = false
        }
    }
    return raised
}

private const val RETURNING_Z_INDEX = 1f

/** About as long as the layout spring takes to settle a card's slide. */
private const val RETURNING_RAISE_MILLIS = 400L

/** The list's count line: [count] says which way it moved, [text] is what it reads. */
@Immutable
private data class CountLine(val count: Int, val text: String)

@Composable
private fun BookmarkCard(row: BookmarkRow, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val author = row.author.ifBlank { stringResource(R.string.feature_profile_bookmarks_unknown_author) }
    val removing = rememberRemovalBeat(key = row.messageId, onRemove = onRemove)
    WaiveraPressable(
        depth = PressDepth.Card,
        onClick = onOpen,
        // Concept A's sheet card: soft lift instead of a hairline, as on Today and Schedule.
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.surface, WaiveraShapes.SheetCard),
        onClickLabel = stringResource(R.string.feature_profile_bookmarks_open, author),
        shape = WaiveraShapes.SheetCard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.Top,
        ) {
            // Every row here is saved, so the row leads with who wrote it, like other message lists.
            WaiveraAvatar(name = author, imageModel = AvatarModel.of(row.authorId), contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            ) {
                Text(text = author, style = MaterialTheme.typography.titleSmall, color = colors.text)
                row.date?.let { date ->
                    Text(
                        text = bookmarkDateText(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
                Text(
                    text = previewText(row.preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    row.isThreadReply ->
                        WaiveraStatusBadge(text = stringResource(R.string.feature_profile_bookmarks_thread_reply))
                    row.isThreadRoot ->
                        WaiveraStatusBadge(text = stringResource(R.string.feature_profile_bookmarks_thread_root))
                }
            }
            // The glyph answers on its own control first: it pops to the plain, unsaved bookmark
            // (the icon button pops any new glyph on the pop token), and the card leaves a beat
            // later (M1-14, M2-07).
            WaiveraIconButton(
                icon = if (removing.started) WaiveraIcons.Bookmark else R.drawable.feature_profile_ic_bookmark_remove,
                contentDescription = stringResource(R.string.feature_profile_bookmarks_remove, author),
                onClick = removing::start,
                tint = if (removing.started) colors.textSecondary else colors.accent,
            )
        }
    }
}

/** A removal that shows on its control first, then asks for the removal. */
@Stable
private class RemovalBeat {
    var started by mutableStateOf(false)
        private set

    fun start() {
        started = true
    }

    fun reset() {
        started = false
    }
}

/**
 * The bookmark removal's beat, kept in UI state so the view model's optimistic removal is
 * unchanged: [RemovalBeat.start] changes the glyph at once and calls [onRemove] after
 * [WaiveraMotionScheme.REMOVAL_BEAT_MILLIS] (at once with animations off). If the card leaves
 * first (the screen closes), the removal is still asked for, so a tap is never lost. If the card
 * is still here [WaiveraMotionScheme.REMOVAL_HELD_MILLIS] after that, the removal did not hold
 * (it failed, or was undone at once) and the glyph goes back, so it can be tapped again.
 */
@Composable
private fun rememberRemovalBeat(key: Any, onRemove: () -> Unit): RemovalBeat {
    val beat = remember(key) { RemovalBeat() }
    val remove by rememberUpdatedState(onRemove)
    val reduced = WaiveraTheme.motion.reduced
    if (beat.started) {
        val scope = rememberCoroutineScope()
        DisposableEffect(beat) {
            var asked = false
            val job = scope.launch {
                if (!reduced) delay(WaiveraMotionScheme.REMOVAL_BEAT_MILLIS)
                asked = true
                remove()
                delay(WaiveraMotionScheme.REMOVAL_HELD_MILLIS)
                beat.reset()
            }
            onDispose {
                job.cancel()
                if (!asked) remove()
            }
        }
    }
    return beat
}

@Composable
private fun previewText(preview: BookmarkPreview): String = when (preview) {
    is BookmarkPreview.Text -> preview.text
    BookmarkPreview.Photo -> stringResource(R.string.feature_profile_bookmarks_photo)
    is BookmarkPreview.File -> preview.name?.let { stringResource(R.string.feature_profile_bookmarks_file_named, it) }
        ?: stringResource(R.string.feature_profile_bookmarks_file)
    BookmarkPreview.Saved -> stringResource(R.string.feature_profile_bookmarks_saved_message)
}

@Composable
private fun bookmarkDateText(date: BookmarkDate): String = when (date) {
    is BookmarkDate.Today -> ProfileFormat.clock(date.at)
    BookmarkDate.Yesterday -> stringResource(R.string.feature_profile_bookmarks_yesterday)
    is BookmarkDate.OnDate -> if (date.showYear) ProfileFormat.date(date.date) else ProfileFormat.birthday(date.date)
}

private const val PREVIEW_LINES = 2

@WaiveraPreviews
@Composable
private fun BookmarksScreenPreview() {
    val now = Instant.parse("2026-11-01T16:30:00Z")
    WaiveraTheme {
        BookmarksContent(
            state = BookmarksUiState(
                rows = BookmarkRules.dated(
                    listOf(
                        BookmarkRow(
                            messageId = 1,
                            conversationId = 7,
                            threadRootId = null,
                            author = "Priya Raman",
                            createdAt = now.minusSeconds(3_600),
                            preview = BookmarkPreview.Text("Shift notes are in the log."),
                        ),
                        BookmarkRow(
                            messageId = 2,
                            conversationId = 7,
                            threadRootId = 9330,
                            author = "Jordan Lee",
                            createdAt = now.minusSeconds(86_400),
                            preview = BookmarkPreview.Photo,
                        ),
                    ),
                    now,
                    WaiveraTime.FallbackZone,
                ),
                totalCount = 2,
                phase = ListPhase.Content,
                now = now,
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onQueryChange = {},
            onFilterChange = {},
            onRefresh = {},
            onRetryConnection = {},
            onOpen = {},
            onRemove = {},
        )
    }
}
