# Data layer

How `core:network`, `core:realtime`, `core:data`, `core:demo` and `core:testing` fit together, what
they expose to feature modules, and what the `app` module has to wire. The contract itself lives in
`spec/contract/` (REST, hub, push, dates, gaps); this page covers the Android side only.

```
feature/*  ──▶  core:data repositories (interfaces)  ◀── core:demo (debug only)
                   │            │
                   ▼            ▼
            core:network   core:realtime        Room cache + Tink DataStore + WorkManager outbox
           (Retrofit DTOs) (SignalR client)
```

Features depend on the interfaces in `com.waivera.waivera.core.data.repository` and on the models in
`com.waivera.waivera.core.data.model`. They never see DTOs, DAOs, Retrofit or SignalR types.

## Rules every repository follows

- Suspend functions return `Result<T>`. The failure is always a `WaiveraError`
  (`core:network/error`), which carries a string resource for the user. Raw server text and stack
  traces never reach the UI.
- Flows never throw. Lists and screens are exposed as `Flow<Synced<T>>`:
  - `data`: what the cache holds now (possibly empty);
  - `status`: `Idle`, `Refreshing` or `Failed(error)`;
  - `lastSyncedAt`: when the server last answered, or null if never.
  - Helpers: `isInitialLoad` (show a skeleton), `isRefreshing`, `error`, and `isEmpty` for
    collections. A ViewModel maps these to loading, empty, error and content states.
- The server is authoritative. Screens render from the Room cache; every refresh writes to the cache
  and the flows re-emit.
- "Is this me" uses `CurrentUser.userId` (the backend id) only. The Firebase uid is used for auth and
  never compared with message authors.
- Timestamps are `Instant`s parsed by one shared UTC parser (`UtcInstant`, `LenientSerializers`);
  zone-less chat timestamps are read as UTC. Schedule values are shown in
  `CalendarRepository.zone` (the employee's saved zone, falling back to `America/Chicago`), never the
  device zone.

## Public interfaces

All in `core/data/.../repository/Repositories.kt`.

| Interface | What it gives features | Notes |
| --- | --- | --- |
| `SessionRepository` | `state: StateFlow<SessionState>`, `signIn`, `signOut`, `sendPasswordReset`, `refresh`, `changePassword` | `SessionState` is `Initializing`, `SignedOut(reason)`, `ConfigurationMissing(keys)`, `BootstrapFailed(error)`, `PasswordChangeRequired(user)` or `SignedIn(user, isOffline)`. Offline launches reuse the cached session. |
| `ConversationRepository` | inbox, one conversation, unread total, refresh, `openConversation(participantIds)`, `conversationForEvent`, `leave`, `markRead`, `observeTyping`, `onComposerChanged` | Typing: outgoing throttled to one hub event per 3 s, incoming expires after 5 s. |
| `MessageRepository` | `observeTimeline`, `refreshLatest`, `loadOlder`, `loadAround`, `send` (returns the client id at once), `retry`, `discard`, `edit`, `delete`, `react` | Collecting a timeline joins the hub group and resyncs its tail after each reconnect. Failed sends stay in the timeline with `SendState.FAILED` until retried or discarded. |
| `ThreadRepository` | `observeThread`, `refresh`, `markRead` | Same actions as the main timeline go through `MessageRepository` with `parentMessageId`. |
| `AttachmentRepository` | `download(messageId, fileName): File`, `resolve(url)` | Bounded 100 MB cache, cleared on sign-out. |
| `BookmarkRepository` | bookmarks, bookmarked ids, refresh, `toggle` | `Bookmark.threadRootMessageId` is the navigation target for replies. |
| `ActivityRepository` | feed, refresh, `loadMore`, `markRead`, `markAllRead` | Page size 30. |
| `PeopleRepository` | staff directory, one person, `avatar(userId): ByteArray?` | Avatars cached 30 min; misses cached 5 min. |
| `CalendarRepository` | `zone`, `observeRange(DateRange)`, `refresh`, milestones, `respondToInvitation`, `meetingLobbyUrl` | `observeRange` refreshes on first collection when older than one minute. |
| `ProfileRepository` | my profile, another profile, time off, files (pages of 20), `updateContact` | |
| `SettingsRepository` | `settings: Flow<AppSettings>` plus setters for theme, haptics, notifications, categories, sound, vibration, previews | Category changes re-register push (debounced 500 ms). |
| `PushRegistrationRepository` | `register(token, preferences)`, `unregister`, `onNewToken` | Registers `provider: "fcm"`; skipped when the SHA-256 fingerprint of the request is unchanged. |
| `RealtimeStatusRepository` | `status: StateFlow<RealtimeStatus>`, `retryNow`, `setVisibleConversation`, `shouldSuppressNotification` | Suppress a push only when the hub is connected and that conversation is on screen. |
| `DataLayerStarter` | `start()` | Call once from `Application.onCreate`. |

### Models

`core/data/.../model/`:

- `Session.kt`: `CurrentUser` (with `zone`), `SessionState`, extensions `currentUserId` and
  `currentUser`.
- `Chat.kt`:
  - conversations: `Person`, `ConversationKind` (`DIRECT`, `GROUP`, `CHANNEL`, `BOT`),
    `ChannelInfo`, `MeetingInfo`, `OngoingCall`, `MessagePreview`, `Conversation` (`canWrite`,
    `title`, `others`);
  - messages: `Message` (`isMine`, `isPending`, `isThreadReply`, `navigationRootId`), `Attachment`,
    `Reaction`, `SendState`, `AttachmentSource`;
  - collections: `MessageTimeline`, `MessageThread`, `ActivityItem`, `ActivityFeed`, `Bookmark`.
- `Schedule.kt`: `DateRange` (half-open), `ScheduleEvent`, `MyInvitation`, `Attendee`,
  `TimeOffEntry`, `Holiday`, `ScheduleFeed`, `Milestone`, `MilestoneOccurrence`.
- `Profile.kt`: `EmployeeProfile`, `TimeOffRequest`, `TimeOffBalance`, `TimeOffSummary`,
  `EmployeeFile`, `EmployeeFilePage`.
- `Settings.kt`: `ThemePreference`, `NotificationCategory` (`messages`, `group_activity`,
  `schedule_reminders`, `schedule_changes`, `digest`), `NotificationPreferences`, `AppSettings`,
  `RealtimeStatus`.

Calendar maths for screens is in `CalendarMath` (`core/data/.../calendar/`): week and month-grid
ranges (weeks start Sunday), `occursOn`/`eventsOn` per day in a zone, milestone resolution (Feb 29
falls on Feb 28 in common years), `joinWindowOpen` (30 minutes before start) and `canRespond`.

### Realtime

`core:realtime` exposes `RealtimeClient`:

- `state: StateFlow<ConnectionState>`: `Disconnected`, `Connecting`, `Connected` or
  `Reconnecting(attempt, delayMillis)`.
- `events: SharedFlow<HubEvent>`: one sealed type for all 17 chat hub events.
- `connectionEpoch`, `resyncRequests`: the epoch goes up on every successful connect;
  `resyncRequests` emits on every reconnect after the first.
- `attendConversation(id): AutoCloseable`: ref-counted group membership that is rejoined after every
  reconnect.
- Hub calls: `sendMessage`, `sendTypingStatus`, `markAsRead`, `sendReaction`.

Reconnect never gives up: backoff is capped, `retryNow()` skips the wait, and a failed handshake
force-refreshes the token on the next attempt. The connection runs only while signed in and the app
is in the foreground. Features normally use the repositories, not the client directly; the
connection banner reads `RealtimeStatusRepository.status`.

## DI bindings and qualifiers

| Module | Installed | Binds |
| --- | --- | --- |
| `NetworkModule` (core:network) | automatically | `NetworkConfig`, `Json`, `@AuthenticatedHttpClient OkHttpClient`, `Retrofit` and every `*Api` |
| `CoroutinesModule` (core:network) | automatically | `@ApplicationScope CoroutineScope` (SupervisorJob, Default), `@IoDispatcher CoroutineDispatcher` |
| `RealtimeModule` (core:realtime) | automatically | `HubTransportFactory` → SignalR, `ReconnectPolicy` |
| `DataInfrastructureModule` (core:data) | automatically | `SecureStore` (Tink AES-256-GCM over DataStore), `SettingsStore`, `ConnectivityMonitor`, `AppForegroundMonitor`, `AuthGateway` → Firebase, `OutboxScheduler` → WorkManager, `Clock`, Room database and DAOs, `TransactionRunner`, `@OutboxDirectory File`, `@AttachmentCacheDirectory File`, the `Set<SignOutCleaner>` multibinding |
| `LiveRepositoriesModule` (core:data) | **not installed** (`@DisableInstallInCheck`) | every repository interface → its `Live*` class, `RealtimeClient` → `DefaultRealtimeClient`, `AuthTokenProvider` → `GatewayTokenProvider`, `DataLayerStarter` → `LiveDataLayerStarter` |
| `DemoSwitchModule` (core:demo) | automatically, debug builds only | the same interfaces as `LiveRepositoriesModule`, choosing live or demo per process; `DemoBackend` |

Qualifiers: `@AuthenticatedHttpClient`, `@ApplicationScope`, `@IoDispatcher`, `@OutboxDirectory` and
`@AttachmentCacheDirectory`. Most `Live*` classes are `@Singleton`.

`OutboxWorker` takes its dependencies from a Hilt `EntryPoint`, so the app does not need
`hilt-work` or a custom `WorkerFactory`.

## Demo mode

`core:demo` holds the demo repositories (`Demo*`), `DemoBackend` (shared in-memory state with a fixed
clock at 2026-11-01 16:30 UTC), `DemoRealtimeClient`, the scripted `DemoHub` and the fixtures in
`src/main/resources/demo-fixtures/`.

The demo covers every kind of conversation, unread badges, an ongoing group call, reactions, call
logs, a bot ticket update, attachments, a thread, a failed send, an all-day event today, an
overnight event across the 1 November DST change, a pending invitation, and milestones today and
tomorrow. A message whose text contains "fail" fails to send, and Retry succeeds. After start the
hub script has Priya type, send a message that mentions Alex, then react to one of Alex's messages.
Sign-out works; any email with the password `demo` signs back in.

`DemoMode` decides once per process. Demo is on when any of these is true:

1. the build used `-Pwaivera.demoMode=true` (`BuildConfig.DEMO_MODE` in core:demo);
2. a launch intent carried the extra `waivera.fake=true`. The value is remembered in private
   preferences until a launch with `waivera.fake=false`;
3. the API base URL or Firebase keys are missing, so the live app could not work.

`DemoSwitchModule` uses `Provider`s, so in demo mode no database, socket or Firebase app is created.
In live mode no demo object is created.

Screenshot and Compose tests do not need Hilt: `DemoGraph(scope)` builds every demo repository
around one `DemoBackend`. `core:testing` re-exports `core:demo` (`api`).

## What the app module must do

1. Dependencies:
   ```kotlin
   implementation(projects.core.data)
   debugImplementation(projects.core.demo)
   ```
2. Release builds: add a module in `app/src/release/` that includes `LiveRepositoriesModule`.
   Debug builds must not have it, or Hilt reports duplicate bindings.
   ```kotlin
   @Module(includes = [LiveRepositoriesModule::class])
   @InstallIn(SingletonComponent::class)
   object ReleaseDataModule
   ```
3. Demo launch flag: in `MainActivity.onCreate`, before `super.onCreate`, call a debug-only hook
   that runs `if (DemoMode.applyLaunchIntent(this, intent)) return`. Release has no `DemoMode`
   class, so put the hook in `src/debug` and a no-op in `src/release`. A changed flag restarts the
   process, because repositories are chosen once per process.
4. In `Application.onCreate` (an `@HiltAndroidApp` class), call `dataLayerStarter.start()` once.
   This starts the realtime lifecycle, hub event sync, reconnect resync, outbox kicks on connectivity
   changes and push re-registration.
5. Images: install the authenticated loader so attachment and avatar URLs send the auth header
   (G-15):
   `SingletonImageLoader.setSafe { authenticatedImageLoader.imageLoader }`.
6. Push (core:notifications):
   - `FirebaseMessagingService.onNewToken` → `PushRegistrationRepository.onNewToken(token)`;
   - before showing a chat notification, check
     `RealtimeStatusRepository.shouldSuppressNotification(conversationId)`.
7. Conversation and thread screens call
   `RealtimeStatusRepository.setVisibleConversation(id)` on resume and `null` on pause.
8. Build values come from `local.properties` or CI secrets: `waivera.apiBaseUrl`,
   `waivera.webBaseUrl` and `waivera.firebase.apiKey`, `.appId`, `.projectId`, `.senderId`.
   Nothing is committed.

Other modules should not inject `AuthGateway`, DAOs or `Live*` classes directly. In demo mode those
would build the live stack.

## Contract gaps each repository works around

From `spec/contract/gaps.md`.

| Gap | Where | What the client does |
| --- | --- | --- |
| G-01 reactions have no REST endpoint | `LiveMessageRepository.react` | Optimistic update sent over the hub only; rolled back when the hub is down or rejects it. |
| G-02 login is enveloped | `LiveSessionRepository`, `AuthenticationApi` | Reads the envelope's `data`, not the top level. |
| G-03 two casings, int and string enums | `core:network` serialization | Case-insensitive names, lenient enums with `UNKNOWN`, string-or-int booleans; hub payloads normalised to camelCase. |
| G-04 bookmarks 500 on bot rows; leaked entities | `LiveBookmarkRepository` | On 500 falls back to per-conversation bookmark searches (4 at a time). |
| G-05 `lastMessage` loses fields | `ConversationMerge` | Merges `/my-conversations` and `/all` field by field and never replaces a richer preview with null. |
| G-06 pagination has no envelope or limit | `LiveMessageRepository.loadOlder` | Fixed page size; stops on a short page or when the oldest id does not move back. |
| G-07 only chat pushes reach employees | `RealtimeSync`, `LiveDataLayerStarter` | Ignores the web notification feed events. Chat state resyncs over REST on every reconnect and resume instead of relying on push. |
| G-08 preferences are write-only, tied to registration | `LivePushRegistrationRepository` | Sends the full preference set every time, skips unchanged requests by fingerprint, debounces, and filters categories on the device. |
| G-09 attention policy ignored by the reference client | `HubEvent.ChatAttention` | Decoded and available for notification decisions. |
| G-10 call-state vocabulary | `LiveConversationRepository.onCallState` | Only `END` clears an ongoing call. |
| G-11 deleted messages are inconsistent | `Mappers.message` | Drops body, doc and attachment when `isDeleted`. |
| G-12 six error shapes; 500 leaks `ex.Message` | `ErrorMapper` | Maps status and known messages to typed `WaiveraError`s; server text is never shown. |
| G-13 `includeArchived` default differs | `ChatApi`, `LiveConversationRepository` | Passes every parameter explicitly; does not read `activeMeeting` from `/all`. |
| G-14 `unreadCount` missing on one endpoint | `ConversationMerge.refresh(keepUnread)` | Keeps the cached unread count when refreshing a single conversation. |
| G-15 attachments need auth; undocumented 423 | `AuthenticatedImageLoader`, `LiveAttachmentRepository` | Authenticated OkHttp client for images and downloads; 423 becomes `AttachmentRejected`. |
| G-16 `IdentityController` unauthenticated | none | The app does not call it. |
| Client id echo (needs API change) | `MessageIngest` | Reconciles optimistic sends by `clientMessageId` when echoed. Otherwise it matches author, conversation, parent, trimmed text and a 2-minute window. |
| FCM provider (needs API change) | `LivePushRegistrationRepository` | Registers `provider: "fcm"`. `/api/mobile-push/register` currently expects `expo`. |

## Known limits

- Read receipts (`ReadReceipt` hub events) are decoded but not stored or shown yet.
- `loadAround` can leave a gap between the jumped-to page and the latest page. The gap is closed when
  the user scrolls or the latest page is refetched.
- The video hub (`api/videoHub`) contract and decoder exist, but nothing connects to it yet.
- Recurring events are shown as the server returns them; there is no client-side RRULE expansion.
