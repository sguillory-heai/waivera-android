package com.waivera.waivera.feature.chat.thread

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessageThread
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.designsystem.feedback.HapticMoment
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.conversation.ConversationViewModel
import com.waivera.waivera.feature.chat.message.ArrivalTracker
import com.waivera.waivera.feature.chat.message.AttachmentDownloads
import com.waivera.waivera.feature.chat.message.AttachmentIo
import com.waivera.waivera.feature.chat.message.ChatActions
import com.waivera.waivera.feature.chat.message.ChatClock
import com.waivera.waivera.feature.chat.message.ChatComputeDispatcher
import com.waivera.waivera.feature.chat.message.ChatEffect
import com.waivera.waivera.feature.chat.message.ChatSheet
import com.waivera.waivera.feature.chat.message.ConnectionUi
import com.waivera.waivera.feature.chat.message.MessageInteractions
import com.waivera.waivera.feature.chat.message.ReadOnlyReason
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.SendErrorKind
import com.waivera.waivera.feature.chat.message.SendErrors
import com.waivera.waivera.feature.chat.message.SendTracker
import com.waivera.waivera.feature.chat.message.TimelineBuilder
import com.waivera.waivera.feature.chat.message.TimelineItem
import com.waivera.waivera.feature.chat.message.createOutgoingMessage
import com.waivera.waivera.feature.chat.message.plainText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
sealed interface ThreadContent {
    /** The route's root id can never be a message (04 §8: rejected locally). */
    data object Invalid : ThreadContent

    data object Loading : ThreadContent

    data class Error(@StringRes val message: Int) : ThreadContent

    /** Loaded; [ThreadUiState.root] is null when the original message is gone. */
    data object Ready : ThreadContent
}

@Immutable
data class ThreadUiState(
    val connection: ConnectionUi = ConnectionUi.Connected,
    val content: ThreadContent = ThreadContent.Loading,
    val root: TimelineItem.MessageRow? = null,
    /** Replies oldest first, with day separators. */
    val replies: List<TimelineItem> = emptyList(),
    val replyCount: Int = 0,
    val isStale: Boolean = false,
    val highlightKey: String? = null,
    val visibleTimestamps: Set<String> = emptySet(),
    val names: Map<String, String> = emptyMap(),
    val conversationTitle: String? = null,
    /** The name inside a group's composed [conversationTitle], the only part the header may cut. */
    val conversationTitleNameRange: IntRange? = null,
    val canWrite: Boolean? = null,
    val readOnlyReason: ReadOnlyReason? = null,
    val sendError: SendErrorKind? = null,
    val editingMessageId: Long? = null,
    val sheet: ChatSheet? = null,
    val myUserId: String? = null,
)

/**
 * A thread (04): the root message, its replies oldest first and a text-only reply composer.
 * Unlike the reference app it wires the full action sheet and reactions, and it joins the
 * conversation's hub group itself (collecting the thread does), so a thread opened straight from
 * a notification still receives live replies.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted("conversationId") private val conversationId: Long,
    @Assisted("rootMessageId") private val rootMessageId: Long,
    private val threads: ThreadRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    bookmarks: BookmarkRepository,
    people: PeopleRepository,
    session: SessionRepository,
    private val realtime: RealtimeStatusRepository,
    io: AttachmentIo,
    downloads: AttachmentDownloads,
    private val clock: ChatClock,
    @ChatComputeDispatcher private val computeDispatcher: CoroutineDispatcher,
    private val titles: ConversationTitleText,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("conversationId") conversationId: Long,
            @Assisted("rootMessageId") rootMessageId: Long,
        ): ThreadViewModel
    }

    private val isValid = ConversationViewModel.isValid(conversationId) && rootMessageId > 0

    private val _state =
        MutableStateFlow(ThreadUiState(content = if (isValid) ThreadContent.Loading else ThreadContent.Invalid))
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = _effects.receiveAsFlow()

    private val interactions = MessageInteractions(
        scope = viewModelScope,
        messages = messages,
        bookmarks = bookmarks,
        downloads = downloads,
        io = io,
        isConnected = { realtime.status.value.isConnected },
        emit = ::emit,
    )
    private val sendTracker = SendTracker()
    private val arrivalTracker = ArrivalTracker()
    private val liveArrivals = MutableStateFlow<Set<Long>>(emptySet())

    @Volatile private var allMessages: List<Message> = emptyList()
    private var conversation: Conversation? = null

    private var isResumed = false
    private var lastMarkedRead: Long? = null
    private var markReadJob: Job? = null
    private var highlightJob: Job? = null
    private var requestedHighlight: Long? = null

    init {
        if (isValid) start(bookmarks, people, session)
    }

    private fun start(bookmarks: BookmarkRepository, people: PeopleRepository, session: SessionRepository) {
        val myUserId = session.state.map { it.currentUserId }.distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
        val conversationFlow = conversations.observeConversation(conversationId)
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
        val directory = people.observeDirectory().map { synced -> synced.data.associate { it.id to it.name } }
            .onStart { emit(emptyMap()) }

        viewModelScope.launch {
            realtime.status.collect { status -> _state.update { it.copy(connection = ConnectionUi.from(status)) } }
        }
        viewModelScope.launch {
            if (conversationFlow.first() == null) conversations.refreshConversation(conversationId)
        }
        viewModelScope.launch {
            combine(conversationFlow, myUserId, directory) { c, me, names -> Triple(c, me, names) }
                .collect { (c, me, names) -> onConversation(c, me, names) }
        }
        viewModelScope.launch { interactions.sheet.collect { sheet -> _state.update { it.copy(sheet = sheet) } } }

        val isBot = conversationFlow.map { it?.kind == ConversationKind.BOT }.distinctUntilChanged()
        val prepared = combine(
            threads.observeThread(conversationId, rootMessageId),
            bookmarks.observeBookmarkedIds(),
            myUserId,
            isBot,
        ) { thread, saved, me, bot -> prepare(thread, saved, me, bot) }
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
        viewModelScope.launch {
            combine(prepared, liveArrivals) { input, live -> input to build(input, live) }
                .flowOn(computeDispatcher)
                .collect { (input, built) -> onThread(input, built) }
        }
    }

    private class Prepared(
        val synced: Synced<MessageThread>,
        val root: Message?,
        val replies: List<Message>,
        val myUserId: String?,
        val isBot: Boolean,
    )

    private class Built(val root: TimelineItem.MessageRow?, val replies: List<TimelineItem>)

    private fun prepare(thread: Synced<MessageThread>, saved: Set<Long>, me: String?, bot: Boolean): Prepared {
        fun Message.marked() = if (isBookmarked == (id in saved)) this else copy(isBookmarked = id in saved)
        val root = thread.data.root?.marked()
        val replies = thread.data.replies.map { it.marked() }
        allMessages = listOfNotNull(root) + replies
        val arrivals = arrivalTracker.update(replies)
        if (arrivals.isNotEmpty()) {
            liveArrivals.update { it + arrivals }
            viewModelScope.launch {
                delay(ConversationViewModel.ARRIVAL_WINDOW_MILLIS)
                liveArrivals.update { it - arrivals }
            }
        }
        val events = sendTracker.update(replies)
        if (events.newFailures > 0) {
            val kind = SendErrors.forFailedSend(
                error = replies.lastOrNull { it.sendState == SendState.FAILED }?.sendError,
                isOnline = realtime.status.value.isOnline,
                canWrite = _state.value.canWrite,
                isArchived = conversation?.channel?.isArchived == true,
            )
            _state.update { it.copy(sendError = kind) }
            emit(ChatEffect.Haptic(HapticMoment.SendFailed))
        }
        return Prepared(thread, root, replies, me, bot)
    }

    private fun build(input: Prepared, live: Set<Long>): Built {
        val zone = clock.zone
        val root = input.root?.let { message ->
            TimelineBuilder.build(listOf(message), input.myUserId, zone, input.isBot, daySeparators = false)
                .filterIsInstance<TimelineItem.MessageRow>()
                .firstOrNull()
        }
        val replies = TimelineBuilder.build(input.replies, input.myUserId, zone, input.isBot, liveArrivals = live)
        return Built(root, replies)
    }

    private fun onThread(input: Prepared, built: Built) {
        val synced = input.synced
        val error = synced.error
        val hasData = input.root != null || input.replies.isNotEmpty()
        val content = when {
            hasData -> ThreadContent.Ready
            error != null -> ThreadContent.Error(error.messageRes)
            synced.isInitialLoad -> ThreadContent.Loading
            else -> ThreadContent.Ready
        }
        _state.update {
            it.copy(
                content = content,
                root = built.root,
                replies = built.replies,
                replyCount = input.replies.count { reply -> !reply.isPending },
                isStale = hasData && error != null,
                myUserId = input.myUserId,
            )
        }
        markReadIfNeeded()
    }

    private fun onConversation(value: Conversation?, me: String?, directory: Map<String, String>) {
        conversation = value
        val names = buildMap {
            putAll(directory)
            value?.participants?.forEach { if (it.name.isNotBlank()) put(it.id, it.name) }
        }
        _state.update {
            it.copy(
                names = names,
                conversationTitle = value?.let { c -> ConversationTitles.format(c, me, titles) },
                conversationTitleNameRange = value?.let { c -> ConversationTitles.groupNameRange(c, me, titles) },
                canWrite = value?.let { c -> c.canWrite && c.kind != ConversationKind.BOT },
                readOnlyReason = value?.let(ConversationViewModel::readOnlyReason),
                myUserId = me,
            )
        }
    }

    // region Lifecycle and read state

    fun onResumed() {
        isResumed = true
        markReadIfNeeded()
    }

    fun onPaused() {
        isResumed = false
    }

    /** 04 §4: read-through with the thread root whenever the newest reply changes; retried on failure. */
    private fun markReadIfNeeded() {
        if (!isResumed || markReadJob?.isActive == true) return
        val newest = allMessages.lastOrNull { !it.isPending && it.id > 0 }?.id ?: return
        if (newest == lastMarkedRead) return
        markReadJob = viewModelScope.launch {
            threads.markRead(conversationId, rootMessageId, newest).onSuccess { lastMarkedRead = newest }
        }
    }

    fun retryLoad() {
        viewModelScope.launch {
            conversations.refreshConversation(conversationId)
            threads.refresh(rootMessageId)
        }
    }

    fun retryConnection() = realtime.retryNow()

    // endregion

    // region Highlight

    /**
     * 04 §5: highlight only what the thread response holds; no context fetch. A target that is not
     * there (deleted, or the link was wrong) keeps the thread open with a brief note, so a push
     * about a reply never lands on a thread that silently shows something else (UI2-08).
     */
    fun highlight(messageId: Long?) {
        if (messageId == null || messageId <= 0 || messageId == requestedHighlight) return
        requestedHighlight = messageId
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            val ready = _state.first { it.content != ThreadContent.Loading }
            val key = keyFor(ready, messageId)
            if (key == null) {
                if (ready.content == ThreadContent.Ready) {
                    emit(ChatEffect.Snackbar(R.string.feature_chat_reply_missing))
                }
                return@launch
            }
            delay(HIGHLIGHT_SCROLL_DELAY_MILLIS)
            emit(ChatEffect.ScrollTo(key))
            _state.update { it.copy(highlightKey = key) }
            delay(ConversationViewModel.HIGHLIGHT_MILLIS)
            _state.update { if (it.highlightKey == key) it.copy(highlightKey = null) else it }
        }
    }

    private fun keyFor(state: ThreadUiState, messageId: Long): String? {
        state.root?.takeIf { it.message.id == messageId }?.let { return it.key }
        return state.replies.firstOrNull { item ->
            item is TimelineItem.MessageRow && item.message.id == messageId
        }?.key
    }

    // endregion

    // region Composer

    fun onDraftChanged() {
        // Threads send no typing events (04 §1); editing only clears the error banner.
        if (_state.value.sendError != null) _state.update { it.copy(sendError = null) }
    }

    fun dismissSendError() = _state.update { it.copy(sendError = null) }

    fun send(draft: String) {
        if (!isValid) return
        val current = _state.value
        if (current.canWrite != true) return
        val editing = current.editingMessageId
        if (editing != null) submitEdit(editing, draft) else sendReply(draft)
    }

    private fun sendReply(draft: String) {
        val outgoing = createOutgoingMessage(draft) ?: return
        _state.update { it.copy(sendError = null) }
        emit(ChatEffect.SetDraft(""))
        viewModelScope.launch {
            runCatching {
                messages.send(
                    conversationId,
                    outgoing.text,
                    outgoing.doc,
                    attachment = null,
                    parentMessageId = rootMessageId,
                )
            }
                // Same as the conversation: a sent reply shows "Sent", only a failure buzzes.
                .onSuccess { emit(ChatEffect.ScrollToLatest) }
                .onFailure { error ->
                    val typed = error as? WaiveraError ?: WaiveraError.from(error)
                    _state.update { it.copy(sendError = SendErrors.from(typed)) }
                    // 04 §3: the exact draft comes back.
                    emit(ChatEffect.SetDraft(draft))
                    emit(ChatEffect.Haptic(HapticMoment.SendFailed))
                }
        }
    }

    private fun submitEdit(messageId: Long, draft: String) {
        val outgoing = createOutgoingMessage(draft) ?: return
        viewModelScope.launch {
            messages.edit(messageId, outgoing.text, outgoing.doc)
                .onSuccess {
                    _state.update { it.copy(editingMessageId = null) }
                    // No vibration: saving an edit is a send, and sends that work are silent (UI4-09).
                    emit(ChatEffect.SetDraft(""))
                }
                .onFailure { error ->
                    val typed = error as? WaiveraError ?: WaiveraError.from(error)
                    emit(ChatEffect.Snackbar(R.string.feature_chat_edit_failed, detail = typed.messageRes))
                }
        }
    }

    fun startEdit(message: Message) {
        interactions.dismissSheet()
        _state.update { it.copy(editingMessageId = message.id) }
        emit(ChatEffect.SetDraft(message.plainText()))
    }

    fun cancelEdit() {
        _state.update { it.copy(editingMessageId = null) }
        emit(ChatEffect.SetDraft(""))
    }

    // endregion

    // region Rows and actions

    fun toggleTimestamp(key: String) = _state.update {
        it.copy(
            visibleTimestamps = if (key in
                it.visibleTimestamps
            ) {
                it.visibleTimestamps - key
            } else {
                it.visibleTimestamps + key
            },
        )
    }

    fun openActions(message: Message) {
        if (!ChatActions.canOpen(message)) return
        val current = _state.value
        // Reply here focuses the reply box; it never opens a nested thread.
        val model = ChatActions.model(message, current.myUserId, current.canWrite, allowReply = false)
        interactions.openActions(model.copy(canReply = current.canWrite == true))
    }

    fun replyFromSheet() {
        interactions.dismissSheet()
        emit(ChatEffect.FocusComposer)
    }

    fun dismissSheet() = interactions.dismissSheet()

    fun copy(message: Message) = interactions.copy(message)

    fun toggleBookmark(messageId: Long) = interactions.toggleBookmark(messageId)

    fun undoBookmark(messageId: Long) = interactions.undoBookmark(messageId)

    fun requestDelete(messageId: Long) = interactions.requestDelete(messageId)

    fun confirmDelete() = interactions.confirmDelete()

    fun react(messageId: Long, emoji: String) = interactions.react(messageId, emoji)

    fun openReactionDetails(messageId: Long, emoji: String) = interactions.openReactionDetails(messageId, emoji)

    fun openAttachment(message: Message) = interactions.openAttachment(message)

    fun saveAttachment(message: Message) = interactions.saveAttachment(message)

    fun onSaveLocationPicked(uri: String?) = interactions.onSaveLocationPicked(uri)

    fun shareAttachment(message: Message) = interactions.shareAttachment(message)

    fun openLink(url: String) = interactions.openLink(url)

    fun retry(clientMessageId: String) {
        sendTracker.markRetried(clientMessageId)
        _state.update { it.copy(sendError = null) }
        interactions.retry(clientMessageId)
    }

    fun discard(clientMessageId: String) = interactions.discard(clientMessageId)

    fun editFailed(message: Message) {
        val clientId = message.clientMessageId ?: return
        emit(ChatEffect.SetDraft(message.plainText()))
        interactions.discard(clientId)
    }

    fun message(messageId: Long): Message? = allMessages.firstOrNull { it.id == messageId }

    fun rowTime(): RowTime = RowTime(clock.now(), clock.zone)

    // endregion

    private fun emit(effect: ChatEffect) {
        _effects.trySend(effect)
    }

    companion object {
        const val HIGHLIGHT_SCROLL_DELAY_MILLIS = 120L
    }
}
