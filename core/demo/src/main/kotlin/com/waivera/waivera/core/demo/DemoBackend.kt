@file:Suppress("MagicNumber")

package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * In-memory state shared by every demo repository, seeded from [DemoData]. Tests and
 * screenshot harnesses can reach in and set [failRefreshes] or [syncStatus] to render error
 * and loading states without a server.
 */
class DemoBackend(val clock: Clock = Clock.fixed(DemoData.NOW, ZoneOffset.UTC)) {
    val user = MutableStateFlow<CurrentUser?>(DemoData.currentUser())
    val conversations = MutableStateFlow(DemoData.conversations().associateBy { it.id })
    val messages = MutableStateFlow(DemoData.messages().groupBy { it.conversationId })
    val bookmarks = MutableStateFlow(DemoData.bookmarkedIds())
    val activity = MutableStateFlow(DemoData.activity())
    val people = MutableStateFlow(DemoData.people())
    val events = MutableStateFlow(DemoData.events())
    val milestones = MutableStateFlow(DemoData.milestones())
    val typing = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val settings = MutableStateFlow(AppSettings())
    val visibleConversation = MutableStateFlow<Long?>(null)

    /** When set, every refresh fails with this error (cached data stays visible). */
    var failRefreshes: WaiveraError? = null

    /**
     * The web app for call lobbies. Null in demo builds, so "Join on web" explains that it is
     * not configured, exactly as a live build without `waivera.webBaseUrl` does; tests set it.
     */
    var webBase: HttpUrl? = null

    /** Overrides the status every flow reports, for loading-state screenshots. */
    val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    private var nextId = 20_000L

    val myUserId: String? get() = user.value?.userId

    fun nextMessageId(): Long = nextId++

    fun now(): Instant = clock.instant()

    fun <T> synced(data: Flow<T>): Flow<Synced<T>> = combine(data, syncStatus) { value, status ->
        Synced(value, status, if (status == SyncStatus.Refreshing) null else now())
    }

    fun refresh(): Result<Unit> = failRefreshes?.let { Result.failure(it) } ?: Result.success(Unit)

    fun message(id: Long): Message? = messages.value.values.asSequence().flatten().firstOrNull { it.id == id }

    fun updateMessage(id: Long, transform: (Message) -> Message) {
        messages.update { all -> all.mapValues { (_, list) -> list.map { if (it.id == id) transform(it) else it } } }
    }

    /** Adds [message]; someone else's top-level message bumps the badge unless that chat is on screen. */
    fun append(message: Message) {
        messages.update { all ->
            all +
                (
                    message.conversationId to
                        (all[message.conversationId].orEmpty().filter { it.id != message.id } + message)
                    )
        }
        if (message.isThreadReply) {
            message.threadRootMessageId?.let { root ->
                updateMessage(root) {
                    it.copy(
                        threadReplyCount =
                        it.threadReplyCount + 1,
                    )
                }
            }
            return
        }
        conversations.update { all ->
            val c = all[message.conversationId] ?: return@update all
            val bump = !message.isMine(myUserId) && visibleConversation.value != c.id && !message.isPending
            all +
                (
                    c.id to
                        c.copy(
                            lastMessage = Mappers.preview(message),
                            unreadCount = if (bump) c.unreadCount + 1 else c.unreadCount,
                        )
                    )
        }
    }

    fun me(): Person? = user.value?.let {
        Person(it.userId, it.firstName, it.lastName, it.displayName, it.email, it.avatarUrl)
    }

    fun conversation(id: Long): Conversation? = conversations.value[id]

    fun addActivity(item: ActivityItem) = activity.update { listOf(item) + it.filter { a -> a.id != item.id } }
}
