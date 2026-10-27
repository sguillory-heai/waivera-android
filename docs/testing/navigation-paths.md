# Navigation path tour

Robolectric + Compose UI tests that drive the real app (`MainActivity`, the real shell and every
feature screen, Hilt graph included) through each navigation path, assert where the app is at
every step, and write a Roborazzi image per step. They are the Android-first UI sign-off's map
of the app: if a path changes, its test and this page change with it.

Code: `app/src/testDebug/kotlin/com/waivera/waivera/navtour/`.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.waivera.waivera.navtour.*'   # assertions only
./gradlew :app:recordRoborazziDebug --tests 'com.waivera.waivera.navtour.*' # write the images
./gradlew :app:verifyRoborazziDebug --tests 'com.waivera.waivera.navtour.*' # compare with them
```

Images land in `app/build/outputs/roborazzi/` as `path_<NN>_<flow>_<NN>_<step>.png`.

## Set-up

- **Data**: the debug demo data layer (`:core:demo`), as `--ez waivera.fake true` gives on a
  device, with three test swaps (`TourModules.kt`): `TourSessionRepository` can put the session
  in any state the gate knows (password change, bootstrap failure, onboarding, missing
  configuration) and can hold a sign-in in `Initializing`; `TourCalendarRepository` gives Sprout
  meetings a lobby URL on the reserved `.test` domain; the scripted demo hub does not start, so no
  typing, mention or reaction lands mid-path.
- **Time**: `TourClock` starts at the demo world's "now", Sunday 1 November 2026 10:30 in Chicago
  (the DST fall-back day). The device zone is set to America/Chicago and the locale to en-US.
- **Device**: 412×915dp, xxhdpi, light, SDK 35, animator scale 0 ("Remove animations").
- **Notifications**: permission granted (no primer) except in path 1.
- These tests live in `testDebug` because release builds have no demo data (`DemoSwitchModule`
  is debug-only) and would get duplicate bindings from `LiveRepositoriesModule`.

Suffixes: `_dark` is the same step with the Dark theme preference (set through the demo settings,
then restored); `_font200` is the step at 200% font in `NavigationPathVariantsTest`;
`_swipe` is a predictive back gesture held halfway, `_before`/`_after` either side of it.

Every step asserts the shell's gate and visible back stack (Today's stack plus the selected
tab's, compared by screen identity) and something on screen (a heading, a row, a button).

## 1. Cold start signed out → sign-in → primer → Today (`Path01SignInTest`)

| Step | Action | Expect | Image |
| --- | --- | --- | --- |
| 1 | Launch signed out | Sign-in gate, "Welcome back." | `path_01_signin_01_empty` (+ `_dark`, `_font200`) |
| 2 | Sign in with empty fields | "Enter your email.", "Enter your password.", still sign-in | `path_01_signin_02_validation` (+ `_font200`) |
| 3 | Email without a domain | "Enter an email address like name@organization.com." | `path_01_signin_03_invalid_email` |
| 4 | Wrong password | "Couldn't sign in" banner, still sign-in | `path_01_signin_04_wrong_password` |
| 5 | Password `demo`, bootstrap held | "Connecting to Waivera…", gate stays sign-in (not the loader) | `path_01_signin_05_connecting` |
| 6 | Bootstrap finishes | Notification primer, "Stay in the loop" | `path_01_signin_06_primer` (+ `_dark`) |
| 7 | Not now | Tabs, stack Today | `path_01_signin_07_today` |

Back: on Today's root and on the sign-in screen back leaves the app. A second sign-in in the same
install skips the primer (`path_01_signin_08_second_sign_in_skips_primer`).

## 2. Gates (`Path02GatesTest`)

| Flow | Steps | Images |
| --- | --- | --- |
| Required password change | Gate "Set a new password" → Update with empty fields shows "Enter your current password." → valid change → Today | `path_02_password_01_required` (+ `_dark`, `_font200`), `_02_validation`, `_03_today` |
| Back on the password gate | Back either leaves the app or stays on the gate; never reaches the tabs | — |
| Sign out from the password gate | Sign out → sign-in | `path_02_password_04_signed_out` |
| Bootstrap failed → retry | "We couldn't reach Waivera" → Try again fails (still the gate) → Try again → Today | `path_02_bootstrap_01_failed` (+ `_dark`, `_font200`), `_02_retry_failed`, `_03_today` |
| Bootstrap failed offline | Network error says "You're offline" | `path_02_bootstrap_04_offline` |
| Onboarding required | "Finish setting up your account" → "I've finished, check again" → Today | `path_02_onboarding_01_required`, `_02_today` |
| Configuration missing | Routed to `SignIn(configurationMissing = true)`, "This build isn't set up yet"; back leaves the app | `path_02_configuration_01_missing` |

## 3. Tabs (`Path03TabsTest`)

| Step | Action | Stack | Image |
| --- | --- | --- | --- |
| 1 | Launch | Today | `path_03_tabs_01_today` (+ `_dark`, `_font200`) |
| 2 | Chat tab | Today ▸ Chat list | `path_03_tabs_02_chat` (+ `_dark`, `_font200`) |
| 3 | Schedule tab | Today ▸ Schedule | `path_03_tabs_03_schedule` (+ `_dark`, `_font200`) |
| 4 | Today tab | Today | `path_03_tabs_04_today_again` |

Back: from the Chat root → Today (`path_03_tabs_05_back_from_chat_root`), then out of the app;
from the Schedule root by predictive swipe → Today (`path_03_tabs_06_predictive_back_from_schedule`,
`path_03_tabs_07_back_from_schedule_root`); a cancelled swipe on Chat changes nothing; re-selecting
a tab at its root changes nothing.

Per-tab stacks: Chat ▸ Priya's conversation (`path_03_tabs_08_chat_conversation`), then a link to
an event switches to Schedule with its own stack (`path_03_tabs_09_schedule_event_from_link`);
back twice → Today; the Chat tab still shows the conversation (`path_03_tabs_10_chat_stack_kept`).
The bottom bar only shows on tab roots, so a link is the only way to leave a tab mid-stack.

## 4. Chat (`Path04ChatTest`)

| Step | Action | Stack / screen | Image |
| --- | --- | --- | --- |
| 1 | Chat tab | Today ▸ Chat list | `path_04_chat_01_list` |
| 2 | Open Priya Raman | ▸ Conversation 7 | `path_04_chat_02_conversation` (+ `_dark`, `_font200`) |
| 3 | Long-press "schedule published" | Action sheet "MESSAGE PREVIEW", stack unchanged | `path_04_chat_03_message_actions` (+ `_dark`, `_font200`) |
| 4 | Close, long-press the 🎉 pill | Reactions sheet with Jordan Lee | `path_04_chat_04_reactions` (+ `_dark`) |
| 5 | Close, long-press the thread root → Reply in thread | ▸ Thread 7/9330 | `path_04_chat_05_thread` (+ `_dark`, `_font200`) |
| 6 | Back | Conversation 7 | `path_04_chat_06_back_to_conversation` |
| 7 | Back | Chat list | `path_04_chat_07_back_to_list` |
| 8 | Back | Today | — |

Also: the "2 replies" chip opens the thread and the top bar back arrow walks back
(`path_04_chat_08_thread_from_chip`); "Start a new chat" → New chat sheet → pick Mei Chen →
Open chat → Today ▸ Chat list ▸ the new conversation, back → Chat list (`path_04_chat_09_new_chat`
(+ `_dark`), `_10_new_chat_selected`, `_11_new_chat_conversation`); the failed demo send shows
"Failed", Retry sends it and the screen stays (`path_04_chat_12_failed_send`, `_13_retried`).

## 5. Today rows (`Path05TodayTest`)

Rows push onto the Today tab, so back returns to Today; "See schedule" switches tab.

| Row | Opens | Image |
| --- | --- | --- |
| Overview | — | `path_05_today_01_overview` (+ `_dark`) |
| Direct message (Priya; the test first adds an unread question from her, `TourSeeds.priyaAsks`) | Today ▸ Conversation 7 | `path_05_today_02_dm_conversation` |
| Mention in the group | Today ▸ Conversation 11 at message 9500 | `path_05_today_03_mention_conversation` |
| Thread reply activity | Today ▸ Thread 7/9330 at reply 9332 | `path_05_today_04_thread_reply` |
| Next up: Client home visit | Today ▸ Event | `path_05_today_05_event` |
| See schedule | Today ▸ Schedule (Schedule tab) | `path_05_today_06_see_schedule` |
| Accept on the invitation row | "Invitation accepted" with Undo, stays on Today | `path_05_today_07_invitation_accepted` |
| Accept on the invitation row → Undo | The invitation is back in Needs you, nothing was sent | (no shot) |
| Invitation row body → Accept → Undo | Today ▸ Event; "You accepted the invitation" with Undo; Undo restores "You haven't responded yet." | `path_05_today_08_invitation_event`, `_09_invitation_accepted_undo`, `_10_invitation_undone` |

Both answers wait out the same 10 s Undo window before they are sent: on the event screen and on Today's one-tap row.

## 6. Schedule (`Path06ScheduleTest`)

Clock moved to 19:45Z, which is 13:45 CST in Chicago on the fall-back day, so the 14:00 Sprout meeting's join window is open.

| Step | Action | Stack | Image |
| --- | --- | --- | --- |
| 1 | Schedule tab, Month | Today ▸ Schedule | `path_06_schedule_01_month` (+ `_dark`) |
| 2 | Week | same | `path_06_schedule_02_week` |
| 3 | Day | same, "Supervision 1:1" listed | `path_06_schedule_03_day` (+ `_dark`) |
| 4 | Open Supervision 1:1 | ▸ Event, "Join meeting" | `path_06_schedule_04_event` (+ `_dark`, `_font200`) |
| 5 | Join meeting | Custom Tab started with the lobby URL; stack unchanged | `path_06_schedule_05_after_join_handoff` |
| 6 | Back | Today ▸ Schedule | `path_06_schedule_06_back_to_schedule` |
| 7 | Back | Today | — |

Also: before the window the event says "Join opens at …" (`path_06_schedule_07_join_not_yet`);
the meeting chat (or Message organizer) pushes a conversation over the event on the Schedule
tab, and back returns to the event (`path_06_schedule_08_meeting_chat`).

## 7. Account menu, Profile, People, Bookmarks, Settings (`Path07AccountTest`)

| Flow | Stack | Images |
| --- | --- | --- |
| Avatar → menu | Today (sheet over it) | `path_07_account_01_menu` (+ `_dark`, `_font200`) |
| View profile | Today ▸ Profile | `path_07_account_02_profile` (+ `_dark`) |
| People → Priya → Message | Today ▸ People ▸ Person ▸ Conversation; back walks up each step | `_03_people`, `_04_person` (+ `_dark`), `_05_person_conversation` |
| Bookmarks → saved thread root | Today ▸ Bookmarks ▸ Conversation 7 at 9330 (a root opens its conversation; a reply would open its thread) | `_06_bookmarks` (+ `_dark`), `_07_bookmark_thread_root` |
| Settings | Today ▸ Settings (the menu's items are peers, so Settings goes straight onto the current tab) | `_08_settings` (+ `_dark`, `_font200`) |
| Appearance → Dark | ▸ Appearance; the app turns dark; back to Settings in dark | `_09_appearance`, `_10_appearance_dark`, `_11_settings_in_dark` |
| Notifications | ▸ Notifications, back → Settings | `_12_notifications` (+ `_dark`) |
| Change password | ▸ Change password, top bar back → Settings | `_13_change_password` |
| Sign out | Styled confirmation "Sign out of Waivera?"; Cancel keeps Settings; Sign out → sign-in gate with every stack cleared | `_14_sign_out_confirm` (+ `_dark`, `_font200`), `_15_signed_out` |

Also: back from Settings opened in the menu lands on Today, where the menu was opened
(`path_07_account_16_back_from_settings`). Profile's own Settings row still stacks Today ▸ Profile ▸
Settings, and back from there returns to Profile. The menu opened on the Chat tab pushes People onto
Chat (`path_07_account_17_people_over_chat`).

## 8. Links and notification taps (`Path08DeepLinksTest`)

| Entry | Stack | Back | Image |
| --- | --- | --- | --- |
| Cold `waivera://chats/7?messageId=9312` | Today ▸ Chat list ▸ Conversation 7 (at 9312) | Chat list, Today, out | `path_08_links_01_conversation` |
| Cold `waivera://chats/7/thread/9330?messageId=9332` | … ▸ Conversation 7 ▸ Thread | each parent | `path_08_links_02_thread` |
| Cold `waivera://calendar/demo_visit` | Today ▸ Schedule ▸ Event | Schedule, Today | `path_08_links_03_event` |
| Cold `waivera://profile` | Today ▸ Profile | Today | `path_08_links_04_profile` |
| Cold `waivera://people/usr_priya_raman` | Today ▸ People ▸ Person | People, Today | `path_08_links_05_person` |
| Cold `waivera://settings/notifications` | Today ▸ Settings ▸ Notifications | Settings, Today | `path_08_links_06_notification_settings` |
| Unknown or malformed (`waivera://nowhere/7`, non-numeric or negative ids, `https://`, over-long ids) | unchanged | — | `path_08_links_07_unknown` |
| Warm link from the Schedule tab | Chat tab with a fresh stack | Chat list, Today | `path_08_links_08_warm_conversation` |
| Link while signed out | waits; after sign-in opens the conversation | — | `path_08_links_09_after_sign_in` |
| Link waiting behind the password gate, then sign out | discarded; next sign-in lands on Today | — | — |
| Push `chat-message.json` tapped | the posted notification's own intent → Conversation 7 at 9312 | — | `path_08_push_01_chat_message` |
| Push `chat-message-thread-reply.json` tapped | Thread at the reply | Conversation | `path_08_push_02_thread_reply` |
| Push `legacy-meeting.json` tapped (cold) | Today ▸ Schedule ▸ Event `evt_8f2b19c4` | Schedule | `path_08_push_03_legacy_meeting` |
| Push `unknown-type.json` tapped | Today ▸ Chat list | — | `path_08_push_04_unknown_type` |
| Push for the conversation open on the Chat tab | suppressed (`ChatOnScreen`), no notification; a tap only moves the highlight | — | `path_08_push_05_already_open` |

Pushes go through the real `PushMessageHandler`, the real notifier and the real tap
`PendingIntent`; the payloads are copies of `docs/push-test-payloads/` in
`app/src/testDebug/resources/push/`.

## 9. Back on every screen (`Path09BackTest`)

A predictive back swipe held halfway (`_swipe`), then committed:

| Screen | Lands on | Images |
| --- | --- | --- |
| Conversation (Chat tab) | Chat list | `path_09_back_01_conversation_{before,swipe,after}` |
| Thread | Conversation | `path_09_back_02_thread_*` |
| Chat root | Today | `path_09_back_03_chat_root_*` |
| Schedule root | Today | `path_09_back_04_schedule_root_*` |
| Event (Schedule tab) | Schedule | `path_09_back_05_event_*` |
| Event opened from Today | Today | `path_09_back_06_event_from_today_*` |
| Profile | Today | `path_09_back_07_profile_*` |
| People | Today | `path_09_back_08_people_*` |
| Person | People | `path_09_back_09_person_*` |
| Bookmarks | Today | `path_09_back_10_bookmarks_*` |
| Settings from the account menu | Today (where the menu was opened) | `path_09_back_11_settings_*` |
| Notification settings | Settings | `path_09_back_12_notification_settings_*` |
| Appearance | Settings | `path_09_back_13_appearance_*` |
| Change password | Settings | `path_09_back_14_change_password_*` |
| Today root | leaves the app (no in-app callback intercepts it) | `path_09_back_15_today_root` |

Sheets close on back before anything under them moves: the account menu
(`path_09_back_16_account_menu_open`, `path_09_back_17_account_menu_closed`), the message
actions sheet and the sign-out confirmation.

## Tests that record a gap

These assert documented behaviour the app does not have yet. They fail until the app is fixed;
do not loosen them.

- `Path08DeepLinksTest.path08_pushTapForConversationAlreadyOpenFromToday_staysOnSameScreen`:
  app-shell.md says a link to the screen already on top changes nothing. A conversation opened
  from Today's "Needs you" sits on the Today tab, and `NavState.open` only treats a target as on
  top when its tab is selected, so the tap rebuilds the Chat tab and moves the person.
- `Path04ChatTest.path04_reactionPill_offersItsActionsToTalkBack`: the reaction pill sets its
  label with `clearAndSetSemantics` ahead of `WaiveraPressable`'s click handling, which drops the
  toggle and "Show who reacted" actions for TalkBack (AGENTS.md: every control works with TalkBack).
  The tour long-presses the pill by touch so the path itself still runs.
