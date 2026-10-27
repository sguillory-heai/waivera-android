package com.waivera.waivera.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Rows keep the domain model as a JSON blob ([json]) plus the columns needed to query and sort.
 * The server is authoritative and the cache is wiped on sign-out, so schema changes can use a
 * destructive migration instead of hand-written ones.
 */

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    /** Epoch millis of the last message, for inbox order; 0 when unknown. */
    val activityAt: Long,
    val json: String,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "id"]), Index(value = ["threadRootId"]), Index(value = ["clientMessageId"])],
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    val conversationId: Long,
    val threadRootId: Long?,
    val clientMessageId: String?,
    val createdAt: Long,
    val json: String,
)

/** Unsent messages. Shown in timelines next to [MessageEntity] rows until the server confirms them. */
@Entity(tableName = "outbox", indices = [Index(value = ["conversationId"])])
data class OutboxEntity(
    @PrimaryKey val clientMessageId: String,
    val conversationId: Long,
    val parentMessageId: Long?,
    val state: String,
    val attempts: Int,
    val createdAt: Long,
    val json: String,
)

@Entity(tableName = "activity")
data class ActivityEntity(@PrimaryKey val id: Long, val createdAt: Long, val json: String)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(@PrimaryKey val messageId: Long, val createdAt: Long, val json: String)

@Entity(tableName = "people")
data class PersonEntity(@PrimaryKey val id: String, val sortName: String, val json: String)

/** Everything else keyed by a string: calendar ranges, profile, milestones, sync metadata. */
@Entity(tableName = "documents")
data class DocumentEntity(@PrimaryKey val key: String, val updatedAt: Long, val json: String)
