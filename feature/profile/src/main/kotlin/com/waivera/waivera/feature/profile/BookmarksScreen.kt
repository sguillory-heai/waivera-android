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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.showUndo
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Instant

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
            if (state.totalCount > 0 || state.filter != BookmarkFilter.All) {
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
            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.phase != ListPhase.Loading,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                when (state.phase) {
                    ListPhase.Loading -> if (rememberDelayedSkeleton(loading = true)) BookmarksSkeleton()
                    // The raw error is never shown here (spec 06 §B3); the copy is fixed.
                    ListPhase.Error -> WaiveraErrorState(
                        title = stringResource(R.string.feature_profile_bookmarks_error_title),
                        message = stringResource(R.string.feature_profile_bookmarks_error_message),
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = WaiveraSpacing.Large),
                    )
                    ListPhase.Empty -> BookmarksEmpty(
                        reason = state.emptyReason,
                        filter = state.filter,
                        onClearSearch = { onQueryChange("") },
                        onShowAll = { onFilterChange(BookmarkFilter.All) },
                    )
                    ListPhase.Content -> BookmarksList(state, onOpen, onRemove)
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
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WaiveraSpacing.Gutter,
            end = WaiveraSpacing.Gutter,
            bottom = bottom + WaiveraSpacing.Medium,
        ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        item(key = "meta") {
            Text(
                text = if (!state.isFiltered) {
                    pluralStringResource(R.plurals.feature_profile_bookmarks_count, state.totalCount, state.totalCount)
                } else {
                    pluralStringResource(
                        R.plurals.feature_profile_bookmarks_count_filtered,
                        state.totalCount,
                        state.rows.size,
                        state.totalCount,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = WaiveraSpacing.ExtraSmall),
            )
        }
        items(state.rows, key = { it.messageId }) { row ->
            // A removed bookmark fades and the rest close up on the shared springs.
            BookmarkCard(
                row = row,
                onOpen = { onOpen(row) },
                onRemove = { onRemove(row) },
                modifier = Modifier.animateItem(
                    fadeInSpec = motion.state(),
                    placementSpec = motion.layout(),
                    fadeOutSpec = motion.state(),
                ),
            )
        }
    }
}

@Composable
private fun BookmarkCard(row: BookmarkRow, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val author = row.author.ifBlank { stringResource(R.string.feature_profile_bookmarks_unknown_author) }
    WaiveraPressable(
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
            WaiveraIconButton(
                icon = R.drawable.feature_profile_ic_bookmark_remove,
                contentDescription = stringResource(R.string.feature_profile_bookmarks_remove, author),
                onClick = onRemove,
                tint = colors.accent,
            )
        }
    }
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
