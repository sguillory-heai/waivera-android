# Requests from the chat list (feature/chat, list side)

The chat list builds and runs against the current core modules. Each item below is a small
addition that would remove a local workaround in `feature/chat/.../list` or `.../newchat`. No
change to `feature/chat/build.gradle.kts` or the version catalog is needed.

## core:data

1. **`ConversationRepository.callLobbyUrl(conversationId: Long): Result<String>`**
   - Today the list builds the lobby URL itself in `list/CallLobby.kt` (`HandoffCallLobby`) from
     `Conversation.ongoingCall` / `activeMeeting.roomId` and `MeetingHandoff.lobbyUrl`.
   - The repository should own this, so the conversation header and the list share one path, and it
     should fall back to `createmeetingroom` when the cached conversation says a call is live but no
     room id is present (the Expo app does this; the list currently shows "Unable to open the video
     call" instead).
   - Replace `CallLobby` with a call to this method once it lands.

2. **Sort fallback timestamps on `Conversation`.** Spec 02 orders by last activity, then
   `modifiedDate`, then `createDate`. The model only exposes `activityAt`, so the list falls back
   to the epoch and then to id descending (`ConversationPresentation.Order`). Add
   `modifiedAt: Instant?` and `createdAt: Instant?` (or make `activityAt` apply that fallback in the
   mapper).

3. **`MessagePreview.isBot` (or `botId`).** Bot detection relies on `ConversationKind.BOT`, a
   `bot:` participant or author id, or a conversation whose only participant is me. A flag from the
   DTO would replace the heuristics in `ConversationPresentation.isBot`.

4. **`ConversationRepository.observeAllTyping(): Flow<Map<Long, Set<String>>>`.** The list combines
   one `observeTyping(id)` flow per row (`flatMapLatest` + `combine`). A single map flow from the
   realtime layer would be cheaper for long inboxes.

5. **Rich-text format on `MessagePreview`** (`contentFormat` and/or the parsed doc). The list
   sniffs TipTap JSON, double-encoded JSON and simple HTML in `list/RichTextPlain.kt`. Knowing the
   format from the DTO would let it skip guessing and share one converter with the conversation
   screen.

## core:designsystem

6. **Icons.** The list ships these as module drawables (`feature_chat_list_ic_*`): hash (channel),
   archive, bot, video, attachment, lock (read only), more, mark read. Move them into
   `WaiveraIcons` so the conversation screens and iOS naming stay aligned.

## Contract notes (no change made)

- `MeetingHandoff.State.relatedChatConversationId` is a `Long`; spec 02/the web lobby state
  describe it as a string. The list passes the `Long` through unchanged. Confirm against the
  sprout_api DTO.
- There is no per-conversation mute in the API (spec 11, drift 56), so the list shows no muted
  indicator.
