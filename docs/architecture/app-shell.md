# App shell

How `:app` wires the auth gate, tabs, routes, deep links and notifications. Feature agents read
this before touching navigation. The data layer is described in `data-layer.md` (owned by the
core agent).

## Modules

| Module | Owns |
| --- | --- |
| `:app` | `MainActivity`, `AppShellViewModel`, routes, `NavState`, the auth gate, the account menu, Firebase start-up, the Coil image loader, `DataLayerStarter.start()`, the release-only `ReleaseDataModule`, adapters from `:core:notifications` ports to `:core:data` |
| `:core:designsystem` | Tokens, `WaiveraTheme`, `WaiveraMotion`, `Haptics`, every shared component |
| `:core:notifications` | FCM service, channels, payload parsing, `DeepLinkTarget` and `waivera://` URIs, chat notifications, Reply and Mark read, the permission primer state, the debug push receiver |
| `:core:screenshot-testing` | `captureScreenshots` and the device/theme/font matrix |
| `:feature:*` | One public entry composable per screen (below). Features never depend on each other or on `:app`. |

## Gate

`AppGate.from(SessionState, primerPending)` decides what shows before the tabs:

| Session | Gate | Screen |
| --- | --- | --- |
| `Initializing` | `Loading` | `WaiveraLoadingScreen` |
| `SignedOut` | `SignIn()` | `SignInScreen(configurationMissing = false)` |
| `ConfigurationMissing` | `SignIn(configurationMissing = true)` | `SignInScreen` offers demo mode |
| `BootstrapFailed` | `BootstrapFailed` | Error state with Try again (`SessionRepository.refresh`) |
| `PasswordChangeRequired` | `PasswordChange` | `PasswordChangeScreen` |
| `SignedIn`, primer pending | `NotificationPrimer` | `NotificationPrimerScreen` (Android 13+, once) |
| `SignedIn` | `Main` | Tabs |

Deep links that arrive before `Main` wait and open when the tabs appear. Signing out discards a
waiting link and every back stack, and cancels all notifications.

## Tabs and back

Tabs are Today, Chat and Schedule, each with its own back stack in `NavState`. The stack given
to `NavDisplay` is Today's stack, plus the selected tab's stack when it is not Today, so:

- back inside a tab pops that tab;
- back at the root of Chat or Schedule returns to Today, with a predictive back preview;
- back at Today's root leaves the app (the system handles it);
- selecting the current tab again pops it to its root.

The bottom bar shows only on the three tab roots. Account-menu screens (Profile, People,
Bookmarks, Settings) push onto the current tab. Opening a conversation, person or event from
any screen pushes onto the current tab, so back returns to where the person was.

Navigation survives process death through `SavedStateHandle` (the `NavState` as JSON and a
waiting deep link as its `waivera://` URI).

## Routes

All routes are `@Serializable` and implement `AppRoute : NavKey`. Arguments are ids only.

| Route | Arguments | Entry composable | Module |
| --- | --- | --- | --- |
| `TodayRoute` | none | `TodayScreen(onAccountClick, onOpenConversation, onOpenSchedule, onOpenEvent, modifier)` | `:feature:today` |
| `ChatListRoute` | none | `ChatListScreen(onAccountClick, onOpenConversation, onOpenPerson, modifier)` | `:feature:chat` |
| `ConversationRoute` | `conversationId: Long`, `messageId: Long?` | `ConversationScreen(conversationId, highlightMessageId, onBack, onOpenThread, onOpenPerson, onOpenEvent, modifier)` | `:feature:chat` |
| `ThreadRoute` | `conversationId: Long`, `rootMessageId: Long`, `messageId: Long?` | `ThreadScreen(conversationId, rootMessageId, highlightMessageId, onBack, onOpenPerson, modifier)` | `:feature:chat` |
| `ScheduleRoute` | none | `ScheduleScreen(onAccountClick, onOpenEvent, modifier)` | `:feature:calendar` |
| `CalendarEventRoute` | `eventId: String` | `CalendarEventScreen(eventId, onBack, onOpenConversation, onOpenPerson, modifier)` | `:feature:calendar` |
| `ProfileRoute` | none | `ProfileScreen(onBack, onOpenPerson, modifier)` | `:feature:profile` |
| `PeopleRoute` | none | `PeopleScreen(onBack, onOpenPerson, modifier)` | `:feature:profile` |
| `PersonRoute` | `userId: String` (backend id) | `PersonDetailScreen(userId, onBack, onOpenConversation, modifier)` | `:feature:profile` |
| `BookmarksRoute` | none | `BookmarksScreen(onBack, onOpenMessage(conversationId, messageId, threadRootMessageId), modifier)` | `:feature:profile` |
| `SettingsRoute` | none | `SettingsScreen(onBack, onOpenNotificationSettings, onOpenAppearance, modifier)` | `:feature:settings` |
| `NotificationSettingsRoute` | none | `NotificationSettingsScreen(onBack, modifier)` | `:feature:settings` |
| `AppearanceRoute` | none | `AppearanceScreen(onBack, modifier)` | `:feature:settings` |

Gate screens (not routes): `SignInScreen(configurationMissing, modifier)`,
`PasswordChangeScreen(onSignOut, modifier)` and `NotificationPrimerScreen(onFinished(granted), modifier)`,
all in `:feature:auth`.

Rules for feature agents:

- Keep entry signatures as listed; add parameters only with a default, and update this table and
  `RouteContent` in the same change.
- Screens get their own ViewModel with `hiltViewModel()`. Each `NavEntry` has its own
  ViewModel store and saved state, keyed by tab, position and route (`ShellEntry.key`).
  The key ignores `messageId`, so a push that only moves the highlight keeps scroll and draft.
- Sign-out lives in Settings and calls `SessionRepository.signOut()`; the gate reacts.
- A bookmark of a thread reply opens `ThreadRoute` at the reply.

## Deep links

`DeepLinkTarget` (in `:core:notifications`) is the single list of places a push or link can open.
`DeepLinkRouting.stackFor` maps each one to a tab and a full stack, so back walks up through the
parents:

| `waivera://` URI | Target | Stack |
| --- | --- | --- |
| `waivera://today` | `Today` | Today |
| `waivera://chats` | `Inbox` | Today ▸ Chat list |
| `waivera://chats/{id}?messageId={m}` | `Conversation` | Today ▸ Chat list ▸ Conversation |
| `waivera://chats/{id}/thread/{root}?messageId={m}` | `Thread` | Today ▸ Chat list ▸ Conversation ▸ Thread |
| `waivera://schedule` (or `waivera://calendar`) | `Schedule` | Today ▸ Schedule |
| `waivera://calendar/{eventId}` | `CalendarEvent` | Today ▸ Schedule ▸ Event |
| `waivera://profile` | `Profile` | Today ▸ Profile |
| `waivera://people` | `People` | Today ▸ People |
| `waivera://people/{userId}` | `Person` | Today ▸ People ▸ Person |
| `waivera://bookmarks` | `Bookmarks` | Today ▸ Bookmarks |
| `waivera://settings` | `Settings` | Today ▸ Settings |
| `waivera://settings/notifications` | `NotificationSettings` | Today ▸ Settings ▸ Notifications |

If the target is already on top, nothing changes; a newer `messageId` only moves the highlight.
Ids are validated by `DeepLinkUris` (positive numbers, or opaque ids of at most 128 safe
characters); anything else is ignored. The `waivera` intent filter is not `BROWSABLE`.

Push payloads map to targets in `PushPayloadParser` (chat to `Conversation` or `Thread`, meeting
to `CalendarEvent`, call to `Conversation`, survey to `Today`, unknown types to `Inbox`).

Try one on an emulator:

```bash
adb shell am start -a android.intent.action.VIEW -d "waivera://chats/42?messageId=9001" com.waivera.waivera
adb shell am broadcast -a com.waivera.waivera.DEBUG_PUSH -p com.waivera.waivera \
  --es payload "$(cat docs/push-test-payloads/chat-message.json)"
```

## Visible conversation and push suppression

The shell, not the chat feature, reports what is on screen: when the top route is a
`ConversationRoute` or `ThreadRoute` and the activity is resumed, `AppShellViewModel` calls
`ActiveConversationTracker.enter` (which also clears that conversation's notifications) and
`RealtimeStatusRepository.setVisibleConversation`. Leaving, pausing or signing out clears both.
Chat screens must not call either themselves.

A chat push is suppressed only when the hub is connected and that exact conversation (and
thread, for thread replies) is on screen: `PushMessageHandler` asks
`RealtimeStatusRepository.shouldSuppressNotification` (through `LiveConversationMonitor`) and
the tracker for the thread. Everything else shows a notification.

## Insets

The app is edge-to-edge. `WaiveraTopBar` owns the status bar and cutout insets and
`WaiveraNavigationBar` owns the navigation bar inset, so a tab root pads nothing itself. Screens
without the bottom bar (conversation, thread, detail screens) pad for the navigation bar and the
IME themselves, usually on the composer or the last list item, never on the whole screen. Full
screens without a top bar (primer, bootstrap error) use `safeDrawingPadding()`.

## Demo mode

There is one demo switch, `:core:demo`'s `DemoMode` (see `data-layer.md`). Debug builds switch
to demo data with `-Pwaivera.demoMode=true` at build time or
`adb shell am start -S -n com.waivera.waivera/.MainActivity --ez waivera.fake true` (and back
with `--ez waivera.fake false`). `MainActivity` hands the launch intent to
`DemoLaunch.restartIfChanged` before `super.onCreate`; the debug copy calls
`DemoMode.applyLaunchIntent`, which remembers the choice and restarts the process when it
changed. Debug builds also fall back to demo data when the API URL or Firebase values are
missing. Release builds have no `:core:demo`: their `DemoLaunch` ignores the extra and
`ReleaseDataModule` (`app/src/release`) includes `LiveRepositoriesModule`.

## Firebase

`FirebaseInitializer` is the only place Firebase is started: in `Application.onCreate`, before
Hilt, guarded by `FirebaseApp.getApps`. It reads `:core:data`'s `FirebaseConfig`, which holds
the `waivera.firebase.apiKey`, `.appId`, `.projectId` and `.senderId` values from Gradle
properties or `local.properties`; `:core:data` never initialises Firebase itself. There is no
`google-services.json` and no Google services plugin. Without the values the app still starts,
the session reports `ConfigurationMissing` and push registration is skipped.
