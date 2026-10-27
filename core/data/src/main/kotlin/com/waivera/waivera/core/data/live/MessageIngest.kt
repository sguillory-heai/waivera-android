package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.outbox.OutboxStore
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one way server messages enter the cache, so every path (REST page, hub echo, send
 * response) reconciles optimistic outbox rows the same way: by client id when the server echoes
 * it, otherwise by (author, conversation, text, time window). The echo is not guaranteed on
 * every payload yet; that needs an API change (gaps log).
 */
@Singleton
class MessageIngest @Inject constructor(
    private val cache: ChatCache,
    private val outbox: OutboxStore,
) {
    suspend fun ingest(messages: List<Message>) {
        if (messages.isEmpty()) return
        cache.putMessages(messages)
        messages.forEach { reconcile(it) }
    }

    @Suppress("ReturnCount")
    private suspend fun reconcile(message: Message) {
        val byId = message.clientMessageId?.let { outbox.get(it) }
        if (byId != null) {
            outbox.remove(byId)
            return
        }
        val created = message.createdAt ?: return
        val authorId = message.authorId ?: return
        val candidate = outbox.forConversation(message.conversationId).firstOrNull { item ->
            item.authorId == authorId &&
                item.text.trim() == message.text?.trim() &&
                item.parentMessageId == message.parentMessageId &&
                Duration.between(item.createdAt, created).abs() <= MATCH_WINDOW
        }
        candidate?.let { outbox.remove(it) }
    }

    private companion object {
        val MATCH_WINDOW: Duration = Duration.ofMinutes(2)
    }
}
