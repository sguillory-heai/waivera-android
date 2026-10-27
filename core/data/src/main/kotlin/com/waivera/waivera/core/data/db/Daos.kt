package com.waivera.waivera.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY activityAt DESC, id DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations")
    suspend fun getAll(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(rows: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :rootId OR threadRootId = :rootId ORDER BY id ASC")
    fun observeThread(rootId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<MessageEntity>

    @Query("SELECT MAX(id) FROM messages WHERE conversationId = :conversationId")
    suspend fun newestId(conversationId: Long): Long?

    @Query("SELECT MIN(id) FROM messages WHERE conversationId = :conversationId AND threadRootId IS NULL")
    suspend fun getOldestId(conversationId: Long): Long?

    @Upsert
    suspend fun upsert(rows: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id < :beforeId")
    suspend fun deleteOlderThan(conversationId: Long, beforeId: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: Long): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE state = :state ORDER BY createdAt ASC")
    suspend fun withState(state: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE conversationId = :conversationId")
    suspend fun forConversation(conversationId: Long): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE clientMessageId = :clientMessageId")
    suspend fun get(clientMessageId: String): OutboxEntity?

    @Upsert
    suspend fun upsert(row: OutboxEntity)

    @Query("DELETE FROM outbox WHERE clientMessageId = :clientMessageId")
    suspend fun delete(clientMessageId: String)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<ActivityEntity>

    @Query("SELECT * FROM activity")
    suspend fun getAll(): List<ActivityEntity>

    @Upsert
    suspend fun upsert(rows: List<ActivityEntity>)

    @Query("DELETE FROM activity WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM activity")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC, messageId DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Upsert
    suspend fun upsert(rows: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE messageId IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY sortName ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun observe(id: String): Flow<PersonEntity?>

    @Upsert
    suspend fun upsert(rows: List<PersonEntity>)

    @Query("DELETE FROM people")
    suspend fun clear()
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE `key` = :key")
    fun observe(key: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE `key` = :key")
    suspend fun get(key: String): DocumentEntity?

    @Upsert
    suspend fun upsert(row: DocumentEntity)

    @Query("DELETE FROM documents WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM documents")
    suspend fun clear()
}

/** Runs [block] atomically; Room's `withTransaction` in the app, a plain call in tests. */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}
