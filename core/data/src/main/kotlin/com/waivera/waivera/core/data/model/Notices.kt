package com.waivera.waivera.core.data.model

import java.time.Instant

/**
 * A stored notification, the same list the web bell shows (`api/notifications`). The server keeps
 * them; the phone only shows and marks them read.
 */
data class Notice(
    val id: String,
    val kind: NoticeKind,
    /** Title and message with the web's `{task}`/`{author}` placeholders already filled. */
    val title: String,
    val message: String,
    val createdAt: Instant?,
    val read: Boolean,
    /** The task it is about, when it names one; opens the task. */
    val taskId: String? = null,
    /** The calendar event it is about, when it names one; opens the event in the app. */
    val eventId: String? = null,
    /** A web path (`/…`) for everything the app has no screen for, opened in the browser. */
    val webPath: String? = null,
) {
    /** Asks the person to do something, so an unread one also shows in Today's Needs you. */
    val asksForAction: Boolean get() = kind in NoticeKind.ActionKinds
}

enum class NoticeKind {
    TaskAssigned,
    TaskActivity,
    Approval,
    Meeting,
    Signature,
    Survey,
    TimeOff,
    Other,
    ;

    companion object {
        val ActionKinds: Set<NoticeKind> = setOf(TaskAssigned, Approval, Signature, Survey)
    }
}

data class NoticeFeed(val items: List<Notice> = emptyList(), val unreadCount: Int = 0)
