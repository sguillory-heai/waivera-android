package com.waivera.waivera.core.notifications.payload

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget

/** A decoded push payload. Every variant knows where a tap goes. */
sealed interface PushMessage {
    val target: DeepLinkTarget

    /** Server-rendered title and body; the server already applied `previewContentEnabled`. */
    val title: String?
    val body: String?

    /**
     * The modern chat push (`data.type == "chat"`), the only push employees receive today.
     * [threadRootMessageId] is not sent yet (gap G-07 follow-up); it is read when present.
     */
    data class Chat(
        val conversationId: Long,
        val messageId: Long?,
        val threadRootMessageId: Long?,
        override val title: String?,
        override val body: String?,
    ) : PushMessage {
        override val target: DeepLinkTarget
            get() = if (threadRootMessageId != null) {
                DeepLinkTarget.Thread(conversationId, threadRootMessageId, messageId)
            } else {
                DeepLinkTarget.Conversation(conversationId, messageId)
            }
    }

    /** Legacy `NotificationType: "meeting"` (calendar invite or change). Participant-targeted today. */
    data class Meeting(val eventId: String?, override val title: String?, override val body: String?) : PushMessage {
        override val target: DeepLinkTarget
            get() = eventId?.let(DeepLinkTarget::CalendarEvent) ?: DeepLinkTarget.Schedule
    }

    /**
     * Legacy `NotificationType: "call"`. There is no in-app call screen yet, so a tap opens the
     * conversation, whose call banner offers Join; without a conversation it opens the inbox.
     */
    data class IncomingCall(
        val roomId: String?,
        val conversationId: Long?,
        val callerName: String?,
        override val title: String?,
        override val body: String?,
    ) : PushMessage {
        override val target: DeepLinkTarget
            get() = conversationId?.let { DeepLinkTarget.Conversation(it) } ?: DeepLinkTarget.Inbox
    }

    /** Legacy `NotificationType: "Survey"`. Staff have no survey screen, so a tap opens Today. */
    data class Survey(val instanceId: String?, override val title: String?, override val body: String?) : PushMessage {
        override val target: DeepLinkTarget
            get() = DeepLinkTarget.Today
    }

    /** Any other type. Shown quietly and opens the inbox, never dropped silently on tap. */
    data class Unknown(val type: String?, override val title: String?, override val body: String?) : PushMessage {
        override val target: DeepLinkTarget
            get() = DeepLinkTarget.Inbox
    }
}
