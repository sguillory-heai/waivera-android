---
name: sync-api-contract
description: Mirror a sprout_api DTO, endpoint, or SignalR hub event change into core/network or core/realtime. Use when the backend contract changes or a decode fails.
---

1. Find the source of truth in `sprout_api`: the controller action, the request/response DTO under `dto/`, or the hub method under `Hubs/`. Note nullability, enum values, and date formats.
2. Update the matching `@Serializable` class in `core/network` (or the event model in `core/realtime`). Keep property names aligned; use `@SerialName` only when Kotlin naming requires it.
3. Map C# nullable fields to nullable Kotlin types with defaults. Map enums with an `UNKNOWN` fallback.
4. Dates: decode as UTC `Instant`s. If the endpoint omits the zone suffix, use the shared lenient UTC serializer, not a local-time parse.
5. Add or update a fixture JSON in `core/testing/src/main/resources/fixtures` captured from the real response shape, and a decode test using it.
6. Run `./gradlew :core:network:testDebugUnitTest`.
7. If the iOS repo consumes the same contract, note the change in the PR so the iOS DTO is updated under the same ticket ID.
