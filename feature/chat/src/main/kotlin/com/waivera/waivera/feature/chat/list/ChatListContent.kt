package com.waivera.waivera.feature.chat.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R

/** Callbacks from the inbox to its owner, grouped so the content stays previewable. */
@Immutable
class ChatListActions(
    val onAccountClick: () -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (ChatListFilter) -> Unit = {},
    val onConversationClick: (Long) -> Unit = {},
    val onShowActions: (Long) -> Unit = {},
    val onJoinCall: (Long) -> Unit = {},
    val onMarkRead: (Long) -> Unit = {},
    val onOpenProfile: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onRetryConnection: () -> Unit = {},
)

/**
 * The inbox, stateless. The header (status pill, banners, search, filter) is the list's first
 * item, so when someone is reading at the top a realtime reorder keeps them at the top, and
 * when they have scrolled down the first visible row stays put: no scroll jump either way.
 * Rows move with the layout motion token. [skeletonDelayMillis] overrides the ~300 ms skeleton
 * delay, for screenshot tests only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListContent(
    state: ChatListUiState,
    avatars: Map<String, ImageBitmap>,
    actions: ChatListActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    skeletonDelayMillis: Long? = null,
) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val myAvatar = state.myUserId?.let { avatars[it] }
    val accountPainter = remember(myAvatar) { myAvatar?.let(::BitmapPainter) }
    val loading = state.content == ChatListContent.Loading
    val showSkeleton = skeletonDelayMillis?.let { rememberSkeletonVisible(loading, delayMillis = it) }
        ?: rememberSkeletonVisible(loading)
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            WaiveraTopBar(
                title = stringResource(R.string.feature_chat_list_title),
                actions = listOf(
                    TopBarAction(
                        icon = WaiveraIcons.Add,
                        contentDescription = stringResource(R.string.feature_chat_list_new_chat),
                        onClick = actions.onNewChat,
                    ),
                ),
                account = rememberTopBarAccount(
                    onClick = actions.onAccountClick,
                    fallbackName = state.accountName.ifEmpty { stringResource(R.string.feature_chat_list_account) },
                    painter = accountPainter,
                ),
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = HEADER_KEY, contentType = HEADER_KEY) { Header(state, actions) }
                    when {
                        showSkeleton -> item(key = SKELETON_KEY, contentType = SKELETON_KEY) { ChatListSkeleton() }
                        state.content is ChatListContent.Error -> item(key = STATE_KEY, contentType = STATE_KEY) {
                            WaiveraErrorState(
                                title = stringResource(R.string.feature_chat_list_error_title),
                                message = stringResource(state.content.messageRes),
                                onRetry = actions.onRetryLoad,
                            )
                        }
                        state.content is ChatListContent.Empty -> item(key = STATE_KEY, contentType = STATE_KEY) {
                            EmptyContent(state.content.reason, actions.onNewChat)
                        }
                        else -> items(state.rows, key = { it.id }, contentType = { ROW_TYPE }) { row ->
                            Column(
                                Modifier.animateItem(
                                    fadeInSpec = motion.state(),
                                    placementSpec = motion.layout(),
                                    fadeOutSpec = motion.state(),
                                ),
                            ) {
                                ConversationRow(
                                    row = row,
                                    avatar = (row.leading as? RowLeading.Direct)?.userId?.let { avatars[it] },
                                    onClick = { actions.onConversationClick(row.id) },
                                    onShowActions = { actions.onShowActions(row.id) },
                                    onJoinCall = { actions.onJoinCall(row.id) },
                                    onMarkRead = { actions.onMarkRead(row.id) },
                                    onOpenProfile = actions.onOpenProfile,
                                )
                                RowDivider()
                            }
                        }
                    }
                }
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WaiveraSpacing.Gutter),
        )
    }
}

@Composable
private fun Header(state: ChatListUiState, actions: ChatListActions) {
    val filters = ChatListFilter.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        ConnectionPill(state.connection)
        ConnectionBanner(
            state = when (state.connection) {
                ConnectionUi.Reconnecting -> ConnectionBannerState.Reconnecting
                ConnectionUi.Offline -> ConnectionBannerState.Offline
                // "Connecting" is the normal start-up state; the pill is enough.
                ConnectionUi.Online, ConnectionUi.Connecting -> ConnectionBannerState.Connected
            },
            onRetry = actions.onRetryConnection,
        )
        state.refreshErrorRes?.let { res ->
            WaiveraBanner(
                message = stringResource(R.string.feature_chat_list_refresh_failed, stringResource(res)),
                tone = BannerTone.Error,
                actionLabel = stringResource(R.string.feature_chat_list_retry),
                onAction = actions.onRetryLoad,
            )
        }
        ChatSearchField(query = state.query, onQueryChange = actions.onQueryChange)
        WaiveraSegmentedControl(
            options = filters.map { stringResource(it.labelRes()) },
            selectedIndex = filters.indexOf(state.filter),
            onSelect = { actions.onFilterChange(filters[it]) },
        )
    }
}

@Composable
private fun EmptyContent(reason: EmptyReason, onNewChat: () -> Unit) {
    val (titleRes, messageRes) = when (reason) {
        EmptyReason.NoMatches ->
            R.string.feature_chat_list_empty_search_title to R.string.feature_chat_list_empty_search
        EmptyReason.AllCaughtUp ->
            R.string.feature_chat_list_empty_unread_title to R.string.feature_chat_list_empty_unread
        EmptyReason.NoChannels ->
            R.string.feature_chat_list_empty_channels_title to R.string.feature_chat_list_empty_channels
        EmptyReason.NoConversations -> R.string.feature_chat_list_empty_title to R.string.feature_chat_list_empty
    }
    WaiveraEmptyState(
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        icon = WaiveraIcons.Chat,
        actionLabel = if (reason == EmptyReason.NoConversations) {
            stringResource(R.string.feature_chat_list_empty_action)
        } else {
            null
        },
        onAction = if (reason == EmptyReason.NoConversations) onNewChat else null,
    )
}

private fun ChatListFilter.labelRes(): Int = when (this) {
    ChatListFilter.All -> R.string.feature_chat_list_filter_all
    ChatListFilter.Unread -> R.string.feature_chat_list_filter_unread
    ChatListFilter.Channels -> R.string.feature_chat_list_filter_channels
}

private const val HEADER_KEY = "header"
private const val SKELETON_KEY = "skeleton"
private const val STATE_KEY = "state"
private const val ROW_TYPE = "row"

@WaiveraPreviews
@Composable
private fun ChatListContentPreview() {
    WaiveraTheme {
        ChatListContent(state = ChatListPreviewData.state, avatars = emptyMap(), actions = ChatListActions())
    }
}

@WaiveraPreviews
@Composable
private fun ChatListEmptyPreview() {
    WaiveraTheme {
        ChatListContent(
            state = ChatListUiState(
                content = ChatListContent.Empty(EmptyReason.AllCaughtUp),
                filter = ChatListFilter.Unread,
                connection = ConnectionUi.Reconnecting,
            ),
            avatars = emptyMap(),
            actions = ChatListActions(),
        )
    }
}
