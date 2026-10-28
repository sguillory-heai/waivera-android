# Waivera voice

Waivera is the app staff open between visits, on a break or from a notification. Most sessions
last under a minute. The words should feel like a helpful coworker: warm, quick and clear. They
should never sound like a policy manual.

All copy lives in `strings.xml` resources. Brand-only additions go in `strings_brand.xml`.

## Tone

- **Warm, not cute.** Sound like a person: "You're all caught up 🎉", not "No unread items."
- **Short.** Every line has to fit at 200% font on a small phone. One idea per sentence, and
  usually one sentence.
- **Plain words.** "Chat", not "conversation thread". "Start date", not "hire date". "Couldn't",
  not "Unable to".
- **Say what to do next.** Empty states invite the next action. Errors say what happened and
  what to try.
- **Calm in errors and security.** No jokes, no exclamation marks and no emoji. Never blame the
  person. Never show raw server text.
- **Talk to the person.** Use "you" and "your". Use "we" only for Waivera itself ("We couldn't
  reach Waivera").

## Patterns

| Moment | Pattern | Example |
| --- | --- | --- |
| Empty, first time | What will appear + how to get it | "Press and hold any message, then tap Bookmark to keep it here for later." |
| Empty, filtered | What's missing + one way out | "No matches" · "Try a different name or word." · Clear search |
| Celebration | Name the person, one emoji | "Happy birthday, Priya! 🎂" |
| Success | Confirm it landed, briefly | "Saved to your bookmarks" · "Accepted. See you there!" |
| Error | What failed + what to do | "You're offline. Reconnect, then try sending again." |
| Busy | What's happening now, with an ellipsis | "Signing you in…" · "Saving your new password…" |
| Placeholder | A hint about what to type, never a repeat of the label | Label "New password" · placeholder "Try a short phrase" |
| Setting | What it does for you, in one line | "Hear from Waivera even when the app is closed." |

## Do and don't

| Do | Don't |
| --- | --- |
| "Couldn't delete the message" | "Delete failed" |
| "You can't post here right now. Ask a channel admin if you need access." | "You do not currently have permission to send this message." |
| "Something went wrong on our side. Your message is still here, so try again." | "The server could not send this message." |
| "Nice work. New messages will land here." | "No unread conversations." |
| "Your day is wide open." | "No events." |
| "Check your inbox" | "Password reset email dispatched" |
| "Time for a new password" | "Password expired" |
| "Search by name or email" (placeholder) | "Search" (placeholder under a "Search" label) |
| "Your password wasn't changed" | "Oops! Password fail 😅" |

## Emoji

- **Only for celebrations and success moments.** Birthdays 🎂, work anniversaries and other wins
  🎉. "All caught up" 🎉 is the one empty state that earns one, because it is a reward.
- **One per string, at the end**, so the words still lead and TalkBack reads the meaning first.
- **Never** in errors, warnings, security, sign-in, password or permission copy, or in
  accessibility-only labels (`*_a11y`, content descriptions). TalkBack reads emoji names aloud,
  and "party popper" adds nothing to a label.
- Compact labels in dense UI (calendar cells, badges) stay plain. The full sentence nearby carries
  the emoji.
- Reactions use real emoji as data. That is content, not voice, and these rules don't apply.

## Mechanics

- Sentence case everywhere ("Start group chat", not "Start Group Chat"). Section labels may render
  in capitals through style, never through the string.
- Use the ellipsis character `…` for progress, not three dots.
- Escape apostrophes as `\'` and ampersands as `&amp;` in XML. Keep `%1$s` and `%1$d` placeholders
  and plural forms intact when you rewrite a string.
- Names in sentences come from data and may be missing. Fallbacks ("Teammate") must read well as
  a headline and in a greeting ("Happy birthday, Teammate! 🎂").
- Accessibility labels describe the control and the target ("Remove bookmark from Priya Raman").
  Being warm never makes a label vaguer.
- Tests look copy up through `stringOf(R.string.…)` (navigation tour) or the module's resources,
  so a copy change doesn't break a selector.
