package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.feature.chat.R
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The inbox's state and actions, free of Android types so it is tested on the JVM with the
 * demo graph and fakes. [ChatListViewModel] is a thin owner of one of these.
 *
 * Rows render from the repository's cache flow (Room in live builds), so a cold start with a
 * populated cache shows rows before the network answers; the server stays authoritative.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListStateHolder(
    private val scope: CoroutineScope,
    private val conversations: ConversationRepository,
    private val session: SessionRepository,
    private val realtime: RealtimeStatusRepository,
    private val text: ChatListText,
    private val clock: Clock,
    ticker: Flow<Instant> = clockTicker(clock),
    locale: Locale = Locale.getDefault(),
) {
    private data class Local(
        val query: String = "",
        val filter: ChatListFilter = ChatListFilter.All,
        val userRefreshing: Boolean = false,
        val openingCallId: Long? = null,
        /** conversation id to the message id whose arrival wash is playing. */
        val arrivals: Map<Long, Long> = emptyMap(),
        /** Marked read here and not yet confirmed by the cache. */
        val optimisticRead: Set<Long> = emptySet(),
    )

    private val relativeTime = RelativeTime(text, locale)
    private val local = MutableStateFlow(Local())
    private val synced: StateFlow<Synced<List<Conversation>>?> =
        conversations.observeConversations().stateIn(scope, SharingStarted.Eagerly, null)
    private val effectChannel = Channel<ChatListEffect>(Channel.BUFFERED)
    private val hydrated = mutableSetOf<Pair<Long, Long>>()
    private val hydrationLock = Mutex()
    private var navigationLocked = false
    private var refreshJob: Job? = null

    val effects: Flow<ChatListEffect> = effectChannel.receiveAsFlow()

    /** One map for the whole inbox; rows look themselves up by id. */
    private val typing: Flow<Map<Long, Set<String>>> = conversations.observeAllTyping()
        .map { all -> all.filterValues { it.isNotEmpty() } }
        .distinctUntilChanged()

    val state: StateFlow<ChatListUiState> = combine(
        combine(synced, typing, ticker) { s, t, now -> Triple(s, t, now) },
        local,
        session.state,
        realtime.status,
    ) { (s, typingMap, now), l, sessionState, status ->
        buildState(s, typingMap, now, l, sessionState, status)
    }.stateIn(scope, SharingStarted.Eagerly, ChatListUiState())

    /** Peers of the first rows plus me, for [AvatarCache.prefetch]. */
    val avatarIds: Flow<List<String>> = state.map { st ->
        st.rows.take(AvatarCache.PREFETCH_ROWS).mapNotNull { (it.leading as? RowLeading.Direct)?.userId } +
            listOfNotNull(st.myUserId)
    }.distinctUntilChanged()

    init {
        scope.launch {
            val first = synced.filterNotNull().first()
            if (isStale(first)) refresh(userInitiated = false)
        }
        scope.launch { watchCache() }
    }

    // Actions

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onFilterChange(filter: ChatListFilter) = local.update { it.copy(filter = filter) }

    /** Guards against a fast double tap pushing the conversation twice (spec 02 §6). */
    fun onConversationClick(conversationId: Long) {
        if (navigationLocked) return
        navigationLocked = true
        effectChannel.trySend(ChatListEffect.OpenConversation(conversationId))
    }

    /** The list is visible again: navigation unlocks, and a stale list refetches (60 s rule). */
    fun onResumed() {
        navigationLocked = false
        val current = synced.value ?: return
        if (isStale(current)) refresh(userInitiated = false)
    }

    fun onRefresh() = refresh(userInitiated = true)

    fun onRetryConnection() = realtime.retryNow()

    fun onOpenProfile(userId: String) {
        effectChannel.trySend(ChatListEffect.OpenPerson(userId))
    }

    fun onMarkRead(conversationId: Long) {
        val conversation = conversation(conversationId) ?: return
        val lastMessageId = conversation.lastMessage?.messageId ?: return
        if (conversation.unreadCount <= 0) return
        markRead(conversationId, lastMessageId)
    }

    /**
     * Retry carries what to mark, so it sends again even when the row already looks read (a
     * failed attempt must never turn Retry into a no-op, AF-08).
     */
    private fun markRead(conversationId: Long, throughMessageId: Long) {
        local.update { it.copy(optimisticRead = it.optimisticRead + conversationId) }
        scope.launch {
            conversations.markRead(conversationId, throughMessageId).onFailure {
                local.update { it.copy(optimisticRead = it.optimisticRead - conversationId) }
                effectChannel.send(
                    ChatListEffect.ShowMessage(
                        R.string.feature_chat_list_mark_read_failed,
                        ChatListRetry.MarkRead(conversationId, throughMessageId),
                    ),
                )
            }
        }
    }

    /**
     * Opens the web lobby for the conversation's call (03 §19): the repository joins the live
     * room or creates one. Only one can be opening at a time.
     */
    fun onJoinCall(conversationId: Long) {
        if (local.value.openingCallId != null) return
        if (conversation(conversationId) == null) return
        local.update { it.copy(openingCallId = conversationId) }
        scope.launch {
            val result = conversations.callLobbyUrl(conversationId)
            val url = result.getOrNull()?.takeIf { it.startsWith(HTTPS) }
            effectChannel.send(
                if (url != null) {
                    ChatListEffect.OpenUrl(url)
                } else {
                    ChatListEffect.ShowMessage(
                        R.string.feature_chat_list_call_failed,
                        ChatListRetry.JoinCall(conversationId),
                    )
                },
            )
            local.update { it.copy(openingCallId = null) }
        }
    }

    fun onRetry(retry: ChatListRetry) = when (retry) {
        is ChatListRetry.MarkRead -> markRead(retry.conversationId, retry.throughMessageId)
        is ChatListRetry.JoinCall -> onJoinCall(retry.conversationId)
    }

    private fun refresh(userInitiated: Boolean) {
        if (refreshJob?.isActive == true) {
            if (userInitiated) local.update { it.copy(userRefreshing = true) }
            return
        }
        if (userInitiated) local.update { it.copy(userRefreshing = true) }
        refreshJob = scope.launch {
            conversations.refresh()
            local.update { it.copy(userRefreshing = false) }
        }
    }

    private fun isStale(s: Synced<List<Conversation>>): Boolean {
        val at = s.lastSyncedAt ?: return true
        return Duration.between(at, clock.instant()) >= STALE_AFTER
    }

    private fun conversation(id: Long): Conversation? = synced.value?.data?.firstOrNull { it.id == id }

    // Cache watching: arrival wash, optimistic read cleanup and sparse-preview hydration.

    private suspend fun watchCache() {
        var baseline: Map<Long, Long>? = null
        synced.filterNotNull().collect { s ->
            val me = session.state.value.currentUserId
            val latest = s.data.associate { it.id to (it.lastMessage?.messageId ?: 0L) }
            baseline?.let { previous -> detectArrivals(s.data, previous, me) }
            if (s.data.isNotEmpty()) baseline = latest
            val readNow = s.data.filter { it.unreadCount == 0 }.map { it.id }.toSet()
            local.update { l -> l.copy(optimisticRead = l.optimisticRead.filterNot { it in readNow }.toSet()) }
            hydrate(s.data)
        }
    }

    private fun detectArrivals(list: List<Conversation>, previous: Map<Long, Long>, me: String?) {
        list.forEach { c ->
            val message = c.lastMessage ?: return@forEach
            val before = previous[c.id] ?: 0L
            if (message.messageId > before && message.authorId != me) markArrival(c.id, message.messageId)
        }
    }

    private fun markArrival(conversationId: Long, messageId: Long) {
        local.update { it.copy(arrivals = it.arrivals + (conversationId to messageId)) }
        scope.launch {
            delay(ARRIVAL_MILLIS)
            local.update { l ->
                if (l.arrivals[conversationId] == messageId) l.copy(arrivals = l.arrivals - conversationId) else l
            }
        }
    }

    /**
     * Rows whose `lastMessage` exists but renders blank are fetched from `/conversation/{id}`,
     * at most [HYDRATE_BATCH] at a time and once per (conversation, message). The repository's
     * merge keeps the list's unread count and attachment fields (G-05, G-14).
     */
    private fun hydrate(list: List<Conversation>) {
        val sparse = list.mapNotNull { c ->
            val message = c.lastMessage ?: return@mapNotNull null
            val blank = ConversationPresentation.preview(message, text).kind == PreviewKind.Blank
            (c.id to message.messageId).takeIf { blank && hydrated.add(it) }
        }
        if (sparse.isEmpty()) return
        scope.launch {
            hydrationLock.withLock {
                sparse.chunked(HYDRATE_BATCH).forEach { batch ->
                    coroutineScope {
                        batch.map { (id, _) -> async { conversations.refreshConversation(id) } }.awaitAll()
                    }
                }
            }
        }
    }

    // State building

    @Suppress("LongParameterList")
    private fun buildState(
        s: Synced<List<Conversation>>?,
        typingMap: Map<Long, Set<String>>,
        now: Instant,
        l: Local,
        sessionState: SessionState,
        status: RealtimeStatus,
    ): ChatListUiState {
        val me = sessionState.currentUserId
        val user = sessionState.currentUser
        val base = ChatListUiState(
            query = l.query,
            filter = l.filter,
            connection = connection(status),
            isRefreshing = l.userRefreshing,
            accountName = user?.let { u ->
                u.displayName?.takeIf(String::isNotBlank)
                    ?: listOfNotNull(u.firstName, u.lastName).joinToString(" ").trim()
            }.orEmpty(),
            myUserId = me,
        )
        if (s == null) return base
        val data = s.data
        if (data.isEmpty()) {
            val error = s.error
            return when {
                error != null -> base.copy(content = ChatListContent.Error(error.messageRes))
                s.isInitialLoad -> base
                else -> base.copy(
                    content = ChatListContent.Empty(ConversationPresentation.emptyReason(l.filter, l.query)),
                )
            }
        }
        val zone = user?.zone ?: WaiveraTime.employeeZone(null)
        val rows = data.sortedWith(ConversationPresentation.Order)
            .map { c -> row(c, typingMap[c.id].orEmpty(), now, zone, me, l) }
            .filter { ConversationPresentation.matches(it, l.filter, l.query) }
        return base.copy(
            content = if (rows.isEmpty()) {
                ChatListContent.Empty(ConversationPresentation.emptyReason(l.filter, l.query))
            } else {
                ChatListContent.Rows
            },
            rows = rows,
            refreshErrorRes = s.error?.messageRes,
        )
    }

    @Suppress("LongParameterList")
    private fun row(
        c: Conversation,
        typingIds: Set<String>,
        now: Instant,
        zone: ZoneId,
        me: String?,
        l: Local,
    ): ChatRowUi {
        val preview = ConversationPresentation.preview(c.lastMessage, text)
        val isChannel = ConversationPresentation.isChannel(c)
        return ChatRowUi(
            id = c.id,
            title = ConversationPresentation.title(c, me, text),
            leading = ConversationPresentation.leading(c, me),
            preview = preview.text,
            previewKind = preview.kind,
            senderPrefix = ConversationPresentation.senderPrefix(c, preview, me, text),
            typingLabel = ConversationPresentation.typingLabel(c, typingIds, me, text),
            timeLabel = relativeTime.label(c.lastMessage?.createdAt, now, zone),
            unreadCount = if (c.id in l.optimisticRead) 0 else c.unreadCount,
            hasAttachment = c.lastMessage?.let { it.hasAttachment && !it.isDeleted } == true,
            isChannel = isChannel,
            isReadOnly = isChannel && !c.canWrite,
            isCallLive = ConversationPresentation.isCallLive(c),
            isOpeningCall = l.openingCallId == c.id,
            arrivalKey = l.arrivals[c.id],
            lastMessageId = c.lastMessage?.messageId,
        )
    }

    private fun connection(status: RealtimeStatus): ConnectionUi = when {
        !status.isOnline -> ConnectionUi.Offline
        else -> when (status.state) {
            ConnectionState.Connected -> ConnectionUi.Online
            ConnectionState.Connecting -> ConnectionUi.Connecting
            is ConnectionState.Reconnecting -> ConnectionUi.Reconnecting
            ConnectionState.Disconnected -> ConnectionUi.Offline
        }
    }

    companion object {
        val STALE_AFTER: Duration = Duration.ofSeconds(60)
        const val HYDRATE_BATCH = 6
        const val ARRIVAL_MILLIS = 1_400L
        private const val HTTPS = "https://"

        fun clockTicker(clock: Clock): Flow<Instant> = flow {
            while (true) {
                emit(clock.instant())
                delay(RelativeTime.TICK_MILLIS)
            }
        }
    }
}
