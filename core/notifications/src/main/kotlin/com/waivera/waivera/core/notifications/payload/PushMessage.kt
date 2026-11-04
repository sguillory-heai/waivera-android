package com.waivera.waivera.core.notifications.payload

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import java.time.Instant

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

    /**
     * A stored web notification (`data.type == "notification"`): task, approval, signature,
     * survey or meeting. A tap opens the inbox, which refreshes, so the person picks it there; opening
     * a row marks it read.
     */
    data class Notice(
        val notificationId: String,
        val taskId: String?,
        override val title: String?,
        override val body: String?,
    ) : PushMessage {
        override val target: DeepLinkTarget
            get() = DeepLinkTarget.Notices
    }

    /**
     * A conversation or thread was read on another device (`data.type == "chat.read"`). Silent:
     * it closes this phone's tray entry and clears the cached badge, and never shows.
     */
    data class ChatRead(
        val conversationId: Long,
        val threadRootMessageId: Long?,
        /** When the read happened; a push held back by Doze must not clear newer messages. */
        val readAt: Instant? = null,
    ) : PushMessage {
        override val target: DeepLinkTarget get() = DeepLinkTarget.Conversation(conversationId)
        override val title: String? get() = null
        override val body: String? get() = null
    }

    /**
     * A stored notification was read on another device (`data.type == "notification.read"`);
     * [notificationId] null means all of them. Silent, like [ChatRead].
     */
    data class NoticeRead(val notificationId: String?) : PushMessage {
        override val target: DeepLinkTarget get() = DeepLinkTarget.Notices
        override val title: String? get() = null
        override val body: String? get() = null
    }

    /**
     * Any other type. Shown quietly and opens the inbox, never dropped silently on tap; one with
     * neither title nor text has nothing to show and is dropped, as the notifier always did.
     */
    data class Unknown(val type: String?, override val title: String?, override val body: String?) : PushMessage {
        override val target: DeepLinkTarget
            get() = DeepLinkTarget.Inbox
    }
}
