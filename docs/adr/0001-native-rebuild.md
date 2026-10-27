# 0001: Rebuild natively instead of staying cross-platform

Status: Accepted (2026-10-27)

## Context

The existing Waivera mobile app is an Expo / React Native app (`waivera-mobile`) shipped
to Google Play. The product's value on a phone is realtime chat, push notifications and
schedule tracking, with lightweight task updates to come. Ten rounds of device fixes in
that app were spent on keyboard handling, inverted lists and loosely typed API payloads.

## Decision

Rebuild as two native apps: Kotlin and Jetpack Compose here, Swift and SwiftUI in
`waivera-ios`. No runtime code is shared. What is shared is the contract: sprout_api's
REST DTOs, the chat hub's method and event names, the push payload format, and one
feature spec per slice.

Kotlin Multiplatform was considered and deferred: Swift export is still Alpha and iOS
debugging of shared code is getting harder, not easier. Core modules are kept free of
Android UI types so they could move to a shared module later.

## Consequences

- Native notification styles, widgets, Live Updates and system motion are available directly.
- Every feature is built and tested twice; each slice needs a parity check against the other app.
- This app keeps the package `com.waivera.waivera` and the existing upload key, so it ships as an
  update to the Expo app's Play listing. The Expo app stays live until this app reaches parity.
