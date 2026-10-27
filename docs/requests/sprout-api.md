# Requests for `sprout_api`

Gaps raised by the Android feature requests that only the API can close. The client works without
each one (the workaround is named), so none of these blocks the native build. Evidence was read
from the `sprout_api` source; paths are relative to that repo. Nothing here changes client code.

## 1. Activity unread total

From `today.md` §3.

- **Evidence.** `Controllers/Chat/ChatController.cs` `GET recent-activity` returns
  `List<ChatActivityResponse>` for one page (`page`, `pageSize` clamped to 1..100) with a per-row
  `Read` flag. There is no total, no unread count and no has-more flag.
- **Ask.** Add an unread total for the signed-in user, either as a wrapper
  (`{ items, unreadCount, hasMore }`) or a `GET recent-activity/unread-count` endpoint.
- **Client today.** Today loads up to 5 pages while the oldest loaded page still has an unread
  row, and shows "N+" when it stops at that cap. Once the count exists, core adds
  `ActivityRepository.observeUnreadCount()` and the pager goes away.

## 2. Pending invitations outside a date range

From `today.md` §8.

- **Evidence.** `Controllers/Calendar/CalendarEventController.cs` only offers range queries
  (`GetAllEvent?startTime&endTime`, `upcoming/Events-by-date`), `next-upcoming-events/{userId}`
  (the next two events) and `GetEventbyId/{id}`. None filters by the caller's invitation status.
- **Ask.** `GET api/CalendarEvent/pending-invitations` (or an `inviteStatus=pending` filter on
  `GetAllEvent` without a range) returning the same event DTO.
- **Client today.** Needs you lists pending invitations from the 14-day range Today already
  observes; an invitation further out does not appear there.

## 3. Conversation sort fallbacks

From `chat-list.md` §2.

- **Evidence.** `dto/Response/ChatResponeDto/UnifiedConversationResponse.cs` and
  `dto/Response/ConversationResponse.cs` carry `LastReadAt`, `UnreadCount`, `LastMessage`,
  `ActiveMeeting` and `OngoingCall`. They have no `LastActivityAt`, `ModifiedDate` or
  `CreateDate`, which spec 02 uses as the sort fallbacks.
- **Ask.** Add `lastActivityAt` (or `createDate` and `modifiedDate`) to both conversation shapes.
- **Client today.** `Conversation.activityAt` uses the last message time, then the live call
  start, then the meeting start. The list breaks ties by id, newest first.

## 4. Ticket bot messages: event type and status fields

From `today.md` §6 and spec 03 §15.

- **Evidence.**
  - Event types: `Services/Tickets/TicketService.cs` sends bot DMs through
    `ChatBotService.SendDirectMessageAsync` with these system event types:
    - `ticket.created` (line 124);
    - `ticket.comment` or `comment.mention` (around line 2370);
    - `ticket.{activity}`, such as `ticket.assigned` and `ticket.status` (line 2450).
  - No code path sends `ticket.update`, yet spec 03 and the Expo reference
    (`message-row.tsx:127`) render a ticket card only for `systemEventType === 'ticket.update'`.
  - Payload keys:
    - `ticket.created` sends `ticketId`, `title`, `type`, `createdBy`, `priority`,
      `createdAt`, `dueDate`, `createdDate`, `dueDateLabel` and `url`.
    - Activity messages send `ticketId`, `ticketInternalId`, `title`, `activity`, `detail` and
      `url`.
    - None of them sends a status or an assignee as separate fields.
- **Ask.**
  - Confirm the event type clients should treat as a ticket card. Either the server sends
    `ticket.update`, or clients match the `ticket.` prefix (and decide about `comment.mention`
    carrying a `ticketId`).
  - Add `status` and `assignee` to the payload if the cards should show them.
- **Client today.**
  - Core parses `ticket.update` only (`TicketUpdate.from`), which matches the spec and the
    reference app. On today's server that means no message is parsed as a ticket card, and those
    messages render as ordinary bot text.
  - Today's ticket row shows the ticket number and title when a payload parses, and otherwise
    falls back to "Ticket update".
  - Changing the matched type is a behaviour change from the reference app, so it waits for the
    decision above.

## 5. Separate time-off summary

From `feature-profile.md` §5.

- **Evidence.** `dto/Response/EmployeeTimeOffResponseDto.cs` repeats the balance on every request
  row: `AllocatedLeaveCount`, `RemainingLeaveCount`, `UsedLeavePercentage`, `RemainingLeaveDays`,
  `UsedLeaveDays`, the hour fields and the vacation and sick splits. There is no separate summary
  object, so a person with no requests has no balance.
- **Ask.** A summary shape (for example `{ balance, requests }`) or a balance endpoint.
- **Client today.** Core reads the balance from the first row (`TimeOffSummary.balance`) and
  prefers the server's `UsedLeavePercentage`.

## 6. Per-conversation mute

From the `chat-list.md` contract notes (spec 11, drift 56).

- **Evidence.** No chat controller, model or DTO has a mute flag. The only "mute" endpoints are
  video host controls (`Controllers/VideoController.cs` `host/mute`).
- **Ask.** Only if product wants muting: a participant-level `isMuted` with a toggle endpoint, and
  push suppression on the server.
- **Client today.** The list shows no muted indicator.

## 7. Read positions for receipts (only if receipts are wanted)

From `conversation.md` §4.

- **Evidence.**
  - The hub broadcasts `ReceiveReadReceipt(conversationId, userId, at)`, sent from
    `Controllers/Chat/ChatController.cs` lines 1348 and 1749.
  - The conversation DTOs only carry the caller's own `LastReadAt`. Nothing returns other
    participants' read positions for a cold start.
- **Ask.** If "Seen by" is wanted, return each participant's last-read message id (or time) with
  the conversation or messages.
- **Client today.** No receipts. The Expo reference never shows them either (spec 05 §6).

## Checked and not a gap

`MeetingHandoff` `relatedChatConversationId`:

- `dto/Request/VideoDtoObjects.cs` declares `RelatedChatConversationId` as `int?`.
- The Android `Long` therefore matches the API. The string in spec 02 describes the web lobby's
  own state, not the API.
