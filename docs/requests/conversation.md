# Requests from the conversation and thread feature

Owner: `feature/chat` (conversation and thread screens). Each item says what the feature does
today so the gap stays visible, and the smallest core change that would close it.

## 1. `coil-compose` in the version catalog (`:feature:chat`)

**Status: Done in part.** `coil-compose` is in the catalog and the design-system avatar uses it. Declined: swapping `ChatImageLoader` for `AsyncImage` is chat feature work, left to its owner; the current loader keeps working.

Needed for: attachment images, the image preview and avatars without hand-drawing bitmaps.

Today: `ChatImageLoader` (feature/chat `message/ChatImages.kt`) runs requests through
`SingletonImageLoader.get(context).execute(...)` from `coil-core` and draws the bitmap with
`Image(BitmapPainter(...))`. It already uses the app's authenticated singleton (G-15), so only the
rendering path changes.

Ask: add `coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }` and allow
`:feature:chat` to depend on it. The feature then swaps `CoilChatImageLoader` for `AsyncImage`.

## 2. Conversation call lobby URL (`:core:data`)

**Status: Done.** `ConversationRepository.callLobbyUrl` (plus demo); `ConversationCallLobby` and `UnavailableCallLobby` are removed.

Needed for: the header video button and the "Call in progress / Join on web" banner (03 §12).

Today: `CalendarRepository.meetingLobbyUrl(eventId)` exists for events only. The feature codes
against a local `fun interface ConversationCallLobby { suspend fun lobbyUrl(conversation): Result<String> }`
(feature/chat `message/ChatSupport.kt`). Hilt binds `UnavailableCallLobby`, which fails with
`WaiveraError.Configuration(listOf("ConversationRepository.callLobbyUrl"))`, so the button shows
"Unable to open video call" until this lands.

Ask: `suspend fun ConversationRepository.callLobbyUrl(conversationId: Long): Result<String>` that
creates or joins the conversation's room (`ongoingCall.roomId` when live) and returns the web lobby
URL, plus the demo implementation. The feature then binds an adapter to it in `ChatFeatureModule`.

## 3. Why an outbox send failed, and upload progress (`:core:data` model)

**Status: Done.** `Message.sendError` (persisted as `SendFailure`) and `uploadProgress`; the banner prefers the outbox reason and uploads show a percentage.

Needed for: the "Not sent" banner copy (03 §11) and upload progress on image sends (03 §8).

Today: a FAILED outbox row carries no error, so `SendErrors.forFailedSend` infers archived,
read-only, offline or server from the connection and the conversation's permissions. Uploads show an
indeterminate "Uploading…" state while `SENDING`.

Ask: on `Message`, `val sendError: WaiveraError? = null` (set when the outbox gives up) and
`val uploadProgress: Float? = null` (0..1 while the attachment uploads). Both optional, so existing
callers are untouched.

## 4. Read receipts (`:core:data`)

**Status: Declined.** Not a parity gap (the reference never shows receipts) and the API has no cold-start read positions (sprout-api.md §7).

Needed for: "Seen" under one's own newest message, if product wants it. The reference app stores
`ReceiveReadReceipt` events but never renders them (05 §6), so this is not a parity gap.

Today: the data layer does not keep per-user read positions, so the screen shows no receipts; it
only sends read-through for what is on screen (03 §11, 04 §4).

Ask (only if receipts are wanted for launch): `fun ConversationRepository.observeReadPositions(conversationId): Flow<Map<String, Long>>`
(user id to last read message id). The feature would render "Seen by …" from it.

## 5. `androidx.browser` for links (`:feature:chat`)

**Status: Done.** `:app` provides a Custom Tabs `LocalUriHandler`, so no feature needs the dependency; chat links open through it.

Needed for: opening ticket links, message links and the call lobby in a Custom Tab, keeping the
person in the app.

Today: links pass `SafeLinks` (http/https with a host only) and open with `Intent.ACTION_VIEW`.

Ask: add `androidx-browser` to the catalog and allow it in `:feature:chat`.

## 6. Notes for core owners (no change required to ship)

- `LiveAttachmentRepository.download` has no in-flight dedupe; the feature wraps it in
  `AttachmentDownloads` (dedupe by message id, one retry unless the file was rejected). Moving that
  into the repository would let other features share it.

  **Status: Declined.** One consumer today; `AttachmentDownloads` stays in the feature until a second one needs it.
- Thread replies send over REST when the hub is down (a core decision). Spec 04 §8 expects a reply
  sent while disconnected to fail with the offline copy. The screen handles both: a refused send
  restores the draft verbatim with the reply copy; a queued one shows Sending/Queued. Please confirm
  the REST path is intended.

  **Status: Done (confirmed).** The REST fallback is intended: the outbox sends over REST when the hub is down (G-01), so a reply is queued rather than refused.
- The demo `react` succeeds while disconnected; the live repository fails with
  `RealtimeUnavailable` (G-01). The feature checks the connection before reacting, so both behave the
  same on screen.

  **Status: Declined (no change).** The screen already guards it, so both paths behave the same.
- `feature/chat/src/main/res/values/strings.xml` still holds the conversation and thread placeholder
  strings (`feature_chat_conversation_*`, `feature_chat_thread_title|placeholder|reference`). They are
  unused now; that file belongs to the chat list owner, so they are left for removal at merge.

  **Status: Done.** The file is deleted; every string in it was unused.
