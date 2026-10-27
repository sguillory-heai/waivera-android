# Requests from `:feature:today`

Gaps the Today tab hit in modules it does not own. Each one has a local workaround in
`feature/today`, which should be removed once the request lands.

## 1. App shell: open a specific message or thread (`:app`)

**Status: Done.** `RouteContent` passes `onOpenMessage`, which opens the conversation at the message and the thread when a root id is set.

`TodayScreen` takes `onOpenConversation(conversationId)` only, so a mention, a thread reply or a
reaction cannot land on the message itself.

- Request: route `onOpenMessage(conversationId, messageId, threadRootMessageId?)` from `:app` to the
  conversation route, scrolling to the message and opening the thread when the root id is set.
- Workaround: `TodayScreen` has an optional trailing `onOpenMessage` parameter that defaults to
  `null`. Until `:app` passes it, message rows open the conversation. The required parameters from
  `app-shell.md` are unchanged.

## 2. Activity: roll back a failed mark-read (`:core:data`)

**Status: Done.** `LiveActivityRepository` restores the previous flags on failure; Today's `failedReadIds` overlay is removed.

`LiveActivityRepository.markRead` and `markAllRead` write `read = true` to the cache before calling
the API and leave it there when the call fails. The row then looks read even though the server
still has it unread.

- Request: restore the previous `read` value on failure, as the conversation repository does.
- Workaround: Today keeps a `failedReadIds` overlay that shows those rows as unread again with a
  Retry snackbar. The overlay is cleared after the next successful activity refresh.

## 3. Activity: total unread count (`sprout_api` + `:core:data`)

**Status: Moved to sprout-api.md** (§1). The API has no unread total, so the bounded auto-pager stays.

`GET /api/chat/activity` is paged and returns no unread total, so the badge is only correct for the
pages already loaded.

- Request: add `unreadCount` to the activity response (or a `/api/chat/activity/unread-count`
  endpoint) and expose it as `ActivityRepository.observeUnreadCount()`.
- Workaround: after each real sync, Today loads more pages while the oldest loaded page still
  contains an unread item, up to 5 pages. If it stops at the cap, the badge shows "N+".

## 4. Activity: serialize `loadMore` (`:core:data`)

**Status: Done.** `loadMore` runs one page at a time behind a mutex; the pager keeps its in-flight check because it also drives the loading row.

`LiveActivityRepository.loadMore` can run twice at once and fetch the same cursor twice.

- Request: guard it with a `Mutex`, or skip a call while one is running.
- Workaround: Today's `ActivityPager` never starts a second load while one is running.

## 5. Reconnect refresh for activity and calendar (`:core:data`)

**Status: Done.** `RealtimeSync.resync` refreshes activity and the open calendar ranges; Today's reconnect watcher is removed.

After a realtime reconnect, chats are resynced but activity and the schedule are not, so anything
that happened while offline is missing.

- Request: refresh activity and the open calendar range when the connection comes back, in the
  same place chats are resynced.
- Workaround: Today watches `RealtimeStatusRepository.status` and refreshes activity and its
  schedule range after the status goes from offline or Reconnecting back to Connected.

## 6. Ticket cards: system event data on `MessagePreview` (`:core:data`)

**Status: Done.** `MessagePreview` keeps `systemEventType` and a parsed `ticket`; the card shows the ticket number and title. Status is not in the payload (sprout-api.md §4).

Bot conversations carry the ticket updates, but `MessagePreview` drops `systemEventType` and the
event data, so Today cannot show the ticket number, status or assignee.

- Request: keep `systemEventType` and a typed ticket payload (id, title, status) on
  `MessagePreview`.
- Workaround: ticket rows show a generic "Ticket update" eyebrow plus the preview text and open the
  bot conversation.

## 7. Avatar images in feature modules (`:core:designsystem`)

**Status: Done.** `WaiveraAvatar(imageModel)` loads through Coil with `AvatarModel`; Today's DM and celebration avatars pass it.

`WaiveraAvatar` takes initials only, and feature modules have no image loader.

- Request: an `imageUrl` parameter on `WaiveraAvatar` that loads through Coil inside the design
  system.
- Workaround: Today shows initials, including the milestone ring.

## 8. Pending invitations outside the schedule range (`:core:data` / `sprout_api`)

**Status: Moved to sprout-api.md** (§2). No API query exists that is independent of a date range.

Today finds pending invitations in the 14-day range it observes for Next up. An invitation to an
event further out does not appear under Needs you.

- Request: a "my pending invitations" query (API filter or repository flow) that does not depend on
  a date range.
- Workaround: none beyond the 14-day range.

## Product note: no search on Today

**Status: Declined (no change).** Today has no search, as built; search stays on the Chats tab until product says otherwise.

The activity feed has the type chips (All, Mentions, Replies, Reactions) and an Unread toggle, but
no text search. Search across chats belongs to the Chats tab. Please confirm that this matches the
intended design.
