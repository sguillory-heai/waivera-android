# Waivera Android

Native Jetpack Compose rebuild of the Waivera mobile app (the Expo app in `waivera-mobile` is the behavior reference). The app gives staff realtime, on-the-go chat, push notifications, and schedule tracking backed by `sprout_api`. Later phases add lightweight task updates.

This file is the single source of truth for every coding agent (Claude Code, Codex, Copilot, Gemini in Android Studio). `CLAUDE.md` imports it. Keep it under 200 lines; move procedures into skills under `.claude/skills/` and file-specific rules into `.claude/rules/`.

## Stack

- Kotlin 2.x (K2), Jetpack Compose with Material 3, single-activity, edge-to-edge.
- minSdk 26, targetSdk/compileSdk latest stable. Package `com.waivera.waivera`.
- Architecture: UI (Compose + ViewModel) → domain (use cases, only where logic is shared) → data (repositories). Unidirectional data flow.
- State: ViewModels expose one `StateFlow<UiState>`; one-off effects via `Channel`/`SharedFlow`. Collect with `collectAsStateWithLifecycle()`.
- DI: Hilt. Async: coroutines + Flow, structured concurrency, no `GlobalScope`.
- Networking: Retrofit + OkHttp + kotlinx.serialization. Realtime: Microsoft SignalR Java client wrapped in a Flow-based `RealtimeClient`.
- Push: Firebase Cloud Messaging. Auth: Firebase Auth; tokens in encrypted DataStore, never plain `SharedPreferences`.
- Local data: Room for offline caches; the server is authoritative. Images: Coil 3.
- Navigation: Navigation 3 with type-safe routes and deep links for every push target.
- Build: Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), convention plugins in `build-logic/`. Adding a dependency needs a stated reason in the PR.

## Layout

```
app/                     application, MainActivity, nav graph, Hilt entry points
core/network/            Retrofit services, DTOs (mirror sprout_api), error mapping
core/realtime/           SignalR connection, reconnect/backoff, event flows
core/designsystem/       theme, tokens, typography, motion, haptics, shared components
core/data/               repositories, Room, DataStore
core/testing/            fakes, fixtures, test rules, screenshot helpers
feature/chat, feature/calendar, feature/profile, feature/settings
build-logic/             convention plugins
```

Feature modules depend on `core:*`, never on each other. Cross-feature navigation goes through routes defined in `app`.

## Commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest                       # all unit tests
./gradlew :feature:chat:testDebugUnitTest         # one module, run this first
./gradlew verifyRoborazziDebug                    # screenshot tests
./gradlew connectedDebugAndroidTest               # instrumented, needs emulator
./gradlew ktlintCheck detekt lintDebug
# Simulate a push on the emulator (debug builds expose a test receiver)
adb shell am broadcast -a com.waivera.waivera.DEBUG_PUSH -p com.waivera.waivera --es payload "$(cat docs/push-test-payloads/chat-message.json)"
```

Run the narrowest module task first, then the full checks before pushing.

## Code standards

- Follow the official Kotlin coding conventions and the Android Kotlin style guide; ktlint and detekt enforce them.
- Composables: stateless by default, state hoisted, `modifier: Modifier = Modifier` as the first optional parameter and applied to the root.
- Screen-level composables take the ViewModel; everything below takes plain state and lambdas so it is previewable and testable.
- Every reusable composable has `@Preview`s for light, dark, and large font scale.
- No heavy work in composition. Use `remember`/`derivedStateOf`, stable keys in lazy lists, and `@Immutable`/`@Stable` UI models.
- No `!!`, no `runBlocking` outside tests, no `lateinit` for injectable state.
- Errors surface as a sealed `WaiveraError` with a user-facing message; never show raw server text or stack traces.
- Strings in `strings.xml`, never hard-coded in composables. Comments explain why, not what. No commented-out code.

## API contract (sprout_api)

- `sprout_api` is the contract owner. DTOs in `core/network` mirror its `dto/` types by name. When a field is unclear, read the C# DTO; do not guess from the Expo app.
- Push registration currently posts `provider: "expo"` to `/api/mobile-push/register`. Native builds register FCM tokens and need a matching API change; coordinate it in the same ticket.
- Configure serialization with `ignoreUnknownKeys = true` and map unknown enum values to an `UNKNOWN` entry.
- Never hard-code environment URLs, keys, or tenant IDs. Use build types/flavors and `local.properties` or CI secrets; secrets stay out of git.

## Dates and time zones

- Read `sprout_api/docs/timezone-standard.md` before any date work. Use `java.time` (`Instant`, `ZonedDateTime`, `ZoneId`) only.
- Treat server timestamps as UTC instants. Some chat endpoints return UTC without a zone suffix; parse those as UTC explicitly.
- Present schedules and calendar items in the employee's saved time zone, not the device zone. Use named zones, never fixed offsets.
- Every date/time change adds or extends a test that covers a DST boundary.

## Design, motion, and feedback

- Use `core/designsystem` tokens only. Primary purple `#7E57C2` (dark mode accent `#A78BFA`). No inline colors or magic dp values.
- Support font scale up to 200%, TalkBack content descriptions on every control, and 48×48dp minimum touch targets.
- Motion: shared `WaiveraMotion` specs (spring-based), shared-element and predictive-back transitions where natural. Respect the system animator scale and "remove animations" setting.
- Haptics through the shared `Haptics` helper at meaningful moments only: send failures (a successful send does not vibrate), receive while visible, errors, toggles.
- Optimistic UI for sends and status changes, with visible retry on failure. Show skeletons for loads over ~300ms; no blocking spinners over content.
- Confirmations use the shared styled confirmation bottom sheet/dialog from the design system, never a raw `AlertDialog`.
- Check light, dark, a small phone, a large phone, and 200% font scale before calling UI done.

## Performance

- Ship a Baseline Profile for startup and the chat list; keep a Macrobenchmark module for cold start and chat scroll.
- Watch for recomposition storms in chat and calendar lists (Layout Inspector recomposition counts) before merging list changes.
- R8 full mode in release; keep rules minimal and documented.

## Testing

- JUnit + Turbine + coroutines-test for ViewModels and repositories, using `core/testing` fakes (no mocking frameworks for our own classes).
- Every ViewModel has tests for loading, empty, error, and reconnect states.
- Roborazzi screenshot tests for design-system components and key screens in light/dark and large font.
- Compose UI tests for critical flows (sign in, open chat from a notification, send a message) against the fake server, never shared dev data.
- Never skip, `@Ignore`, or loosen a test to get green. Fix the code or explain why the test is wrong.

## Git and delivery

- Until the first production release, work on `master`; pushing directly to `master` is allowed while there are no production users. After a live release, switch to the sprout_api convention (branch from `dev`, no direct pushes) and update this line. Commit messages start with the ticket ID: `[WAIV-2527] Add schedule list skeleton`. Use the same prefix across repos for one ticket.
- Never credit an AI tool anywhere in the repo: no `Co-Authored-By` or session trailers in commits, no "Generated with" footers or AI links in PR descriptions, comments, or code. Commits and PRs are authored by the person who owns the work.
- One logical change per PR. The PR description says what changed, how it was verified (commands, emulator, screenshots for UI), and any API dependency.
- Ticket comments are for non-technical business partners: describe user-visible behavior and impact only, no PRs, branches, or code terms.
- After delivery is merged and the integration branch build passes, remove the task's temporary branches and worktrees (see the cleanup rules in `sprout_api/AGENTS.md`).

## Safety

- Do not edit signing configs, keystores, `google-services.json`, or the Play publishing track without being asked.
- Do not publish to Google Play or change the application ID without explicit approval.
- Never log tokens, message bodies, or participant personal data. Redact in debug logs too.
