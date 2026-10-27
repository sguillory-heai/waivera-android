package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.ChannelInfo
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.dto.MessageType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * Hand-written fakes for the list's repositories (no mocking frameworks for our own classes).
 * The demo graph from core:testing drives the screenshot tests; these give the unit tests
 * exact control over sync status, failures and concurrency.
 */

const val ME = "usr_alex_rivera"
const val PRIYA = "usr_priya_raman"
const val JORDAN = "usr_jordan_lee"
val NOW: Instant = Instant.parse("2026-11-01T16:30:00Z")

val me = Person(ME, "Alex", "Rivera", "Alex Rivera", "alex.rivera@example.com")
val priya = Person(PRIYA, "Priya", "Raman", "Priya Raman", "priya.raman@example.com")
val jordan = Person(JORDAN, "Jordan", "Lee", "Jordan Lee", "jordan.lee@example.com")

object EnglishText : ChatListText {
    override val noMessages = "No messages yet"
    override val photo = "Photo"
    override val fileAttachment = "File attachment"
    override val conversationUpdate = "Conversation update"
    override val deletedMessage = "Message deleted"
    override val channelFallback = "Channel"
    override val conversationFallback = "Conversation"
    override val botTitle = "Waivera"
    override val unknownPerson = "Unknown"
    override val you = "You"
    override val someone = "Someone"
    override val severalTyping = "Several people are typing…"
    override val now = "Now"
    override val yesterday = "Yesterday"

    override fun groupTitle(first: String, others: Int) = "$first & $others ${if (others == 1) "other" else "others"}"

    override fun typing(name: String) = "$name is typing…"

    override fun minutesAgo(minutes: Int) = "${minutes}m"
}

fun preview(
    id: Long,
    text: String? = "hello",
    at: Instant? = NOW.minusSeconds(60),
    author: String? = PRIYA,
    authorName: String? = null,
    type: MessageType = MessageType.USER,
    attachmentName: String? = null,
    attachmentType: String? = null,
    hasAttachment: Boolean = false,
    deleted: Boolean = false,
) = MessagePreview(
    messageId = id,
    authorId = author,
    authorName = authorName,
    text = text,
    attachmentName = attachmentName,
    attachmentContentType = attachmentType,
    hasAttachment = hasAttachment,
    isDeleted = deleted,
    messageType = type,
    createdAt = at,
)

fun dm(id: Long, last: MessagePreview? = preview(id * 100), unread: Int = 0, other: Person = priya) =
    Conversation(id, ConversationKind.DIRECT, listOf(me, other), lastMessage = last, unreadCount = unread)

fun channel(
    id: Long,
    name: String? = "north-team",
    last: MessagePreview? = preview(id * 100),
    canWrite: Boolean = true,
) =
    Conversation(
        id,
        ConversationKind.CHANNEL,
        listOf(me, priya),
        channel = ChannelInfo(name = name, canWrite = canWrite),
        lastMessage = last,
    )

class FakeConversationRepository(initial: Synced<List<Conversation>>) : ConversationRepository {
    val synced = MutableStateFlow(initial)
    val typing = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    var refreshCalls = 0
        private set
    var refreshResult: Result<Unit> = Result.success(Unit)

    /** When set, refresh waits for it, so the test can see the in-flight state. */
    var refreshGate: CompletableDeferred<Unit>? = null
    var markReadResult: Result<Unit> = Result.success(Unit)
    val markReadCalls = mutableListOf<Pair<Long, Long>>()
    val hydrationCalls = mutableListOf<Long>()
    var inFlightHydrations = 0
        private set
    var maxConcurrentHydrations = 0
        private set

    /** When set, every hydration waits for it, so the test can observe concurrency. */
    var hydrationGate: CompletableDeferred<Unit>? = null
    var openResult: Result<Long> = Result.success(99L)
    val openCalls = mutableListOf<List<String>>()

    fun setConversations(list: List<Conversation>, status: SyncStatus = SyncStatus.Idle, syncedAt: Instant? = NOW) {
        synced.value = Synced(list, status, syncedAt)
    }

    fun update(id: Long, transform: (Conversation) -> Conversation) = synced.update { s ->
        s.copy(data = s.data.map { if (it.id == id) transform(it) else it })
    }

    override fun observeConversations(): Flow<Synced<List<Conversation>>> = synced

    override fun observeConversation(conversationId: Long): Flow<Conversation?> =
        synced.map { s -> s.data.firstOrNull { it.id == conversationId } }

    override fun observeUnreadTotal(): Flow<Int> = synced.map { s -> s.data.sumOf { it.unreadCount } }

    override suspend fun refresh(): Result<Unit> {
        refreshCalls++
        refreshGate?.await()
        return refreshResult
    }

    override suspend fun refreshConversation(conversationId: Long): Result<Conversation> {
        hydrationCalls += conversationId
        inFlightHydrations++
        maxConcurrentHydrations = maxOf(maxConcurrentHydrations, inFlightHydrations)
        hydrationGate?.await()
        inFlightHydrations--
        return synced.value.data.firstOrNull { it.id == conversationId }?.let { Result.success(it) }
            ?: Result.failure(WaiveraError.NotFound())
    }

    override suspend fun openConversation(participantIds: List<String>): Result<Long> {
        openCalls += participantIds
        return openResult
    }

    override suspend fun conversationForEvent(calendarEventId: String): Result<Long> =
        Result.failure(WaiveraError.NotFound())

    override suspend fun leave(conversationId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit> {
        markReadCalls += conversationId to lastVisibleMessageId
        return markReadResult
    }

    override fun observeTyping(conversationId: Long): Flow<Set<String>> = typing.map { it[conversationId].orEmpty() }

    override fun onComposerChanged(conversationId: Long, isEmpty: Boolean) = Unit
}

class FakeSessionRepository(
    user: CurrentUser? = CurrentUser(ME, displayName = "Alex Rivera", timeZoneId = "America/Chicago"),
) : SessionRepository {
    private val _state = MutableStateFlow<SessionState>(
        user?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut(),
    )
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun signIn(email: String, password: String) = Result.success(Unit)

    override suspend fun signOut() = Result.success(Unit)

    override suspend fun sendPasswordReset(email: String) = Result.success(Unit)

    override suspend fun refresh() = Result.success(Unit)

    override suspend fun changePassword(currentPassword: String, newPassword: String) = Result.success(Unit)
}

class FakeRealtimeStatusRepository : RealtimeStatusRepository {
    val mutableStatus = MutableStateFlow(RealtimeStatus(ConnectionState.Connected, isOnline = true))
    override val status: StateFlow<RealtimeStatus> = mutableStatus.asStateFlow()
    var retries = 0
        private set

    override fun retryNow() {
        retries++
    }

    override fun setVisibleConversation(conversationId: Long?) = Unit

    override fun shouldSuppressNotification(conversationId: Long) = false
}

class FakePeopleRepository(initial: Synced<List<Person>>) : PeopleRepository {
    val directory = MutableStateFlow(initial)
    val avatarRequests = mutableListOf<String>()
    var refreshCalls = 0
        private set

    override fun observeDirectory(): Flow<Synced<List<Person>>> = directory

    override fun observePerson(userId: String): Flow<Person?> =
        directory.map { d -> d.data.firstOrNull { it.id == userId } }

    override suspend fun refresh(): Result<Unit> {
        refreshCalls++
        return Result.success(Unit)
    }

    override suspend fun avatar(userId: String): ByteArray? {
        avatarRequests += userId
        return if (userId == PRIYA) byteArrayOf(1, 2, 3) else null
    }
}
