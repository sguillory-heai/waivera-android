package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.live.MeetingHandoff
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import javax.inject.Inject

/**
 * Builds the web lobby URL for a conversation's live call (spec 03 §19). There is no native
 * calling. Local seam: `docs/requests/chat-list.md` asks core for
 * `ConversationRepository.callLobbyUrl(conversationId)`, which can also create a room through
 * `createmeetingroom` when none is live; until then only a live room can be joined.
 */
fun interface CallLobby {
    suspend fun lobbyUrl(conversation: Conversation): Result<String>
}

class HandoffCallLobby @Inject constructor(private val config: NetworkConfig) : CallLobby {
    override suspend fun lobbyUrl(conversation: Conversation): Result<String> {
        val web = config.webBase ?: return Result.failure(WaiveraError.Configuration(listOf("waivera.webBaseUrl")))
        val room = conversation.ongoingCall?.takeIf { it.isLive }?.roomId?.takeIf(String::isNotBlank)
            ?: conversation.activeMeeting?.roomId?.takeIf(String::isNotBlank)
            ?: return Result.failure(WaiveraError.NotFound())
        val state = MeetingHandoff.State(
            calenderEventId = conversation.activeMeeting?.calendarEventId,
            relatedChatConversationId = conversation.id,
            // Joining an existing room, never publishing it.
            isPublisher = false,
            isOneToOneCall = conversation.participants.size == 2,
        )
        return Result.success(MeetingHandoff.lobbyUrl(web, room, state))
    }
}
