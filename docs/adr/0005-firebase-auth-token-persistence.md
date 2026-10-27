# 0005: Firebase Auth keeps its own refresh token

Status: Accepted (2026-10-27). Accepted risk, recorded so it is not "fixed" by caching tokens.

## Context

AGENTS.md says tokens live in encrypted DataStore, never in plain `SharedPreferences`. The app
itself follows that: it never stores the ID token or the refresh token, and reads the ID token on
demand through `FirebaseAuthGateway` (`getIdToken`, forced refresh after a 401). The Firebase Auth
Android SDK, however, persists the signed-in user, refresh token included, in its own unencrypted
`com.google.firebase.auth.api.Store.*` preferences. The SDK offers no custom persistence hook, and
the security review (AS-07) rated this low.

## Decision

- Keep Firebase Auth's default persistence. Do not copy, cache or wrap tokens in app storage to
  work around it; that would add a second copy rather than remove one.
- Keep the mitigations that make the file hard to reach:
  - `allowBackup="false"` and `fullBackupContent="false"` in the manifest;
  - `data_extraction_rules.xml` excludes the `sharedpref` domain from cloud backup and device
    transfer;
  - signing out, and a session the API rejects, end the session through one cleanup routine and
    then sign Firebase out, which clears its store.
- Reading the token needs a rooted device or a debuggable build of this app.

## Consequences

- Any change to the backup or data-extraction rules must keep `sharedpref` excluded; reviewers
  re-check this record when those files change.
- If Firebase adds pluggable or encrypted persistence, or the app moves to the Identity Toolkit
  REST API (as iOS does, PLAN.md), revisit this record and supersede it.
