package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.outbox.OutboxItem
import com.waivera.waivera.core.data.outbox.OutboxStore
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one way server messages enter the cache, so every path (REST page, hub echo, send
 * response) reconciles optimistic outbox rows the same way: by client id when the server echoes
 * it, otherwise (only when the payload has no client id) by (author, conversation, text, time
 * window). The echo is not guaranteed on every payload yet; that needs an API change (gaps log).
 */
@Singleton
class MessageIngest @Inject constructor(private val cache: ChatCache, private val outbox: OutboxStore) {
    suspend fun ingest(messages: List<Message>) {
        if (messages.isEmpty()) return
        cache.putMessages(messages)
        messages.forEach { reconcile(it) }
    }

    private suspend fun reconcile(message: Message) {
        val clientId = message.clientMessageId
        if (clientId != null) {
            // The id is authoritative: a second ingest of the same message (send response plus
            // hub echo, or a later page) finds nothing and must not fall through to the text match,
            // which would remove a different queued message with the same text.
            outbox.get(clientId)?.let { outbox.remove(it) }
            return
        }
        fallbackMatch(message)?.let { outbox.remove(it) }
    }

    /**
     * The (author, text, thread, time window) match for echoes without a client id. Items in
     * flight are skipped: their own send response settles them by id.
     */
    private suspend fun fallbackMatch(message: Message): OutboxItem? {
        val created = message.createdAt ?: return null
        val authorId = message.authorId ?: return null
        return outbox.forConversation(message.conversationId)
            .filter { it.state != SendState.SENDING }
            .sortedBy { it.createdAt }
            .firstOrNull { item ->
                item.authorId == authorId &&
                    item.text.trim() == message.text?.trim() &&
                    item.parentMessageId == message.parentMessageId &&
                    Duration.between(item.createdAt, created).abs() <= MATCH_WINDOW
            }
    }

    private companion object {
        val MATCH_WINDOW: Duration = Duration.ofMinutes(2)
    }
}
