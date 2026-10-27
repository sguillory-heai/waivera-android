# Push test payloads

Payloads for simulating pushes on a debug build without Firebase. Shapes follow
`spec/contract/push.md`. Each file is either the FCM `data` map the Android app receives or a
whole FCM request body; for the latter the receiver reads `message.data` and
`message.notification`, exactly as `onMessageReceived` would see them.

```bash
# The app must be installed as a debug build. -p is required on Android 8+ for manifest receivers.
adb shell am broadcast -a com.waivera.waivera.DEBUG_PUSH -p com.waivera.waivera \
  --es payload "$(cat docs/push-test-payloads/chat-message.json)"
```

| File | What it checks |
| --- | --- |
| `chat-message.json` | New chat message in conversation 7; MessagingStyle with Reply and Mark read |
| `chat-message-second.json` | Second message in conversation 7; updates the same notification |
| `chat-message-thread-reply.json` | Thread reply; tap opens the thread with the reply highlighted |
| `chat-message-attachment-only.json` | Attachment-only body ("Sent an attachment") |
| `chat-message-no-preview.json` | `previewContentEnabled = false` body ("New message") |
| `legacy-meeting.json` | Legacy participant meeting push; Schedule changes channel, opens the event |
| `legacy-call.json` | Legacy participant call push; opens the conversation |
| `legacy-survey.json` | Legacy participant survey push; Digest channel, opens Today |
| `unknown-type.json` | Unknown `type`; Digest channel, opens the inbox |

Send the same file twice to check de-duplication by message id. Open conversation 7 with the
hub connected and send `chat-message.json` to check foreground suppression.
The same files are copied into `core/notifications/src/test/resources/push/` for unit tests.
