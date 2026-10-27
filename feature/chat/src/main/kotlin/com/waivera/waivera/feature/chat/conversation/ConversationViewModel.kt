package com.waivera.waivera.feature.chat.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessageTimeline
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.designsystem.feedback.HapticMoment
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.message.ArrivalTracker
import com.waivera.waivera.feature.chat.message.AttachmentDownloads
import com.waivera.waivera.feature.chat.message.AttachmentIo
import com.waivera.waivera.feature.chat.message.ChatActions
import com.waivera.waivera.feature.chat.message.ChatClock
import com.waivera.waivera.feature.chat.message.ChatComputeDispatcher
import com.waivera.waivera.feature.chat.message.ChatEffect
import com.waivera.waivera.feature.chat.message.ConnectionUi
import com.waivera.waivera.feature.chat.message.MessageInteractions
import com.waivera.waivera.feature.chat.message.ReadOnlyReason
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.SafeLinks
import com.waivera.waivera.feature.chat.message.SendErrors
import com.waivera.waivera.feature.chat.message.SendTracker
import com.waivera.waivera.feature.chat.message.StagedImage
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One conversation: timeline, composer, typing and the message actions (03; 05 §2-4).
 *
 * The cached timeline also carries thread replies (they arrive in the same hub group); they are
 * filtered out here and shown only in their thread (04 §2).
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel(assistedFactory = ConversationViewModel.Factory::class)
class ConversationViewModel @AssistedInject constructor(
    @Assisted private val conversationId: Long,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    bookmarks: BookmarkRepository,
    people: PeopleRepository,
    session: SessionRepository,
    private val realtime: RealtimeStatusRepository,
    private val io: AttachmentIo,
    downloads: AttachmentDownloads,
    private val clock: ChatClock,
    @ChatComputeDispatcher private val computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(conversationId: Long): ConversationViewModel
    }

    private val _state = MutableStateFlow(
        ConversationUiState(content = if (isValid(conversationId)) ConversationContent.Loading else ConversationContent.Invalid),
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

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

    /** Every cached message, thread replies included, for highlight lookups. */
    @Volatile private var allMessages: List<Message> = emptyList()
    private var conversation: Conversation? = null
    private var typingIds: List<String> = emptyList()

    private var olderJob: Job? = null
    private var highlightJob: Job? = null
    private var requestedHighlight: Long? = null

    private var isResumed = false
    private var lastMarkedRead: Long? = null
    private var markReadJob: Job? = null

    init {
        if (isValid(conversationId)) start(bookmarks, people, session)
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
            combine(conversationFlow, myUserId, directory) { conversation, me, names -> Triple(conversation, me, names) }
                .collect { (conversation, me, names) -> onConversation(conversation, me, names) }
        }
        viewModelScope.launch {
            combine(conversations.observeTyping(conversationId), myUserId) { typing, me -> typing - setOfNotNull(me) }
                .collect { typing ->
                    typingIds = typing.sorted()
                    _state.update { it.copy(typingNames = typingNames(it.names)) }
                }
        }
        viewModelScope.launch { interactions.sheet.collect { sheet -> _state.update { it.copy(sheet = sheet) } } }

        val isBot = conversationFlow.map { it?.kind == ConversationKind.BOT }.distinctUntilChanged()
        val prepared = combine(messages.observeTimeline(conversationId), bookmarks.observeBookmarkedIds(), myUserId, isBot) {
                timeline, saved, me, bot ->
            prepare(timeline, saved, me, bot)
        }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
        viewModelScope.launch {
            combine(prepared, liveArrivals) { input, live ->
                input to TimelineBuilder.build(input.messages, input.myUserId, clock.zone, input.isBot, liveArrivals = live)
            }
                .flowOn(computeDispatcher)
                .collect { (input, items) -> onTimeline(input, items) }
        }
    }

    private class Prepared(
        val synced: Synced<MessageTimeline>,
        val messages: List<Message>,
        val myUserId: String?,
        val isBot: Boolean,
    )

    /** Runs once per data emission, so the trackers see each change exactly once. */
    private fun prepare(timeline: Synced<MessageTimeline>, saved: Set<Long>, me: String?, bot: Boolean): Prepared {
        allMessages = timeline.data.messages
        val visible = timeline.data.messages
            .filter { !it.isThreadReply }
            .map { if (it.isBookmarked == (it.id in saved)) it else it.copy(isBookmarked = it.id in saved) }
        val arrivals = arrivalTracker.update(visible)
        if (arrivals.isNotEmpty()) {
            liveArrivals.update { it + arrivals }
            viewModelScope.launch {
                delay(ARRIVAL_WINDOW_MILLIS)
                liveArrivals.update { it - arrivals }
            }
        }
        val events = sendTracker.update(visible)
        if (events.newFailures > 0) {
            val status = realtime.status.value
            val current = _state.value
            val kind = SendErrors.forFailedSend(
                error = visible.lastOrNull { it.sendState == SendState.FAILED }?.sendError,
                isOnline = status.isOnline,
                canWrite = current.canWrite,
                isArchived = conversation?.channel?.isArchived == true,
            )
            _state.update { it.copy(sendError = kind) }
            emit(ChatEffect.Haptic(HapticMoment.Error))
        }
        if (events.retrySucceeded) emit(ChatEffect.Haptic(HapticMoment.Success))
        return Prepared(timeline, visible, me, bot)
    }

    private fun onTimeline(input: Prepared, items: List<TimelineItem>) {
        val synced = input.synced
        val error = synced.error
        val content = when {
            items.isNotEmpty() -> ConversationContent.Messages
            error != null -> ConversationContent.Error(error.messageRes)
            synced.isInitialLoad -> ConversationContent.Loading
            else -> ConversationContent.Empty
        }
        _state.update {
            it.copy(
                content = content,
                items = items,
                isStale = items.isNotEmpty() && synced.error != null,
                hasOlder = synced.data.hasOlder,
                isLoadingOlder = synced.data.isLoadingOlder || olderJob?.isActive == true,
                myUserId = input.myUserId,
                isBot = input.isBot,
            )
        }
        markReadIfNeeded()
    }

    private fun onConversation(value: Conversation?, me: String?, directory: Map<String, String>) {
        conversation = value
        val names = buildMap {
            putAll(directory)
            value?.participants?.forEach { if (it.name.isNotBlank()) put(it.id, it.name) }
            allMessages.forEach { m -> m.author?.takeIf { it.name.isNotBlank() }?.let { put(it.id, it.name) } }
        }
        _state.update {
            it.copy(
                title = value?.title(me)?.takeIf(String::isNotBlank),
                names = names,
                typingNames = typingNames(names),
                canWrite = value?.let { c -> c.canWrite && c.kind != ConversationKind.BOT },
                readOnlyReason = value?.let(::readOnlyReason),
                call = it.call.copy(
                    available = value != null && value.kind != ConversationKind.BOT,
                    isLive = value?.ongoingCall?.isLive == true,
                ),
                myUserId = me,
            )
        }
    }

    private fun typingNames(names: Map<String, String>): List<String> = typingIds.map { names[it].orEmpty() }

    // region Lifecycle

    /** The screen is resumed: mark what is on screen read (03 §11). */
    fun onResumed() {
        isResumed = true
        markReadIfNeeded()
    }

    fun onPaused() {
        isResumed = false
    }

    override fun onCleared() {
        if (isValid(conversationId)) conversations.onComposerChanged(conversationId, isEmpty = true)
    }

    private fun markReadIfNeeded() {
        if (!isResumed || markReadJob?.isActive == true) return
        val newest = _state.value.items.asReversed().firstNotNullOfOrNull { item ->
            (item as? TimelineItem.MessageRow)?.message?.takeIf { !it.isPending && it.id > 0 }?.id
                ?: (item as? TimelineItem.SystemRow)?.message?.id
                ?: (item as? TimelineItem.CallLog)?.messages?.lastOrNull()?.id
        } ?: return
        if (newest == lastMarkedRead) return
        markReadJob = viewModelScope.launch {
            // On failure the guard is released so the next emission or resume tries again.
            conversations.markRead(conversationId, newest).onSuccess { lastMarkedRead = newest }
        }
    }

    // endregion

    // region Loading

    fun retryLoad() {
        viewModelScope.launch {
            conversations.refreshConversation(conversationId)
            messages.refreshLatest(conversationId)
        }
    }

    /** Called when the list nears its oldest row. */
    fun loadOlder() {
        val current = _state.value
        if (!current.hasOlder || current.isLoadingOlder || olderJob?.isActive == true) return
        _state.update { it.copy(isLoadingOlder = true, olderFailed = false) }
        olderJob = viewModelScope.launch {
            val failed = messages.loadOlder(conversationId).isFailure
            _state.update { it.copy(isLoadingOlder = false, olderFailed = failed) }
        }
    }

    fun retryConnection() = realtime.retryNow()

    // endregion

    // region Highlight

    /**
     * Scrolls to and briefly highlights [messageId] (activity, bookmark and push jumps). The nav
     * entry ignores the message id, so the screen calls this again whenever it changes.
     */
    fun highlight(messageId: Long?) {
        if (messageId == null || messageId <= 0 || messageId == requestedHighlight) return
        requestedHighlight = messageId
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch { runHighlight(messageId) }
    }

    private suspend fun runHighlight(messageId: Long) {
        _state.first { it.content != ConversationContent.Loading }
        allMessages.firstOrNull { it.id == messageId }?.takeIf { it.isThreadReply }?.let { reply ->
            reply.threadRootMessageId?.let { emit(ChatEffect.OpenThread(it)) }
            return
        }
        var key = keyFor(messageId)
        if (key == null && messages.loadAround(conversationId, messageId).isSuccess) key = awaitKey(messageId)
        var pages = 0
        while (key == null && pages < MAX_HIGHLIGHT_PAGES && _state.value.hasOlder) {
            if (messages.loadOlder(conversationId).getOrDefault(false).not()) break
            key = awaitKey(messageId)
            pages++
        }
        if (key == null) return
        delay(HIGHLIGHT_SCROLL_DELAY_MILLIS)
        emit(ChatEffect.ScrollTo(key))
        _state.update { it.copy(highlightKey = key) }
        delay(HIGHLIGHT_MILLIS)
        _state.update { if (it.highlightKey == key) it.copy(highlightKey = null) else it }
    }

    private fun keyFor(messageId: Long): String? = _state.value.items.firstOrNull { item ->
        when (item) {
            is TimelineItem.MessageRow -> item.message.id == messageId
            is TimelineItem.SystemRow -> item.message.id == messageId
            is TimelineItem.CallLog -> item.messages.any { it.id == messageId }
            is TimelineItem.DaySeparator -> false
        }
    }?.key

    private suspend fun awaitKey(messageId: Long): String? = withTimeoutOrNull(HIGHLIGHT_WAIT_MILLIS) {
        _state.map { keyFor(messageId) }.filterNotNull().first()
    }

    // endregion

    // region Composer

    fun onDraftChanged(text: String) {
        conversations.onComposerChanged(conversationId, text.isBlank())
        if (_state.value.sendError != null) _state.update { it.copy(sendError = null) }
    }

    fun dismissSendError() = _state.update { it.copy(sendError = null) }

    fun onImagePicked(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val image = io.describe(uri)
            if (image == null) {
                emit(ChatEffect.Snackbar(R.string.feature_chat_image_unreadable))
                emit(ChatEffect.Haptic(HapticMoment.Error))
            } else {
                _state.update { it.copy(stagedImage = image) }
            }
        }
    }

    fun removeStagedImage() = _state.update { it.copy(stagedImage = null) }

    fun send(draft: String) {
        val current = _state.value
        if (current.canWrite != true) return
        current.editingMessageId?.let { return submitEdit(it, draft) }
        val outgoing = createOutgoingMessage(draft)
        val image: StagedImage? = current.stagedImage
        if (outgoing == null && image == null) return
        _state.update { it.copy(stagedImage = null, sendError = null) }
        emit(ChatEffect.SetDraft(""))
        conversations.onComposerChanged(conversationId, isEmpty = true)
        viewModelScope.launch {
            val attachment = image?.let(io::source)
            runCatching { messages.send(conversationId, outgoing?.text.orEmpty(), outgoing?.doc, attachment) }
                .onSuccess {
                    emit(ChatEffect.Haptic(HapticMoment.Success))
                    emit(ChatEffect.ScrollToLatest)
                }
                .onFailure { error ->
                    val typed = error as? WaiveraError ?: WaiveraError.from(error)
                    _state.update { it.copy(sendError = SendErrors.from(typed), stagedImage = image) }
                    emit(ChatEffect.SetDraft(draft))
                    emit(ChatEffect.Haptic(HapticMoment.Error))
                }
        }
    }

    private fun submitEdit(messageId: Long, draft: String) {
        val outgoing = createOutgoingMessage(draft) ?: return
        viewModelScope.launch {
            messages.edit(messageId, outgoing.text, outgoing.doc)
                .onSuccess {
                    _state.update { it.copy(editingMessageId = null) }
                    emit(ChatEffect.SetDraft(""))
                    emit(ChatEffect.Haptic(HapticMoment.Success))
                }
                .onFailure { error ->
                    val typed = error as? WaiveraError ?: WaiveraError.from(error)
                    emit(ChatEffect.Snackbar(R.string.feature_chat_edit_failed, detail = typed.messageRes))
                    emit(ChatEffect.Haptic(HapticMoment.Error))
                }
        }
    }

    fun startEdit(message: Message) {
        interactions.dismissSheet()
        _state.update { it.copy(editingMessageId = message.id, stagedImage = null) }
        emit(ChatEffect.SetDraft(message.plainText()))
    }

    fun cancelEdit() {
        _state.update { it.copy(editingMessageId = null) }
        emit(ChatEffect.SetDraft(""))
    }

    // endregion

    // region Rows and actions

    fun toggleTimestamp(key: String) = _state.update {
        it.copy(visibleTimestamps = if (key in it.visibleTimestamps) it.visibleTimestamps - key else it.visibleTimestamps + key)
    }

    fun openActions(message: Message) {
        if (!ChatActions.canOpen(message)) return
        val current = _state.value
        emit(ChatEffect.Haptic(HapticMoment.LongPress))
        interactions.openActions(ChatActions.model(message, current.myUserId, current.canWrite, allowReply = true))
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

    /** Reply from the action sheet, or the thread chip: replies live in the thread (04 §1). */
    fun openThread(message: Message) {
        interactions.dismissSheet()
        emit(ChatEffect.OpenThread(message.navigationRootId ?: message.id))
    }

    fun openThread(rootMessageId: Long) = emit(ChatEffect.OpenThread(rootMessageId))

    fun retry(clientMessageId: String) {
        sendTracker.markRetried(clientMessageId)
        _state.update { it.copy(sendError = null) }
        interactions.retry(clientMessageId)
    }

    fun discard(clientMessageId: String) = interactions.discard(clientMessageId)

    /** A failed row's Edit puts its text back in the composer and drops the failed copy (03 §16). */
    fun editFailed(message: Message) {
        val clientId = message.clientMessageId ?: return
        emit(ChatEffect.SetDraft(message.plainText()))
        interactions.discard(clientId)
    }

    /** Sheets hold message ids; the screen looks the current copy up here. */
    fun message(messageId: Long): Message? = allMessages.firstOrNull { it.id == messageId }

    // endregion

    // region Call

    /** Video calls run on the web; the header button and the banner both hand off (03 §12). */
    fun openCall() {
        val current = conversation ?: return
        if (_state.value.call.isOpening) return
        _state.update { it.copy(call = it.call.copy(isOpening = true)) }
        viewModelScope.launch {
            conversations.callLobbyUrl(current.id)
                .onSuccess { url ->
                    if (SafeLinks.isAllowed(url)) {
                        emit(ChatEffect.OpenUrl(url))
                    } else {
                        emit(ChatEffect.Snackbar(R.string.feature_chat_call_open_failed))
                    }
                }
                .onFailure { error ->
                    val typed = error as? WaiveraError ?: WaiveraError.from(error)
                    emit(ChatEffect.Snackbar(R.string.feature_chat_call_open_failed, detail = typed.messageRes))
                    emit(ChatEffect.Haptic(HapticMoment.Error))
                }
            _state.update { it.copy(call = it.call.copy(isOpening = false)) }
        }
    }

    // endregion

    fun rowTime(): RowTime = RowTime(clock.now(), clock.zone)

    private fun emit(effect: ChatEffect) {
        _effects.trySend(effect)
    }

    companion object {
        const val ARRIVAL_WINDOW_MILLIS = 1_000L
        const val HIGHLIGHT_SCROLL_DELAY_MILLIS = 160L
        const val HIGHLIGHT_MILLIS = 3_200L
        const val HIGHLIGHT_WAIT_MILLIS = 2_000L
        const val MAX_HIGHLIGHT_PAGES = 8

        fun isValid(conversationId: Long): Boolean = conversationId > 0

        fun readOnlyReason(conversation: Conversation): ReadOnlyReason? = when {
            conversation.kind == ConversationKind.BOT -> ReadOnlyReason.Automated
            conversation.channel?.isArchived == true -> ReadOnlyReason.ArchivedChannel
            conversation.channel?.canWrite == false -> ReadOnlyReason.ReadOnlyChannel
            else -> null
        }
    }
}
