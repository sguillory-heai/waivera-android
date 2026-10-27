package com.waivera.waivera.core.testing

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
import com.waivera.waivera.core.data.db.OutboxDao
import com.waivera.waivera.core.data.db.OutboxEntity
import com.waivera.waivera.core.data.db.PersonDao
import com.waivera.waivera.core.data.db.PersonEntity
import com.waivera.waivera.core.data.db.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory stand-ins for the Room DAOs, with the same ordering as the SQL queries. */
class InMemoryDatabase {
    val conversations = Conversations()
    val messages = Messages()
    val outbox = Outbox()
    val activity = Activity()
    val bookmarks = Bookmarks()
    val people = People()
    val documents = Documents()

    val transactions: TransactionRunner = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    class Conversations : ConversationDao {
        val rows = MutableStateFlow<Map<Long, ConversationEntity>>(emptyMap())

        override fun observeAll(): Flow<List<ConversationEntity>> = rows.map {
            it.values.sortedWith(
                compareByDescending<ConversationEntity> { r ->
                    r.activityAt
                }.thenByDescending { r -> r.id },
            )
        }

        override fun observe(id: Long): Flow<ConversationEntity?> = rows.map { it[id] }.distinctUntilChanged()

        override suspend fun get(id: Long): ConversationEntity? = rows.value[id]

        override suspend fun getAll(): List<ConversationEntity> = rows.value.values.toList()

        override suspend fun upsert(rows: List<ConversationEntity>) = this.rows.update {
            it +
                rows.associateBy { r -> r.id }
        }

        override suspend fun delete(ids: List<Long>) = rows.update { it - ids.toSet() }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    class Messages : MessageDao {
        val rows = MutableStateFlow<Map<Long, MessageEntity>>(emptyMap())

        override fun observeConversation(conversationId: Long): Flow<List<MessageEntity>> = rows.map { all ->
            all.values.filter { it.conversationId == conversationId }.sortedBy { it.id }
        }.distinctUntilChanged()

        override fun observeThread(rootId: Long): Flow<List<MessageEntity>> = rows.map { all ->
            all.values.filter { it.id == rootId || it.threadRootId == rootId }.sortedBy { it.id }
        }.distinctUntilChanged()

        override suspend fun get(id: Long): MessageEntity? = rows.value[id]

        override suspend fun getMany(ids: List<Long>): List<MessageEntity> = ids.mapNotNull { rows.value[it] }

        override suspend fun newestId(conversationId: Long): Long? =
            rows.value.values.filter { it.conversationId == conversationId }.maxOfOrNull { it.id }

        override suspend fun getOldestId(conversationId: Long): Long? = rows.value.values.filter {
            it.conversationId == conversationId && it.threadRootId == null
        }.minOfOrNull { it.id }

        override suspend fun upsert(rows: List<MessageEntity>) = this.rows.update {
            it + rows.associateBy { r -> r.id }
        }

        override suspend fun deleteConversation(conversationId: Long) =
            rows.update { all -> all.filterValues { it.conversationId != conversationId } }

        override suspend fun deleteOlderThan(conversationId: Long, beforeId: Long) =
            rows.update { all -> all.filterValues { it.conversationId != conversationId || it.id >= beforeId } }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    class Outbox : OutboxDao {
        val rows = MutableStateFlow<Map<String, OutboxEntity>>(emptyMap())

        private fun sorted(filter: (OutboxEntity) -> Boolean) = rows.value.values.filter(filter).sortedBy {
            it.createdAt
        }

        override fun observeConversation(conversationId: Long): Flow<List<OutboxEntity>> = rows.map { all ->
            all.values.filter { it.conversationId == conversationId }.sortedBy { it.createdAt }
        }.distinctUntilChanged()

        override fun observeAll(): Flow<List<OutboxEntity>> = rows.map { all -> all.values.sortedBy { it.createdAt } }

        override suspend fun withState(state: String): List<OutboxEntity> = sorted { it.state == state }

        override suspend fun forConversation(conversationId: Long): List<OutboxEntity> = sorted {
            it.conversationId ==
                conversationId
        }

        override suspend fun get(clientMessageId: String): OutboxEntity? = rows.value[clientMessageId]

        override suspend fun upsert(row: OutboxEntity) = rows.update { it + (row.clientMessageId to row) }

        override suspend fun delete(clientMessageId: String) = rows.update { it - clientMessageId }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    class Activity : ActivityDao {
        val rows = MutableStateFlow<Map<Long, ActivityEntity>>(emptyMap())

        override fun observeAll(): Flow<List<ActivityEntity>> = rows.map {
            it.values.sortedWith(
                compareByDescending<ActivityEntity> { r ->
                    r.createdAt
                }.thenByDescending { r -> r.id },
            )
        }

        override suspend fun getMany(ids: List<Long>): List<ActivityEntity> = ids.mapNotNull { rows.value[it] }

        override suspend fun getAll(): List<ActivityEntity> = rows.value.values.toList()

        override suspend fun upsert(rows: List<ActivityEntity>) = this.rows.update {
            it + rows.associateBy { r -> r.id }
        }

        override suspend fun delete(ids: List<Long>) = rows.update { it - ids.toSet() }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    class Bookmarks : BookmarkDao {
        val rows = MutableStateFlow<Map<Long, BookmarkEntity>>(emptyMap())

        override fun observeAll(): Flow<List<BookmarkEntity>> = rows.map {
            it.values.sortedWith(
                compareByDescending<BookmarkEntity> { r ->
                    r.createdAt
                }.thenByDescending { r -> r.messageId },
            )
        }

        override suspend fun upsert(rows: List<BookmarkEntity>) = this.rows.update {
            it +
                rows.associateBy { r -> r.messageId }
        }

        override suspend fun delete(ids: List<Long>) = rows.update { it - ids.toSet() }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    class People : PersonDao {
        val rows = MutableStateFlow<Map<String, PersonEntity>>(emptyMap())

        override fun observeAll(): Flow<List<PersonEntity>> = rows.map { it.values.sortedBy { r -> r.sortName } }

        override fun observe(id: String): Flow<PersonEntity?> = rows.map { it[id] }.distinctUntilChanged()

        override suspend fun upsert(rows: List<PersonEntity>) = this.rows.update { it + rows.associateBy { r -> r.id } }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    class Documents : DocumentDao {
        val rows = MutableStateFlow<Map<String, DocumentEntity>>(emptyMap())

        override fun observe(key: String): Flow<DocumentEntity?> = rows.map { it[key] }.distinctUntilChanged()

        override suspend fun get(key: String): DocumentEntity? = rows.value[key]

        override suspend fun upsert(row: DocumentEntity) = rows.update { it + (row.key to row) }

        override suspend fun delete(key: String) = rows.update { it - key }

        override suspend fun clear() = rows.update { emptyMap() }
    }
}
