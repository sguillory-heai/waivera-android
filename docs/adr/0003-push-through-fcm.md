# 0003: Deliver pushes through FCM HTTP v1, data-only on Android

Status: Accepted (2026-10-27)

## Context

The Expo app registers Expo push tokens and sprout_api relays through Expo's push service.
Native apps cannot receive Expo tokens. sprout_api already sends some pushes through FCM HTTP v1.

## Decision

- The app registers its FCM token at `POST /api/mobile-push/register` with `provider: "fcm"`
  (sprout_api PR #881).
- sprout_api sends Android chat pushes as data-only, high-priority FCM messages carrying `type`,
  `conversationId`, `messageId`, `url`, `title`, `body` and `channelId`. The app always builds
  the notification itself (MessagingStyle, inline reply), so it looks the same in every app state.
- The app creates the channels sprout_api names: `chat-messages`, `chat-messages-silent`,
  `chat-messages-sound-only` and `chat-messages-quiet`, plus one channel per future push kind.
- A high-priority message must always produce a visible notification, or FCM deprioritizes the app.

## Consequences

- If the app is force-stopped, Android delivers nothing until the next launch; this is accepted.
- New push kinds need a sprout_api change and a channel here in the same ticket.
