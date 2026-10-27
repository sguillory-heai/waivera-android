@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.data.outbox

import com.waivera.waivera.core.data.db.OutboxDao
import com.waivera.waivera.core.data.db.OutboxEntity
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendFailure
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

/** A staged attachment: copied into app storage at send time so a retry never re-reads the picker. */
@Serializable
data class OutboxAttachment(
    val localPath: String,
    val fileName: String,
    val contentType: String,
    val size: Long? = null,
    /** Storage key once uploaded, so a retry does not upload twice. */
    val uploadedUrl: String? = null,
)

@Serializable
data class OutboxItem(
    /** Client-generated UUID: the idempotency key for the hub and REST send (hub.md SendMessage). */
    val clientMessageId: String,
    val conversationId: Long,
    val authorId: String,
    val text: String,
    val doc: JsonElement? = null,
    val parentMessageId: Long? = null,
    val attachment: OutboxAttachment? = null,
    val state: SendState = SendState.QUEUED,
    val attempts: Int = 0,
    val createdAt: Instant,
    /** Why the last attempt gave up; set with [SendState.FAILED], cleared on retry. */
    val failure: SendFailure? = null,
    /** Upload progress 0..1, from memory only (never persisted). */
    @Transient val uploadProgress: Float? = null,
) {
    /** The optimistic message shown in the timeline. Negative ids never collide with server ids. */
    fun toMessage(author: com.waivera.waivera.core.data.model.Person?): Message = Message(
        id = -createdAt.toEpochMilli() - (clientMessageId.hashCode().toLong() and HASH_MASK),
        conversationId = conversationId,
        clientMessageId = clientMessageId,
        author = author,
        authorId = authorId,
        text = text,
        doc = doc,
        createdAt = createdAt,
        attachment = attachment?.let {
            Attachment(url = it.localPath, originalName = it.fileName, contentType = it.contentType, size = it.size)
        },
        parentMessageId = parentMessageId,
        threadRootMessageId = parentMessageId,
        sendState = state,
        sendFailure = failure.takeIf { state == SendState.FAILED },
        uploadProgress = uploadProgress.takeIf { state == SendState.SENDING },
    )
}

private const val HASH_MASK = 0xFFFL

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OutboxDirectory

@Singleton
class OutboxStore @Inject constructor(private val dao: OutboxDao) {
    /** Upload progress by client id, in memory: it changes too often to write to the database. */
    private val progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun observeConversation(conversationId: Long): Flow<List<OutboxItem>> =
        combine(dao.observeConversation(conversationId), progress) { rows, uploads -> decode(rows, uploads) }

    fun observeAll(): Flow<List<OutboxItem>> =
        combine(dao.observeAll(), progress) { rows, uploads -> decode(rows, uploads) }

    /** Records how much of [clientMessageId]'s attachment has been sent, 0..1. */
    fun setUploadProgress(clientMessageId: String, fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        progress.update { it + (clientMessageId to clamped) }
    }

    fun clearUploadProgress(clientMessageId: String) {
        progress.update { it - clientMessageId }
    }

    suspend fun get(clientMessageId: String): OutboxItem? = dao.get(clientMessageId)?.let(::decode)

    suspend fun withState(state: SendState): List<OutboxItem> = dao.withState(state.name).mapNotNull(::decode)

    suspend fun forConversation(conversationId: Long): List<OutboxItem> =
        dao.forConversation(conversationId).mapNotNull(::decode)

    suspend fun put(item: OutboxItem) {
        dao.upsert(
            OutboxEntity(
                clientMessageId = item.clientMessageId,
                conversationId = item.conversationId,
                parentMessageId = item.parentMessageId,
                state = item.state.name,
                attempts = item.attempts,
                createdAt = item.createdAt.toEpochMilli(),
                json = WaiveraJson.encodeToString(OutboxItem.serializer(), item),
            ),
        )
    }

    suspend fun remove(item: OutboxItem) {
        clearUploadProgress(item.clientMessageId)
        dao.delete(item.clientMessageId)
        item.attachment?.let { File(it.localPath).delete() }
    }

    suspend fun clear(directory: File) {
        progress.value = emptyMap()
        dao.clear()
        directory.listFiles()?.forEach(File::delete)
    }
}

/** A stored row as an item; null for a row written by an older, incompatible version. */
private fun decode(row: OutboxEntity): OutboxItem? =
    runCatching { WaiveraJson.decodeFromString(OutboxItem.serializer(), row.json) }.getOrNull()

private fun decode(rows: List<OutboxEntity>, uploads: Map<String, Float>): List<OutboxItem> =
    rows.mapNotNull { row ->
        decode(row)?.let { item -> uploads[item.clientMessageId]?.let { item.copy(uploadProgress = it) } ?: item }
    }

/** Schedules durable outbox processing (WorkManager in the app, a counter in tests). */
fun interface OutboxScheduler {
    fun schedule()
}
