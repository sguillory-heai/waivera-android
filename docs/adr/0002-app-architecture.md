# 0002: Modular, unidirectional architecture with a local source of truth

Status: Accepted (2026-10-27)

## Context

The app must feel instant on poor connections and survive reconnects without losing
messages or showing stale state.

## Decision

- Modules: `app`, `core:*` (designsystem, network, realtime, data, testing) and one
  `feature:*` module per product area. Features depend on core modules, never on each other.
- Data flows one way: screen → ViewModel → repository → network and local store; the screen
  renders from a single `StateFlow<UiState>` that the repository feeds from the local store.
- Room is the source of truth for anything a screen lists. Writes are optimistic: saved locally
  first, then sent through an outbox that WorkManager retries.
- The chat hub sits behind `RealtimeClient`. Because the SignalR Java client has no automatic
  reconnect, the implementation owns backoff (`ReconnectPolicy`), rejoins open conversations,
  and resyncs over REST after every reconnect.
- Build conventions live in `build-logic`; versions live only in `gradle/libs.versions.toml`.

## Consequences

- Screens never wait on the network to respond to a tap.
- Every repository needs a fake in `core:testing`, and every ViewModel needs tests for loading,
  empty, error and reconnect states.
