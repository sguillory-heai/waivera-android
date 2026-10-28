# Waivera voice

Waivera is the app staff open between visits, on a break or from a notification. Most sessions
last under a minute. The words should feel like a helpful coworker: warm, quick and clear. They
should never sound like a policy manual.

All copy lives in `strings.xml` resources. Brand-only additions go in `strings_brand.xml`.

## Tone

- **Warm, not cute.** Sound like a person: "Nice work. New messages will land here.", not "No
  unread items."
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
| Celebration | Name the person, one emoji (in the tile when the card has one) | "Happy birthday, Priya! 🎂" |
| Your own celebration | Your first name, to you | "Happy birthday, Alex!" (never "…, Alex Rivera!") |
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

Use them sparingly. Too many read as tacky, so the rule is strict:

- **Only on celebration content and the one reward.** A birthday 🎂, a work anniversary 🎉 and a
  new hire 👋 get their emoji. "You're all caught up 🎉" on Today is the one reward that earns
  one. Nothing else does: no emoji in greetings ("Good morning, Alex", not "Good morning ☀️"),
  successes, empty states, settings, filters or headings.
- **Everywhere else, a flat outlined icon in the standard tile, or nothing.** A row or note that
  wants a mark uses an outlined glyph in `WaiveraIconTile` in its tone. `WaiveraEmojiTile` is for
  celebrations only.
- **Once per moment.** A celebration shows its emoji once: in its tile when the card has one
  (Today, the schedule, a profile), and then the sentence carries none and the avatar has no
  badge. The tile is decorative; TalkBack reads the sentence.
- **At the end, tied to the last word.** When the emoji is in the sentence, it comes last and is
  joined to the last word by a no-break space (`\u00A0`), so it never wraps onto a line by itself.
- **Never** in errors, warnings, security, sign-in, password or permission copy, or in
  accessibility-only labels (`*_a11y`, content descriptions). TalkBack reads emoji names aloud,
  and "party popper" adds nothing to a label.
- Compact labels in dense UI (calendar cells, badges, list rows) stay plain.
- Reactions use real emoji as data. That is content the person sent, not voice: it shows once,
  in the sentence ("Jordan reacted 👍 to one of your messages."), and its row takes the outlined
  reaction icon in the tile.

## Mechanics

- Sentence case everywhere ("Start group chat", not "Start Group Chat"). Section labels may render
  in capitals through style, never through the string.
- Use the ellipsis character `…` for progress, not three dots.
- Escape apostrophes as `\'` and ampersands as `&amp;` in XML. Keep `%1$s` and `%1$d` placeholders
  and plural forms intact when you rewrite a string.
- Names in sentences come from data and may be missing. Fallbacks ("Teammate") must read well as
  a headline and in a greeting ("Happy birthday, Teammate!").
- Accessibility labels describe the control and the target ("Remove bookmark from Priya Raman").
  Being warm never makes a label vaguer.
- Tests look copy up through `stringOf(R.string.…)` (navigation tour) or the module's resources,
  so a copy change doesn't break a selector.
