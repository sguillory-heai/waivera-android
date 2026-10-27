@AGENTS.md

## Claude Code specifics

- Project skills live in `.claude/skills/`. Use `verify-android-ui` before calling any UI work done, and `sync-api-contract` whenever a `sprout_api` DTO or hub event changes.
- Recommended third-party skills (review before use): Google's official Android skills (`android skills list` for exact names, `android skills add <name>` to install), especially Set up Navigation 3, Make app edge-to-edge, Set up testing strategy, Use Android profilers, Audit R8 configuration, Audit Play policy compliance, and Android Intent security. They advise; this file and `AGENTS.md` win on conflicts.
- Use the Android CLI or `adb`/emulator commands for install, launch, screenshots, and UI inspection on a running emulator.
- Formatting and lint run automatically through hooks in `.claude/settings.json`. Do not disable or bypass them.
- For changes touching more than one module, plan first and list the modules you will touch.
- Before pushing, run `/code-review` on the diff and fix confirmed findings. Run `/security-review` when the change touches auth, token storage, push payloads, intents/deep links, or networking.
- Personal notes (emulator names, local API URL) go in `CLAUDE.local.md`, which is git-ignored.
