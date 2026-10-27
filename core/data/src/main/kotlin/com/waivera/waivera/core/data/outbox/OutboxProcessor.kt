package com.waivera.waivera.core.data.outbox

import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.live.MessageIngest
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.dto.SendMessageRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.log.WaiveraLog
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.realtime.RealtimeClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Sends queued messages in order. The hub is preferred when connected and there is no
 * attachment; otherwise REST `/api/chat/send`. Both carry the same client id, which the server
 * uses for idempotency, so a retry after an unknown outcome cannot duplicate a message.
 * Transient failures stay queued for the scheduler; permanent ones become FAILED with Retry.
 */
@Singleton
class OutboxProcessor @Inject constructor(
    private val store: OutboxStore,
    private val chatApi: ChatApi,
    private val realtime: RealtimeClient,
    private val ingest: MessageIngest,
) {
    private val mutex = Mutex()

    /** Processes everything queued. Returns true when something is left for a later retry. */
    suspend fun process(): Boolean = mutex.withLock {
        // A SENDING row at this point was interrupted (process death); the server dedupes by id.
        store.withState(SendState.SENDING).forEach { store.put(it.copy(state = SendState.QUEUED)) }
        val blocked = mutableSetOf<Long>()
        var retryLater = false
        for (item in store.withState(SendState.QUEUED)) {
            // Keep per-conversation order: nothing overtakes a message waiting for retry.
            val current = store.get(item.clientMessageId)
                ?.takeIf { it.state == SendState.QUEUED && item.conversationId !in blocked }
                ?: continue
            when (sendOne(current)) {
                Outcome.SENT, Outcome.FAILED -> Unit
                Outcome.RETRY -> {
                    blocked += item.conversationId
                    retryLater = true
                }
            }
        }
        retryLater
    }

    private enum class Outcome { SENT, RETRY, FAILED }

    private suspend fun sendOne(item: OutboxItem): Outcome {
        store.put(item.copy(state = SendState.SENDING))
        return try {
            val withUpload = upload(item)
            val dto = if (withUpload.attachment == null && realtime.state.value == ConnectionState.Connected) {
                sendOverHub(withUpload) ?: sendOverRest(withUpload)
            } else {
                sendOverRest(withUpload)
            }
            val mapped = Mappers.message(dto)
            val message = if (mapped.clientMessageId ==
                null
            ) {
                mapped.copy(clientMessageId = item.clientMessageId)
            } else {
                mapped
            }
            ingest.ingest(listOf(message))
            // The echo may not carry our id; make sure the optimistic row is gone either way.
            store.get(item.clientMessageId)?.let { store.remove(it) }
            Outcome.SENT
        } catch (cancelled: CancellationException) {
            store.get(item.clientMessageId)?.let { store.put(it.copy(state = SendState.QUEUED)) }
            throw cancelled
        } catch (error: WaiveraError) {
            val latest = store.get(item.clientMessageId) ?: return Outcome.SENT
            val attempts = latest.attempts + 1
            if (error.isTransient && attempts < MAX_AUTOMATIC_ATTEMPTS) {
                store.put(latest.copy(state = SendState.QUEUED, attempts = attempts))
                Outcome.RETRY
            } else {
                WaiveraLog.w(TAG, "Send failed permanently (${error.javaClass.simpleName})")
                store.put(latest.copy(state = SendState.FAILED, attempts = attempts))
                Outcome.FAILED
            }
        }
    }

    private suspend fun sendOverHub(item: OutboxItem) = try {
        realtime.sendMessage(
            conversationId = item.conversationId,
            text = item.text,
            doc = item.doc,
            parentMessageId = item.parentMessageId,
            clientMessageId = item.clientMessageId,
        )
    } catch (_: WaiveraError.RealtimeUnavailable) {
        null
    }

    private suspend fun sendOverRest(item: OutboxItem) = apiCall {
        chatApi.sendMessage(
            SendMessageRequest(
                conversationId = item.conversationId,
                clientMessageId = item.clientMessageId,
                text = item.text,
                attachmentUrl = item.attachment?.uploadedUrl,
                doc = item.doc,
                parentMessageId = item.parentMessageId,
            ),
        )
    }

    @Suppress("ReturnCount")
    private suspend fun upload(item: OutboxItem): OutboxItem {
        val attachment = item.attachment ?: return item
        if (attachment.uploadedUrl != null) return item
        val file = File(attachment.localPath)
        if (!file.exists()) throw WaiveraError.AttachmentExpired()
        val part = MultipartBody.Part.createFormData(
            "file",
            attachment.fileName,
            file.asRequestBody(attachment.contentType.toMediaTypeOrNull()),
        )
        val uploaded = apiCall { chatApi.uploadAttachment(part) }
        val key = uploaded.documentUrl ?: throw WaiveraError.Unexpected()
        val updated = item.copy(attachment = attachment.copy(uploadedUrl = key))
        store.put(updated.copy(state = SendState.SENDING))
        return updated
    }

    companion object {
        const val MAX_AUTOMATIC_ATTEMPTS = 5
        private const val TAG = "Outbox"
    }
}
