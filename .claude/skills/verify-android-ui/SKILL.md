---
name: verify-android-ui
description: Verify a new or changed Compose screen on the emulator before calling it done. Use after any UI, animation, haptics, or navigation change.
---

Verify the changed UI as a user would, then record evidence.

1. List the affected screens and the states that apply: loading, empty, populated, long content, error, offline/reconnecting, disabled, selected.
2. Install the debug build on an emulator pointed at the fake server in `core/testing` (never shared dev data): `./gradlew installDebug`.
3. For each screen and state, capture settled screenshots (`adb exec-out screencap -p > <file>.png` after animations finish) in:
   - light and dark theme (`adb shell cmd uimode night yes|no`)
   - a small phone and a large phone
   - default and 200% font scale (`adb shell settings put system font_scale 2.0`)
4. Interact for real: tap every control, predictive back, pull to refresh, send a message, toggle settings. Confirm exactly one state change per action, no layout jumps, no clipped text, correct edge-to-edge insets with and without the keyboard.
5. Set animator scale to 0 (`adb shell settings put global animator_duration_scale 0`) and confirm nothing breaks or hangs. Restore it afterward.
6. Run the Accessibility Scanner checks in the Compose UI test for the screen (`enableAccessibilityChecks()`) and fix findings.
7. If push or realtime is involved, send the debug push broadcast while the app is foregrounded, backgrounded, and killed. Confirm the notification, channel, badge, and deep link.
8. Update or add Roborazzi screenshot tests for the screen in the states above and run `./gradlew verifyRoborazziDebug`.
9. Score the result 1 to 10 for professional polish against `AGENTS.md` design rules. Below 9.5, fix the findings and repeat the affected steps.

Report: screens checked, states covered, screenshots path, what you could not check and why. Never claim a check you did not run. Reset font scale and night mode when done.
