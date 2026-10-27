# 0004: Notification channels by meaning

Status: Accepted (2026-10-27). Supersedes the channel bullet of [0003](0003-push-through-fcm.md).

## Context

0003 copied the Expo app's channels (`chat-messages`, `-silent`, `-sound-only`, `-quiet`), which
encode a sound setting rather than what the notification is about. People cannot turn off group
chatter while keeping direct messages, and the sound variants duplicate what Android's own
channel settings already offer. The UX research and PLAN.md group notifications by meaning.

## Decision

- Android creates five channels: `messages` (direct messages and mentions, high importance),
  `group_activity` (default), `schedule_reminders` (high), `schedule_changes` (default) and
  `digest` (low). iOS uses the same ids as notification categories.
- The app picks the channel from the payload type and the cached conversation, ignoring any
  `channelId` the server sends. Data-only pushes are always built by the app.
- `messages` is the manifest default, so a push with a notification block and no known channel
  still lands somewhere the person can control.
- The four Expo-era channels are deleted on first launch so they do not linger in Settings.
- The in-app notification settings map one-to-one to these channels and link to the system page
  for each.

## Consequences

- sprout_api does not have to change: the `channelId` it sends is no longer read on Android.
- DM versus group is decided from the local cache, because the payload does not say (gap logged
  in the push contract); an uncached conversation goes to `messages`.
- A new kind of notification picks one of these five channels or adds a channel here, with a new
  record if the meaning model changes.
