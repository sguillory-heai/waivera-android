package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.network.dto.MessageType

/** Which actions a long press offers (03 §14). Threads use the same rules (04 §1). */
object ChatActions {
    /** Pending rows have inline Retry/Edit/Discard instead; deleted and system rows have none. */
    fun canOpen(message: Message): Boolean =
        !message.isPending && !message.isDeleted && message.type == MessageType.USER

    fun model(message: Message, myUserId: String?, canWrite: Boolean?, allowReply: Boolean): MessageActionsModel {
        val writable = canWrite == true
        val hasText = message.plainText().isNotEmpty()
        return MessageActionsModel(
            message = message,
            rich = TipTapParser.documentFor(message),
            canReact = writable,
            canReply = writable && allowReply && !message.isThreadReply,
            canEdit = writable && message.isMine(myUserId) && hasText,
            canDelete = message.isMine(myUserId),
            canCopy = hasText,
        )
    }
}
