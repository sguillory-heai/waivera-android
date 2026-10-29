package com.waivera.waivera.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.message.ChatDimens
import com.waivera.waivera.feature.chat.message.ChatEffectsHandler
import com.waivera.waivera.feature.chat.message.ChatPreviewData
import com.waivera.waivera.feature.chat.message.ChatSheetCallbacks
import com.waivera.waivera.feature.chat.message.ChatSheetHost
import com.waivera.waivera.feature.chat.message.Composer
import com.waivera.waivera.feature.chat.message.ComposerConfig
import com.waivera.waivera.feature.chat.message.DelayedListSkeleton
import com.waivera.waivera.feature.chat.message.HideKeyboardOnDrag
import com.waivera.waivera.feature.chat.message.LocalChatImages
import com.waivera.waivera.feature.chat.message.LocalMessageFocusAnchors
import com.waivera.waivera.feature.chat.message.MessageFocusAnchors
import com.waivera.waivera.feature.chat.message.MessageRowCallbacks
import com.waivera.waivera.feature.chat.message.ReadOnlyNotice
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.SendErrorSignal
import com.waivera.waivera.feature.chat.message.SendErrorUi
import com.waivera.waivera.feature.chat.message.TimelineContext
import com.waivera.waivera.feature.chat.message.TimelineItem
import com.waivera.waivera.feature.chat.message.TimelineItemView
import com.waivera.waivera.feature.chat.message.rememberChatImageEnvironment
import com.waivera.waivera.feature.chat.message.rememberSendErrorUi
import com.waivera.waivera.feature.chat.thread.ThreadContent
import com.waivera.waivera.feature.chat.thread.ThreadUiState
import com.waivera.waivera.feature.chat.thread.ThreadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Entry point for the thread screen. Owned by `:feature:chat`; routed by `:app`. */
@Composable
fun ThreadScreen(
    conversationId: Long,
    rootMessageId: Long,
    highlightMessageId: Long?,
    onBack: () -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(key = "thread-$conversationId-$rootMessageId") {
            it.create(conversationId, rootMessageId)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val entries = remember(state.root, state.replies, state.content, state.replyCount) { threadEntries(state) }
    val time by produceState(viewModel.rowTime(), viewModel) {
        while (true) {
            delay(ChatDimens.TIME_TICK_MILLIS)
            value = viewModel.rowTime()
        }
    }
    val saveFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(THREAD_SAVE_MIME_TYPE)) { uri ->
            viewModel.onSaveLocationPicked(uri?.toString())
        }

    LaunchedEffect(viewModel, highlightMessageId) { viewModel.highlight(highlightMessageId) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }
    ChatEffectsHandler(
        effects = viewModel.effects,
        snackbarHostState = snackbarHostState,
        listState = listState,
        onSetDraft = { draft = it },
        onOpenThread = {},
        onPickSaveLocation = { name -> saveFile.launch(name) },
        onUndoBookmark = viewModel::undoBookmark,
        onFocusComposer = { runCatching { focusRequester.requestFocus() } },
        // The reply lands in the list a frame after the send returns; scroll once it is laid out.
        scrollToLatest = {
            withFrameNanos { }
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.animateScrollToItem(last)
        },
        indexOfKey = { key -> entries.indexOfFirst { it.key == key }.takeIf { it >= 0 } },
    )

    val rowCallbacks = remember(viewModel, onOpenPerson) {
        MessageRowCallbacks(
            onToggleTimestamp = viewModel::toggleTimestamp,
            onLongPress = viewModel::openActions,
            onReact = viewModel::react,
            onReactionDetails = viewModel::openReactionDetails,
            // Already in the thread: the root's reply chip has nowhere else to go.
            onOpenThread = null,
            onOpenAttachment = viewModel::openAttachment,
            onOpenPerson = onOpenPerson,
            onLink = viewModel::openLink,
            onRetry = viewModel::retry,
            onEditFailed = viewModel::editFailed,
            onDiscard = viewModel::discard,
        )
    }
    val sheetCallbacks = remember(viewModel) {
        ChatSheetCallbacks(
            onDismiss = viewModel::dismissSheet,
            onReact = viewModel::react,
            onCopy = viewModel::copy,
            onReply = { viewModel.replyFromSheet() },
            onEdit = viewModel::startEdit,
            onBookmark = viewModel::toggleBookmark,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onSave = viewModel::saveAttachment,
            onShare = viewModel::shareAttachment,
            onLink = viewModel::openLink,
        )
    }
    val actions = remember(viewModel, onBack) {
        ThreadActions(
            onBack = onBack,
            onRetryLoad = viewModel::retryLoad,
            onRetryConnection = viewModel::retryConnection,
            onSend = viewModel::send,
            onCancelEdit = viewModel::cancelEdit,
            onDismissSendError = viewModel::dismissSendError,
            rows = rowCallbacks,
            sheets = sheetCallbacks,
        )
    }

    CompositionLocalProvider(LocalChatImages provides rememberChatImageEnvironment()) {
        ThreadScreenContent(
            state = state,
            draft = draft,
            onDraftChange = {
                draft = it
                viewModel.onDraftChanged()
            },
            time = time,
            actions = actions,
            messageFor = viewModel::message,
            listState = listState,
            snackbarHostState = snackbarHostState,
            focusRequester = focusRequester,
            scrollToNewestOnOpen = highlightMessageId == null,
            modifier = modifier,
        )
    }
}

private const val THREAD_SAVE_MIME_TYPE = "application/octet-stream"
internal const val THREAD_SUBTITLE_LINES = 1

@Immutable
class ThreadActions(
    val onBack: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onRetryConnection: () -> Unit,
    val onSend: (String) -> Unit,
    val onCancelEdit: () -> Unit,
    val onDismissSendError: () -> Unit,
    val rows: MessageRowCallbacks,
    val sheets: ChatSheetCallbacks,
) {
    companion object {
        val None = ThreadActions({}, {}, {}, {}, {}, {}, MessageRowCallbacks.None, ChatSheetCallbacks.None)
    }
}

/** One row of the thread list: the root, the replies header, notices, or a timeline item. */
@Immutable
internal sealed interface ThreadEntry {
    val key: String

    data class Item(val item: TimelineItem) : ThreadEntry {
        override val key: String get() = item.key
    }

    data class Header(val count: Int) : ThreadEntry {
        override val key: String get() = "replies-header"
    }

    data object RootMissing : ThreadEntry {
        override val key: String get() = "root-missing"
    }

    data object NoReplies : ThreadEntry {
        override val key: String get() = "no-replies"
    }
}

internal fun threadEntries(state: ThreadUiState): List<ThreadEntry> {
    if (state.content != ThreadContent.Ready) return emptyList()
    return buildList {
        add(state.root?.let { ThreadEntry.Item(it) } ?: ThreadEntry.RootMissing)
        // "0 replies" over "No replies yet" says it twice; the empty state alone says it.
        if (state.replies.isEmpty()) add(ThreadEntry.NoReplies) else add(ThreadEntry.Header(state.replyCount))
        state.replies.forEach { add(ThreadEntry.Item(it)) }
    }
}

/**
 * The thread without its ViewModel: oldest first, not reversed (04 §1). Insets as in the
 * conversation: the composer area alone pads the IME and navigation bar.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
fun ThreadScreenContent(
    state: ThreadUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    time: RowTime,
    actions: ThreadActions,
    modifier: Modifier = Modifier,
    messageFor: (Long) -> Message? = { null },
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    focusRequester: FocusRequester? = null,
    scrollToNewestOnOpen: Boolean = true,
    skeletonDelayMillis: Long? = null,
) {
    val colors = WaiveraTheme.colors
    val entries = remember(state.root, state.replies, state.content, state.replyCount) { threadEntries(state) }
    val failedIndices = remember(entries) {
        entries.indices.reversed().filter { index ->
            (entries[index] as? ThreadEntry.Item)?.item?.let(SendErrorUi::isFailedRow) == true
        }
    }
    val sendErrorUi = rememberSendErrorUi(
        sendError = state.sendError,
        failedIndices = failedIndices,
        listState = listState,
        connectionBannerShowing = state.connection.banner != ConnectionBannerState.Connected,
    )
    val scope = rememberCoroutineScope()
    val sendError = SendErrorState(sendErrorUi) { index -> scope.launch { listState.animateScrollToItem(index) } }
    val focusAnchors = remember { MessageFocusAnchors() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            WaiveraTopBar(
                title = stringResource(R.string.feature_chat_thread_screen_title),
                // Where the thread lives; the connection banner is the one connection signal.
                subtitle = state.conversationTitle?.let { stringResource(R.string.feature_chat_thread_in, it) },
                // One line under the one-word title keeps the header at two lines at 200%; a long
                // channel name ends in "…" and is still read whole.
                subtitleMaxLines = THREAD_SUBTITLE_LINES,
                onBack = actions.onBack,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(
                            WindowInsets.displayCutout,
                        ).only(WindowInsetsSides.Horizontal),
                    ),
            ) {
                val connectionBanner = state.connection.banner
                ConnectionBanner(
                    state = connectionBanner,
                    onRetry = actions.onRetryConnection,
                    offlineMessage = stringResource(R.string.feature_chat_messages_offline_saved),
                )
                val stale = state.isStale && connectionBanner == ConnectionBannerState.Connected
                if (stale) {
                    WaiveraBanner(
                        message = stringResource(R.string.feature_chat_messages_stale),
                        tone = BannerTone.Warning,
                        actionLabel = stringResource(R.string.feature_chat_retry),
                        onAction = actions.onRetryLoad,
                        actionIcon = WaiveraIcons.Refresh,
                    )
                }
                if (stale || connectionBanner != ConnectionBannerState.Connected) WaiveraDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    CompositionLocalProvider(LocalMessageFocusAnchors provides focusAnchors) {
                        ThreadBody(state, time, actions, listState, scrollToNewestOnOpen, skeletonDelayMillis)
                    }
                }
                // Nothing to reply to when the thread is missing or failed to load (UI review UI-18).
                if (state.content != ThreadContent.Invalid && state.content !is ThreadContent.Error) {
                    ThreadComposerArea(state, draft, onDraftChange, actions, focusRequester, sendError)
                }
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(bottom = ChatDimens.ComposerMinHeight),
        )
    }
    ChatSheetHost(
        sheet = state.sheet,
        messageFor = messageFor,
        myUserId = state.myUserId,
        nameFor = reactionNameFor(state.names, state.myUserId),
        callbacks = actions.sheets,
        anchors = focusAnchors,
    )
}

@Composable
private fun ThreadBody(
    state: ThreadUiState,
    time: RowTime,
    actions: ThreadActions,
    listState: LazyListState,
    scrollToNewestOnOpen: Boolean,
    skeletonDelayMillis: Long?,
) {
    when (val content = state.content) {
        ThreadContent.Invalid -> WaiveraEmptyState(
            title = stringResource(R.string.feature_chat_thread_error_title),
            message = stringResource(R.string.feature_chat_thread_invalid),
            illustration = WaiveraIllustration.Search,
        )
        ThreadContent.Loading -> DelayedListSkeleton(
            label = stringResource(R.string.feature_chat_thread_loading),
            delayMillis = skeletonDelayMillis,
        )
        is ThreadContent.Error -> WaiveraErrorState(
            message = stringResource(content.message),
            onRetry = actions.onRetryLoad,
            title = stringResource(R.string.feature_chat_thread_error_title),
        )
        ThreadContent.Ready -> ThreadList(state, time, actions, listState, scrollToNewestOnOpen)
    }
}

@Suppress("LongMethod")
@Composable
private fun ThreadList(
    state: ThreadUiState,
    time: RowTime,
    actions: ThreadActions,
    listState: LazyListState,
    scrollToNewestOnOpen: Boolean,
) {
    val motion = WaiveraTheme.motion
    val entries = remember(state.root, state.replies, state.content, state.replyCount) { threadEntries(state) }
    val context = remember(time, state.names, state.conversationTitle, state.visibleTimestamps, state.highlightKey) {
        TimelineContext(time, state.names, state.conversationTitle, state.visibleTimestamps, state.highlightKey)
    }
    val stickPx = with(LocalDensity.current) { ChatDimens.StickToBottomThreshold.roundToPx() }

    HideKeyboardOnDrag(listState)
    // A reaction from the focus overlay grows its row in one frame; the rows below follow it
    // exactly instead of chasing it with the placement spring (wave 30 O4).
    val holdsPlacement = rememberFocusCloseHold(state.sheet, entries)

    // Opening lands on the newest reply (04 §8) unless a highlight will scroll elsewhere.
    var openedAtEnd by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isNotEmpty() && !openedAtEnd) {
            if (scrollToNewestOnOpen) listState.scrollToItem(entries.lastIndex)
            openedAtEnd = true
        }
    }
    // Whether the person was at the bottom before the newest change; a layout pass that only
    // grew the list keeps the previous answer, so a new reply can still pin the view.
    var wasAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var lastCount = -1
        snapshotFlow { listState.layoutInfo.totalItemsCount to isAtBottom(listState.layoutInfo, stickPx) }
            .collect { (count, atBottom) ->
                if (count == lastCount || lastCount < 0) wasAtBottom = atBottom
                lastCount = count
            }
    }
    val lastKey = entries.lastOrNull()?.key
    LaunchedEffect(lastKey) {
        if (openedAtEnd && wasAtBottom && entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    val listLabel = stringResource(R.string.feature_chat_thread_list)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = listLabel },
    ) {
        items(items = entries, key = { it.key }, contentType = { it::class }) { entry ->
            val itemModifier = Modifier.animateItem(
                fadeInSpec = null,
                placementSpec = if (holdsPlacement) null else motion.layout(),
                fadeOutSpec = motion.state(),
            )
            when (entry) {
                is ThreadEntry.Item -> TimelineItemView(entry.item, context, actions.rows, itemModifier)
                is ThreadEntry.Header -> RepliesHeader(entry.count, itemModifier)
                ThreadEntry.RootMissing -> RootMissingBubble(itemModifier)
                ThreadEntry.NoReplies -> WaiveraEmptyState(
                    title = stringResource(R.string.feature_chat_thread_empty),
                    message = stringResource(R.string.feature_chat_thread_empty_message),
                    illustration = WaiveraIllustration.Chats,
                    modifier = itemModifier,
                )
            }
        }
    }
}

private fun isAtBottom(info: LazyListLayoutInfo, thresholdPx: Int): Boolean {
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    if (last.index < info.totalItemsCount - 1) return false
    return last.offset + last.size - info.viewportEndOffset <= thresholdPx
}

@Composable
private fun RepliesHeader(count: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        WaiveraDivider()
        Text(
            text = pluralStringResource(R.plurals.feature_chat_thread_reply_count_header, count, count),
            style = WaiveraTextStyles.Eyebrow,
            color = WaiveraTheme.colors.textSecondary,
            modifier = Modifier
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
                .semantics { heading() },
        )
    }
}

/** A muted stand-in bubble where the thread's original message would be. */
@Composable
private fun RootMissingBubble(modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Text(
        text = stringResource(R.string.feature_chat_thread_root_missing),
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = colors.textSecondary,
        modifier = modifier
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
            .background(colors.surfaceMuted, WaiveraShapes.Bubble)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Bubble)
            .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
    )
}

@Composable
private fun ThreadComposerArea(
    state: ThreadUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    actions: ThreadActions,
    focusRequester: FocusRequester?,
    sendError: SendErrorState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaiveraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
            .padding(top = WaiveraSpacing.ExtraSmall),
    ) {
        val reason = state.readOnlyReason
        if (reason != null) {
            ReadOnlyNotice(reason = reason, isThread = true)
        } else {
            SendErrorSignal(
                ui = sendError.ui,
                isReply = true,
                onShow = sendError.onShow,
                onDismiss = actions.onDismissSendError,
            )
            Composer(
                draft = draft,
                onDraftChange = onDraftChange,
                config = ComposerConfig(
                    isReply = true,
                    allowImages = false,
                    enabled = state.canWrite == true,
                    isEditing = state.editingMessageId != null,
                ),
                onSend = { actions.onSend(draft) },
                onAttach = {},
                onRemoveImage = {},
                onCancelEdit = actions.onCancelEdit,
                focusRequester = focusRequester,
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun ThreadScreenPreview() {
    val items = ChatPreviewData.items()
    WaiveraTheme {
        ThreadScreenContent(
            state = ThreadUiState(
                content = ThreadContent.Ready,
                root = items.filterIsInstance<TimelineItem.MessageRow>().first(),
                replies = items.drop(2),
                replyCount = 2,
                names = ChatPreviewData.names,
                conversationTitle = "Priya Shah",
                canWrite = true,
                myUserId = ChatPreviewData.ME,
            ),
            draft = "",
            onDraftChange = {},
            time = ChatPreviewData.time,
            actions = ThreadActions.None,
        )
    }
}
