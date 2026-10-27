# Architecture decision records

One short file per decision that would be expensive to reverse. Read the relevant
records before changing architecture, and add a new record (never rewrite an
accepted one) when a decision changes.

Format: `NNNN-title.md` with Status, Context, Decision, Consequences.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-native-rebuild.md) | Rebuild natively instead of staying cross-platform | Accepted |
| [0002](0002-app-architecture.md) | Modular, unidirectional architecture with a local source of truth | Accepted |
| [0003](0003-push-through-fcm.md) | Deliver pushes through FCM HTTP v1, data-only on Android | Accepted |
| [0004](0004-notification-channels-by-meaning.md) | Notification channels by meaning (supersedes the channel part of 0003) | Accepted |
| [0005](0005-firebase-auth-token-persistence.md) | Leave the refresh token in Firebase Auth's own store (accepted risk) | Accepted |
