package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.db.ActivityDao
import com.waivera.waivera.core.data.db.ActivityEntity
import com.waivera.waivera.core.data.db.BookmarkDao
import com.waivera.waivera.core.data.db.BookmarkEntity
import com.waivera.waivera.core.data.db.ConversationDao
import com.waivera.waivera.core.data.db.ConversationEntity
import com.waivera.waivera.core.data.db.DocumentDao
import com.waivera.waivera.core.data.db.DocumentEntity
import com.waivera.waivera.core.data.db.MessageDao
import com.waivera.waivera.core.data.db.MessageEntity
import com.waivera.waivera.core.data.db.PersonDao
import com.waivera.waivera.core.data.db.PersonEntity
import com.waivera.waivera.core.data.db.TransactionRunner
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.network.WaiveraJson
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Typed access to the Room cache. Rows hold domain models as JSON; a row that no longer decodes
 * (an older app version's shape) is skipped rather than crashing the screen.
 */
@Singleton
@Suppress("TooManyFunctions", "LongParameterList")
class ChatCache @Inject constructor(
    val conversationDao: ConversationDao,
    val messageDao: MessageDao,
    val activityDao: ActivityDao,
    val bookmarkDao: BookmarkDao,
    val personDao: PersonDao,
    val documentDao: DocumentDao,
    val transactions: TransactionRunner,
    private val clock: Clock,
) {
    // Conversations

    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { rows -> rows.mapNotNull { decode(Conversation.serializer(), it.json) } }

    fun observeConversation(id: Long): Flow<Conversation?> = conversationDao.observe(id).map { row ->
        row?.let { decode(Conversation.serializer(), it.json) }
    }.distinctUntilChanged()

    suspend fun conversation(id: Long): Conversation? = conversationDao.get(id)?.let {
        decode(Conversation.serializer(), it.json)
    }

    suspend fun conversations(): List<Conversation> = conversationDao.getAll().mapNotNull {
        decode(Conversation.serializer(), it.json)
    }

    suspend fun putConversations(items: List<Conversation>) {
        if (items.isEmpty()) return
        conversationDao.upsert(items.map(::entity))
    }

    /** Read-modify-write in a transaction; [transform] returning null leaves the row alone. */
    suspend fun updateConversation(id: Long, transform: (Conversation) -> Conversation?) {
        transactions.run {
            val current = conversation(id) ?: return@run
            transform(current)?.takeIf { it != current }?.let { conversationDao.upsert(listOf(entity(it))) }
        }
    }

    private fun entity(c: Conversation) = ConversationEntity(
        id = c.id,
        activityAt = c.activityAt?.toEpochMilli() ?: 0,
        json = WaiveraJson.encodeToString(Conversation.serializer(), c),
    )

    // Messages

    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.observeConversation(conversationId).map { rows ->
            rows.mapNotNull { decode(Message.serializer(), it.json) }
        }

    fun observeThread(rootId: Long): Flow<List<Message>> =
        messageDao.observeThread(rootId).map { rows -> rows.mapNotNull { decode(Message.serializer(), it.json) } }

    suspend fun message(id: Long): Message? = messageDao.get(id)?.let { decode(Message.serializer(), it.json) }

    suspend fun putMessages(items: List<Message>) {
        if (items.isEmpty()) return
        messageDao.upsert(items.map(::entity))
    }

    suspend fun updateMessage(id: Long, transform: (Message) -> Message?) {
        transactions.run {
            val current = message(id) ?: return@run
            transform(current)?.takeIf { it != current }?.let { messageDao.upsert(listOf(entity(it))) }
        }
    }

    private fun entity(m: Message) = MessageEntity(
        id = m.id,
        conversationId = m.conversationId,
        threadRootId = m.threadRootMessageId?.takeIf { it != m.id },
        clientMessageId = m.clientMessageId,
        createdAt = m.createdAt?.toEpochMilli() ?: 0,
        json = WaiveraJson.encodeToString(Message.serializer(), m),
    )

    // Activity

    fun observeActivity(): Flow<List<ActivityItem>> =
        activityDao.observeAll().map { rows -> rows.mapNotNull { decode(ActivityItem.serializer(), it.json) } }

    suspend fun putActivity(items: List<ActivityItem>) {
        if (items.isEmpty()) return
        activityDao.upsert(
            items.map {
                ActivityEntity(
                    it.id,
                    it.createdAt?.toEpochMilli() ?: 0,
                    WaiveraJson.encodeToString(ActivityItem.serializer(), it),
                )
            },
        )
    }

    suspend fun activity(ids: List<Long>): List<ActivityItem> =
        activityDao.getMany(ids).mapNotNull { decode(ActivityItem.serializer(), it.json) }

    suspend fun allActivity(): List<ActivityItem> = activityDao.getAll().mapNotNull {
        decode(ActivityItem.serializer(), it.json)
    }

    // Bookmarks

    fun observeBookmarks(): Flow<List<Bookmark>> = bookmarkDao.observeAll().map { rows ->
        rows.mapNotNull { decode(Message.serializer(), it.json)?.let(::Bookmark) }
    }

    suspend fun putBookmarks(items: List<Message>) {
        if (items.isEmpty()) return
        bookmarkDao.upsert(
            items.map {
                BookmarkEntity(
                    it.id,
                    it.createdAt?.toEpochMilli() ?: 0,
                    WaiveraJson.encodeToString(Message.serializer(), it),
                )
            },
        )
    }

    // People

    fun observePeople(): Flow<List<Person>> = personDao.observeAll().map { rows ->
        rows.mapNotNull { decode(Person.serializer(), it.json) }
    }

    fun observePerson(id: String): Flow<Person?> = personDao.observe(id).map { row ->
        row?.let { decode(Person.serializer(), it.json) }
    }

    suspend fun putPeople(items: List<Person>) {
        if (items.isEmpty()) return
        personDao.upsert(
            items.map {
                PersonEntity(it.id, it.name.lowercase(), WaiveraJson.encodeToString(Person.serializer(), it))
            },
        )
    }

    // Documents

    fun <T> observeDocument(key: String, serializer: KSerializer<T>): Flow<T?> =
        documentDao.observe(key).map { row -> row?.let { decode(serializer, it.json) } }.distinctUntilChanged()

    suspend fun <T> document(key: String, serializer: KSerializer<T>): T? = documentDao.get(key)?.let {
        decode(serializer, it.json)
    }

    suspend fun <T> putDocument(key: String, serializer: KSerializer<T>, value: T) {
        documentDao.upsert(DocumentEntity(key, clock.millis(), WaiveraJson.encodeToString(serializer, value)))
    }

    /** Wipes every table, for sign-out. */
    suspend fun clear() {
        transactions.run {
            conversationDao.clear()
            messageDao.clear()
            activityDao.clear()
            bookmarkDao.clear()
            personDao.clear()
            documentDao.clear()
        }
    }

    private fun <T> decode(serializer: KSerializer<T>, json: String): T? = try {
        WaiveraJson.decodeFromString(serializer, json)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
