# Requests from feature/calendar (Schedule tab)

Core changes the Schedule feature needs but does not own. Each item names the local stand-in the
feature uses today, so the stand-in can be deleted when the core change lands.

## 1. `CalendarRepository.observeEvent(id)` (core/data)

**Need.** The event screen is routed with an id only (`CalendarEventRoute(eventId)`), and it is
opened from push and deep links as well as from the schedule. `CalendarRepository` can only
observe feed ranges, so a cold id has nowhere to come from.

**Proposed API.**

```kotlin
/** One event, cached; refreshes from `GET api/CalendarEvent/GetEventbyId/{id}` when stale or missing. */
fun observeEvent(eventId: String): Flow<Synced<ScheduleEvent?>>
suspend fun refreshEvent(eventId: String): Result<Unit>
```

`CalendarApi.getEventById` already exists in core/network. A 404 should map to `data = null` with
a synced timestamp (so the screen can say "not found" rather than "error"). After
`respondToInvitation` succeeds, the cached event should update along with the feed ranges.

**Stand-in.** `feature/calendar/.../data/ScheduleEventStore.kt` remembers the range each event was
last seen in, and `data/EventLookup.kt` watches that range; a cold id is searched in the month grids
for the previous, current and next month in the employee's zone. An event outside those three
months opened from a push shows "not found" until this lands.

## 2. Holiday description on the `Holiday` model (core/data)

**Need.** 07 §3 shows a holiday's description under its title. `HolidayDto.description` is
decoded, but `CalendarMath.feed` drops it when it builds `Holiday(id, title, start, endInclusive)`.

**Proposed change.** Add `val description: String? = null` to `Holiday` and pass `h.description`
through in `CalendarMath.feed`.

**Stand-in.** The agenda card's subtitle is always "Company holiday".

## 3. Milestone avatar and calendar tokens (core/designsystem)

**Need.** Profile, the chat list and conversation headers will want the same birthday/anniversary
badge on avatars (07 §4). The feature has private copies:

- `ui/MilestoneAvatar.kt`: an avatar with a corner badge (cake, award, or celebration icon) and
  the years shown only on anniversaries at 40dp and larger, with one full-sentence TalkBack label.
  `WaiveraAvatar` already takes `milestone: MilestoneKind`, but it has no MULTIPLE kind and no
  years.
- `ui/CalendarTokens.kt`: `CalendarSizes` (day dot 5dp, dot gap 3dp, today border 1.5dp, event
  accent bar 4dp, event card minimum height 66dp, week cell minimum height 76dp, badge ratios) and
  `CalendarIcons` (chevron left, place, videocam, open in new, time off, award, cake, celebration)
  as module vector drawables.

**Proposed change.** Add `MilestoneKind.Multiple` plus an optional `years: Int?` to
`WaiveraAvatar`'s badge, a `multipleContainer`/`onMultipleContainer` colour pair (the feature uses
`accentSoft`/`onAccentSoft` for now), and promote the icons and the sizes the badge uses.

## 4. The signed-in user's name and avatar for the top bar account button (app or core/data)

**Need.** `WaiveraTopBar` takes `TopBarAccount(name, onClick, painter)`. The Schedule entry
composable's signature (`ScheduleScreen(onAccountClick, onOpenEvent, modifier)`) carries no user,
so the feature passes a generic "Account" label and no picture.

**Proposed change.** Either a shared `@Composable rememberTopBarAccount(onClick)` in a core module
that reads `SessionRepository` and `PeopleRepository.avatar`, or app-level injection of the account
into every tab's top bar. Either keeps the entry signature unchanged.

## 5. Spec conflict to settle (docs, not code)

07-calendar says to fall back to the **device** zone when the employee has no saved zone;
`contract/dates.md`, `docs/architecture/data-layer.md` and `sprout_api/docs/timezone-standard.md`
say **America/Chicago**. The feature follows the contract (`WaiveraTime.employeeZone`), never uses
the device zone for placement, and says on screen which zone is in effect when the saved zone is
missing or invalid. 07 should be corrected to match.
