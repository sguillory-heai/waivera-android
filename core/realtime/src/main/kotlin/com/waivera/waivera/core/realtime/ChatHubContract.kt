package com.waivera.waivera.core.realtime

/**
 * Method and event names on sprout_api's hubs, from `Hubs/ChatHub.cs` and `Hubs/VideoHub.cs`
 * (spec/contract/hub.md). Keep in step with the server; run the `sync-api-contract` skill when
 * they change.
 */
object ChatHubContract {
    object Methods {
        /** `(int conversationId, string messageText, object? doc, int? parentMessageId, Guid? clientMessageId, string? attachmentUrl)`. */
        const val SEND_MESSAGE = "SendMessage"
        const val JOIN_CONVERSATION = "JoinConversation"
        const val LEAVE_CONVERSATION = "LeaveConversation"
        const val SEND_TYPING_STATUS = "SendTypingStatus"
        const val MARK_AS_READ = "MarkAsRead"

        /** `(int messageId, string newReactionType)`; toggle semantics, the only reaction writer (G-01). */
        const val SEND_REACTION = "SendReaction"
        const val GET_CONNECTION_ID = "GetConnectionId"
        const val GET_USER_IDENTIFIER = "GetUserIdentifier"
    }

    /**
     * Server events with their positional argument counts. Every event the server can send to a
     * staff connection is registered so none is dropped with a runtime warning (pitfalls §43).
     * `ReceiveComment` is left out: it only reaches `task_{id}` groups, which the app never joins,
     * and its argument count is not pinned. The phantom events listed in hub.md §3 are not
     * registered because no server code sends them.
     */
    object Events {
        const val RECEIVE_MESSAGE = "ReceiveMessage"
        const val RECEIVE_CONVERSATION_LIST_MESSAGE = "ReceiveConversationListMessage"
        const val RECEIVE_CHAT_ATTENTION = "ReceiveChatAttention"
        const val RECEIVE_CHAT_ACTIVITY = "ReceiveChatActivity"
        const val REMOVE_CHAT_ACTIVITY = "RemoveChatActivity"
        const val RECEIVE_CHAT_READ_STATE = "ReceiveChatReadState"
        const val RECEIVE_TYPING_STATUS_FOR_USER = "ReceiveTypingStatusForUser"
        const val RECEIVE_READ_RECEIPT = "ReceiveReadReceipt"
        const val RECEIVE_REACTION = "ReceiveReaction"
        const val RECEIVE_BOOKMARK_UPDATE = "ReceiveBookmarkUpdate"
        const val RECEIVE_MESSAGE_UPDATE = "ReceiveMessageUpdate"
        const val RECEIVE_MESSAGE_DELETE = "ReceiveMessageDelete"
        const val CONVERSATION_CREATED = "ConversationCreated"
        const val CONVERSATION_CALL_STATE_CHANGED = "ConversationCallStateChanged"
        const val REMOVE_AD_HOC_FROM_CONVERSATION = "RemoveAdHocFromConversation"
        const val RECEIVE_NOTIFICATION = "ReceiveNotification"

        /** Lower-case on the wire; register verbatim. */
        const val RECEIVE_NOTIFICATION_WEB = "receivenotification_web"

        @Suppress("MagicNumber")
        val argumentCounts: Map<String, Int> = mapOf(
            RECEIVE_MESSAGE to 1,
            RECEIVE_CONVERSATION_LIST_MESSAGE to 1,
            RECEIVE_CHAT_ATTENTION to 2,
            RECEIVE_CHAT_ACTIVITY to 1,
            REMOVE_CHAT_ACTIVITY to 1,
            RECEIVE_CHAT_READ_STATE to 1,
            RECEIVE_TYPING_STATUS_FOR_USER to 3,
            RECEIVE_READ_RECEIPT to 3,
            RECEIVE_REACTION to 5,
            RECEIVE_BOOKMARK_UPDATE to 1,
            RECEIVE_MESSAGE_UPDATE to 1,
            RECEIVE_MESSAGE_DELETE to 1,
            CONVERSATION_CREATED to 1,
            CONVERSATION_CALL_STATE_CHANGED to 1,
            REMOVE_AD_HOC_FROM_CONVERSATION to 1,
            RECEIVE_NOTIFICATION to 1,
            RECEIVE_NOTIFICATION_WEB to 1,
        )
    }
}

/** `api/videoHub`. Phase 1 hands calls to the web lobby, so only the names and payloads are modelled. */
object VideoHubContract {
    const val PATH = "api/videoHub"

    object Methods {
        const val JOIN_ROOM = "JoinRoom"
        const val ANNOUNCE_PUBLISHER = "AnnouncePublisher"
        const val RESUME_PUBLISHER = "ResumePublisher"
        const val BROADCAST_MEDIA_STATE = "BroadcastMediaState"
        const val NOTIFY_FEED_LEFT = "NotifyFeedLeft"
        const val LEAVE_ROOM = "LeaveRoom"
        const val NOTIFY_USER_LEFT = "NotifyUserLeft"
        const val REJECT_CALL = "RejectCall"
        const val REFRESH_SESSION_ACTIVITY = "RefreshSessionActivity"
        const val JOIN_EXTERNAL_INVITE_GROUP = "JoinExternalInviteGroup"
        const val NOTIFY_CALL_RINGING_OFF_TO_USERS = "NotifyCallRingingOffToUsers"
        const val ANNOUNCE_SCREEN_SHARE_STARTED = "AnnounceScreenShareStarted"
        const val ANNOUNCE_SCREEN_SHARE_STOPPED = "AnnounceScreenShareStopped"
    }

    object Events {
        const val RECEIVE_GROUP_CALL_INVITE = "ReceiveGroupCallInvite"
        const val RECEIVE_EXTERNAL_GROUP_CALL_INVITE = "ReceiveExternalGroupCallInvite"
        const val NEW_PUBLISHER = "NewPublisher"
        const val EXISTING_PARTICIPANTS = "ExistingParticipants"
        const val REMOTE_MEDIA_STATE_CHANGED = "RemoteMediaStateChanged"
        const val REMOTE_FEED_LEFT = "RemoteFeedLeft"
        const val SCREEN_SHARE_STARTED = "ScreenShareStarted"
        const val SCREEN_SHARE_ENDED = "ScreenShareEnded"
        const val CALL_REJECTED = "CallRejected"
    }
}
