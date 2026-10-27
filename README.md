# Waivera Android

Native Kotlin and Jetpack Compose app for Waivera staff: realtime chat, push
notifications and schedules, backed by `sprout_api`.

- Agent and contributor rules: [AGENTS.md](AGENTS.md)
- Architecture decisions: [docs/adr](docs/adr)

## Requirements

- Android Studio (latest stable) and JDK 17
- Android SDK with API 37

## Common commands

```bash
./gradlew assembleDebug                   # build
./gradlew testDebugUnitTest               # unit tests
./gradlew ktlintCheck detekt lintDebug    # format, static analysis, lint
```

CI runs the same checks on every pull request.
