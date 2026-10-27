package com.waivera.waivera.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.conversation.CallUi
import com.waivera.waivera.feature.chat.conversation.ConversationContent
import com.waivera.waivera.feature.chat.conversation.ConversationUiState
import com.waivera.waivera.feature.chat.conversation.ConversationViewModel
import com.waivera.waivera.feature.chat.message.CallBanner
import com.waivera.waivera.feature.chat.message.ChatDimens
import com.waivera.waivera.feature.chat.message.ChatEffectsHandler
import com.waivera.waivera.feature.chat.message.ChatIcons
import com.waivera.waivera.feature.chat.message.ChatPreviewData
import com.waivera.waivera.feature.chat.message.ChatSheetCallbacks
import com.waivera.waivera.feature.chat.message.ChatSheetHost
import com.waivera.waivera.feature.chat.message.Composer
import com.waivera.waivera.feature.chat.message.ComposerConfig
import com.waivera.waivera.feature.chat.message.HideKeyboardOnDrag
import com.waivera.waivera.feature.chat.message.JumpToLatestPill
import com.waivera.waivera.feature.chat.message.LocalChatImages
import com.waivera.waivera.feature.chat.message.MessageListSkeleton
import com.waivera.waivera.feature.chat.message.MessageRowCallbacks
import com.waivera.waivera.feature.chat.message.OlderMessagesRow
import com.waivera.waivera.feature.chat.message.ReadOnlyNotice
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.SendErrorBanner
import com.waivera.waivera.feature.chat.message.TimelineContext
import com.waivera.waivera.feature.chat.message.TimelineItemView
import com.waivera.waivera.feature.chat.message.TypingSlot
import com.waivera.waivera.feature.chat.message.rememberChatImageEnvironment
import com.waivera.waivera.feature.chat.message.typingLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Entry point for the conversation screen. Owned by `:feature:chat`; routed by `:app`.
 *
 * [onOpenEvent] stays in the routed contract for meeting chats; the screen has no event link yet.
 */
@Suppress("UnusedParameter")
@Composable
fun ConversationScreen(
    conversationId: Long,
    highlightMessageId: Long?,
    onBack: () -> Unit,
    onOpenThread: (rootMessageId: Long) -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ConversationViewModel, ConversationViewModel.Factory>(key = "conversation-$conversationId") {
        it.create(conversationId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val time by produceState(viewModel.rowTime(), viewModel) {
        while (true) {
            delay(ChatDimens.TIME_TICK_MILLIS)
            value = viewModel.rowTime()
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri?.toString())
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SAVE_MIME_TYPE)) { uri ->
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
        onOpenThread = onOpenThread,
        onPickSaveLocation = { name -> saveFile.launch(name) },
        onUndoBookmark = viewModel::undoBookmark,
        onFocusComposer = {},
        scrollToLatest = { listState.animateScrollToItem(0) },
        // The list is newest first (reverse layout), so the index counts from the newest row.
        indexOfKey = { key -> state.items.indexOfLast { it.key == key }.takeIf { it >= 0 }?.let { state.items.lastIndex - it } },
    )

    val rowCallbacks = remember(viewModel) {
        MessageRowCallbacks(
            onToggleTimestamp = viewModel::toggleTimestamp,
            onLongPress = viewModel::openActions,
            onReact = viewModel::react,
            onReactionDetails = viewModel::openReactionDetails,
            onOpenThread = { root -> viewModel.openThread(root) },
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
            onReply = { message -> viewModel.openThread(message) },
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
        ConversationActions(
            onBack = onBack,
            onRetryLoad = viewModel::retryLoad,
            onRetryConnection = viewModel::retryConnection,
            onLoadOlder = viewModel::loadOlder,
            onOpenCall = viewModel::openCall,
            onSend = viewModel::send,
            onAttach = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveImage = viewModel::removeStagedImage,
            onCancelEdit = viewModel::cancelEdit,
            onDismissSendError = viewModel::dismissSendError,
            rows = rowCallbacks,
            sheets = sheetCallbacks,
        )
    }

    CompositionLocalProvider(LocalChatImages provides rememberChatImageEnvironment()) {
        ConversationScreenContent(
            state = state,
            draft = draft,
            onDraftChange = {
                draft = it
                viewModel.onDraftChanged(it)
            },
            time = time,
            actions = actions,
            messageFor = viewModel::message,
            listState = listState,
            snackbarHostState = snackbarHostState,
            onJumpToLatest = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = modifier,
        )
    }
}

/** Generic type for the "save to" picker; the file name carries the real extension. */
private const val SAVE_MIME_TYPE = "application/octet-stream"

/** Every screen-level action, so [ConversationScreenContent] stays previewable. */
@Immutable
class ConversationActions(
    val onBack: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onRetryConnection: () -> Unit,
    val onLoadOlder: () -> Unit,
    val onOpenCall: () -> Unit,
    val onSend: (String) -> Unit,
    val onAttach: () -> Unit,
    val onRemoveImage: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onDismissSendError: () -> Unit,
    val rows: MessageRowCallbacks,
    val sheets: ChatSheetCallbacks,
) {
    companion object {
        val None = ConversationActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, MessageRowCallbacks.None, ChatSheetCallbacks.None)
    }
}

/**
 * The conversation without its ViewModel. Insets: the top bar pads the status bar; the composer
 * area alone pads the IME and navigation bar (the larger of the two), so nothing compensates
 * twice; the list sits between them and shrinks as the keyboard opens.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
fun ConversationScreenContent(
    state: ConversationUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    time: RowTime,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
    messageFor: (Long) -> Message? = { null },
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onJumpToLatest: () -> Unit = {},
) {
    val colors = WaiveraTheme.colors
    val title = state.title ?: stringResource(R.string.feature_chat_conversation_fallback_title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            WaiveraTopBar(
                title = title,
                subtitle = state.connection.subtitle?.let { stringResource(it) },
                onBack = actions.onBack,
                actions = callActions(state.call, actions.onOpenCall),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)),
            ) {
                if (state.call.isLive) CallBanner(onJoin = actions.onOpenCall)
                ConnectionBanner(state = state.connection.banner, onRetry = actions.onRetryConnection)
                if (state.isStale) {
                    WaiveraBanner(
                        message = stringResource(R.string.feature_chat_messages_stale),
                        tone = BannerTone.Warning,
                        actionLabel = stringResource(R.string.feature_chat_retry),
                        onAction = actions.onRetryLoad,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    MessageArea(state, time, actions, listState, onJumpToLatest)
                }
                ComposerArea(state, draft, onDraftChange, actions)
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(bottom = ChatDimens.ComposerMinHeight + ChatDimens.TypingSlot),
        )
    }
    ChatSheetHost(
        sheet = state.sheet,
        messageFor = messageFor,
        myUserId = state.myUserId,
        nameFor = reactionNameFor(state.names, state.myUserId),
        callbacks = actions.sheets,
    )
}

/** Reaction details list "You" for oneself and "Unknown user" for anyone not in the directory. */
@Composable
internal fun reactionNameFor(names: Map<String, String>, myUserId: String?): (String) -> String {
    val you = stringResource(R.string.feature_chat_you)
    val unknown = stringResource(R.string.feature_chat_unknown_author)
    return remember(names, myUserId, you, unknown) {
        { id -> if (id == myUserId) you else names[id]?.takeIf(String::isNotBlank) ?: unknown }
    }
}

@Composable
private fun callActions(call: CallUi, onOpenCall: () -> Unit): List<TopBarAction> {
    if (!call.available) return emptyList()
    val label = stringResource(if (call.isLive) R.string.feature_chat_call_join else R.string.feature_chat_call_start)
    return listOf(TopBarAction(icon = ChatIcons.Video, contentDescription = label, onClick = onOpenCall))
}

@Composable
private fun MessageArea(
    state: ConversationUiState,
    time: RowTime,
    actions: ConversationActions,
    listState: LazyListState,
    onJumpToLatest: () -> Unit,
) {
    when (val content = state.content) {
        ConversationContent.Invalid -> WaiveraEmptyState(
            title = stringResource(R.string.feature_chat_messages_error_title),
            message = stringResource(R.string.feature_chat_conversation_invalid),
            icon = WaiveraIcons.Error,
        )
        ConversationContent.Loading -> {
            val loading = stringResource(R.string.feature_chat_messages_loading)
            MessageListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = loading }
                    .padding(vertical = WaiveraSpacing.Medium),
            )
        }
        is ConversationContent.Error -> WaiveraErrorState(
            message = stringResource(content.message),
            onRetry = actions.onRetryLoad,
            title = stringResource(R.string.feature_chat_messages_error_title),
        )
        ConversationContent.Empty -> WaiveraEmptyState(
            title = stringResource(R.string.feature_chat_messages_empty_title),
            message = stringResource(R.string.feature_chat_messages_empty),
            icon = WaiveraIcons.Chat,
        )
        ConversationContent.Messages -> MessageList(state, time, actions, listState, onJumpToLatest)
    }
}

/**
 * Newest first with `reverseLayout`, so the list starts at the bottom without a scroll on open
 * and index 0 is the newest row. Keys are stable across the optimistic-to-server swap.
 */
@Suppress("LongMethod")
@Composable
private fun MessageList(
    state: ConversationUiState,
    time: RowTime,
    actions: ConversationActions,
    listState: LazyListState,
    onJumpToLatest: () -> Unit,
) {
    val motion = WaiveraTheme.motion
    val density = LocalDensity.current
    val newestFirst = remember(state.items) { state.items.asReversed() }
    val context = remember(time, state.names, state.title, state.visibleTimestamps, state.highlightKey) {
        TimelineContext(time, state.names, state.title, state.visibleTimestamps, state.highlightKey)
    }
    val jumpThresholdPx = with(density) { ChatDimens.JumpToLatestThreshold.roundToPx() }
    val stickThresholdPx = with(density) { ChatDimens.StickToBottomThreshold.roundToPx() }
    val showJump by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > jumpThresholdPx }
    }

    HideKeyboardOnDrag(listState)
    // A new newest row keeps the list pinned when the person was already at (or next to) it.
    val newestKey = newestFirst.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null && listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset <= stickThresholdPx) {
            listState.animateScrollToItem(0)
        }
    }
    // Ask for the next older page a few rows before the top.
    LaunchedEffect(listState, state.hasOlder) {
        if (!state.hasOlder) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - ChatDimens.OLDER_PREFETCH_ROWS
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { actions.onLoadOlder() }
    }

    val listLabel = stringResource(R.string.feature_chat_messages_list)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = listLabel },
        ) {
            items(items = newestFirst, key = { it.key }, contentType = { it::class }) { item ->
                TimelineItemView(
                    item = item,
                    context = context,
                    callbacks = actions.rows,
                    modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = motion.layout(), fadeOutSpec = motion.state()),
                )
            }
            if (state.hasOlder || state.olderFailed) {
                item(key = OLDER_KEY, contentType = OLDER_KEY) {
                    OlderMessagesRow(failed = state.olderFailed, onRetry = actions.onLoadOlder)
                }
            }
        }
        if (showJump) {
            JumpToLatestPill(
                onClick = onJumpToLatest,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ChatDimens.JumpPillBottomGap),
            )
        }
    }
}

private const val OLDER_KEY = "older"

@Composable
private fun ComposerArea(
    state: ConversationUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    actions: ConversationActions,
) {
    val unknown = stringResource(R.string.feature_chat_unknown_author)
    val typingNames = remember(state.typingNames, unknown) { state.typingNames.map { it.ifBlank { unknown } } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaiveraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
    ) {
        TypingSlot(label = typingLabel(typingNames))
        val reason = state.readOnlyReason
        when {
            reason != null -> ReadOnlyNotice(reason = reason, isThread = false)
            else -> {
                state.sendError?.let { kind ->
                    SendErrorBanner(kind = kind, isReply = false, onDismiss = actions.onDismissSendError)
                }
                Composer(
                    draft = draft,
                    onDraftChange = onDraftChange,
                    config = ComposerConfig(
                        isReply = false,
                        allowImages = state.editingMessageId == null,
                        enabled = state.canWrite == true,
                        stagedImage = state.stagedImage,
                        isEditing = state.editingMessageId != null,
                    ),
                    onSend = { actions.onSend(draft) },
                    onAttach = actions.onAttach,
                    onRemoveImage = actions.onRemoveImage,
                    onCancelEdit = actions.onCancelEdit,
                )
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun ConversationScreenPreview() {
    WaiveraTheme {
        ConversationScreenContent(
            state = ConversationUiState(
                title = "Priya Shah",
                content = ConversationContent.Messages,
                items = ChatPreviewData.items(),
                names = ChatPreviewData.names,
                typingNames = listOf("Priya Shah"),
                canWrite = true,
                myUserId = ChatPreviewData.ME,
                call = CallUi(available = true, isLive = true),
            ),
            draft = "",
            onDraftChange = {},
            time = ChatPreviewData.time,
            actions = ConversationActions.None,
        )
    }
}
