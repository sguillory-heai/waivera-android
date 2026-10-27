@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.data.outbox

import com.waivera.waivera.core.data.db.OutboxDao
import com.waivera.waivera.core.data.db.OutboxEntity
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
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
    )
}

private const val HASH_MASK = 0xFFFL

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OutboxDirectory

@Singleton
class OutboxStore @Inject constructor(private val dao: OutboxDao) {
    fun observeConversation(conversationId: Long): Flow<List<OutboxItem>> =
        dao.observeConversation(conversationId).map { rows -> rows.mapNotNull(::decode) }

    fun observeAll(): Flow<List<OutboxItem>> = dao.observeAll().map { rows -> rows.mapNotNull(::decode) }

    suspend fun get(clientMessageId: String): OutboxItem? = dao.get(clientMessageId)?.let(::decode)

    suspend fun withState(state: SendState): List<OutboxItem> = dao.withState(state.name).mapNotNull(::decode)

    suspend fun forConversation(conversationId: Long): List<OutboxItem> = dao.forConversation(conversationId).mapNotNull(::decode)

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
        dao.delete(item.clientMessageId)
        item.attachment?.let { File(it.localPath).delete() }
    }

    suspend fun clear(directory: File) {
        dao.clear()
        directory.listFiles()?.forEach(File::delete)
    }

    private fun decode(row: OutboxEntity): OutboxItem? =
        runCatching { WaiveraJson.decodeFromString(OutboxItem.serializer(), row.json) }.getOrNull()
}

/** Schedules durable outbox processing (WorkManager in the app, a counter in tests). */
fun interface OutboxScheduler {
    fun schedule()
}
