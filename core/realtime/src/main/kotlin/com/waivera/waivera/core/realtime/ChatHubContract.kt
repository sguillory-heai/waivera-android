package com.waivera.waivera.core.realtime

/**
 * Method and event names on sprout_api's `ChatHub` (`/api/chatHub`), taken from
 * the Expo app's `@waivera/signalr` package. Keep in step with `Hubs/ChatHub.cs`.
 */
object ChatHubContract {
    object Methods {
        const val JOIN_CONVERSATION = "JoinConversation"
        const val LEAVE_CONVERSATION = "LeaveConversation"
        const val SEND_MESSAGE = "SendMessage"
        const val SEND_TYPING_STATUS = "SendTypingStatus"
        const val MARK_AS_READ = "MarkAsRead"
        const val SEND_REACTION = "SendReaction"
    }

    object Events {
        const val RECEIVE_MESSAGE = "ReceiveMessage"
        const val RECEIVE_CONVERSATION_LIST_MESSAGE = "ReceiveConversationListMessage"
        const val RECEIVE_TYPING_STATUS_FOR_USER = "ReceiveTypingStatusForUser"
        const val RECEIVE_READ_RECEIPT = "ReceiveReadReceipt"
        const val RECEIVE_REACTION = "ReceiveReaction"
        const val RECEIVE_BOOKMARK_UPDATE = "ReceiveBookmarkUpdate"
        const val RECEIVE_CHAT_ACTIVITY = "ReceiveChatActivity"
        const val REMOVE_CHAT_ACTIVITY = "RemoveChatActivity"
        const val RECEIVE_CHAT_READ_STATE = "ReceiveChatReadState"
        const val RECEIVE_CHAT_ATTENTION = "ReceiveChatAttention"
        const val CONVERSATION_CALL_STATE_CHANGED = "ConversationCallStateChanged"
    }
}
