# Requests from `:feature:profile` and `:feature:settings`

Small additions these modules need from modules they do not own. Each gap is coded against a
local adapter so the screens do not change when the request lands.

## core:data

1. **Organization details matched by id** (spec 08 §5).
   Add `ProfileRepository.organization(): Result<Organization?>` that calls
   `GET /api/Authentication/OrganizationDetails` and returns the entry whose `id` equals
   `CurrentUser.organizationId`, or null. No name-based fallback. Model:
   `Organization(id: Int, name: String?, address: String?, contactNumber: String?)` (add legal
   name and identifiers if `OrganizationDto` grows them).
   Local stand-in: `SessionOrganizationSource` (`feature/profile/.../ProfileSources.kt`) uses the
   session's id and name only, so address and contact show "—" today.

   **Status: Done.** `ProfileRepository.organization()`; `RepositoryOrganizationSource` delegates to it.

2. **Another person's details** (spec 06 §C2).
   Add `PeopleRepository.details(userId): Result<PersonDetails>` over
   `GET /api/Authentication/GetUserDetailsByUserID/{userId}` (`UserWithRoleDto`), mapping
   display name, email, phone, `roles` and `organizationName`.
   Local stand-in: `ProfilePersonDetailsSource` reads `ProfileRepository.profile(userId)`
   (`/api/employee/getProfile/{id}`), which has no roles or organization, so positions are shown
   as roles and the organization row is hidden. If `getProfile` refuses other employees for some
   roles, the person screen shows its error state with Try again.

   **Status: Done.** `PeopleRepository.details()`; `RepositoryPersonDetailsSource` delegates and shows roles and organization.

3. **Time-off percentage** (spec 08 §3).
   `TimeOffProfileDto.usedLeavePercentage` is decoded but not mapped. Add
   `TimeOffBalance.usedPercentage: Double?` (and `remainingLeaveCount` as a fallback for
   `remainingDays`). Until then `TimeOffMath.usedPercentage` derives it from used and allocated
   days.

   **Status: Done.** `usedPercentage` and the `remainingLeaveCount` fallback are mapped; `TimeOffMath` prefers the server's value.

4. **Profile fields the screen should show** (spec 08 §1 layout).
   `GetEmployeeProfileDto.waiverFamilies` and `UserWithRoleDto.scId` are not on
   `EmployeeProfile`. Add `waiverFamilies: List<String>` and `scId: String?`; the profile will
   add "Waiver families" and "SCID" rows (they are left out rather than shown as permanent "—").

   **Status: Done.** `waiverFamilies` and `scId` are mapped and shown as rows.

5. **Separate time-off summary** (spec 08 §3, API owner).
   The first time-off row doubles as the balance summary. Track an API change for a separate
   summary shape; nothing to do on the client until then.

   **Status: Moved to sprout-api.md** (§5).

## app

6. **Settings link from Profile.** `ProfileScreen` gained an optional
   `onOpenSettings: (() -> Unit)? = null` (after `modifier`). Please pass
   `onOpenSettings = { actions.push(SettingsRoute) }` in `RouteContent` and add the parameter to
   the routes table in `docs/architecture/app-shell.md`. Until then the Settings row is hidden
   (Settings stays reachable from the account menu).

   **Status: Done.** `RouteContent` passes `onOpenSettings`; `app-shell.md` lists it.

7. **Back from settings lands on Profile** (spec 08 §6 acceptance). Settings is pushed from the
   account menu onto the current tab, so back returns to wherever the person was (for example a
   chat). If the spec's "back lands on the profile" is still wanted, the account menu could push
   `ProfileRoute` then `SettingsRoute`, or the shell could treat Profile as Settings' parent.
   Decide in the shell; the settings screens just call `onBack`.

   **Status: Changed after UI review UI2-10.** The account menu now pushes Settings straight onto the
   current tab, so back returns to where the menu was opened; only Profile's own Settings row stacks
   Settings over Profile. This departs from spec 08 §6's "back lands on the profile" on purpose.

8. **Appearance before the first frame** (spec 08 §7). `MainActivity` reads the theme from
   `AppShellViewModel.uiState`; please confirm the initial state already carries the stored
   theme (for example a `runBlocking` first read of the settings DataStore before `setContent`,
   or a splash-screen keep-on-screen condition), so a Dark preference never flashes light.

   **Status: Done.** `MainActivity` holds the first frame until settings load (at most 1 s), with no `runBlocking`.

## Not requested, noted

- Sign-out, push unregister and token handling are entirely `SessionRepository.signOut()`;
  the settings screen only confirms and calls it once.
- `pushEnabled` off → one unregister call, back on → one register call with the stored
  categories: owned by the push registration sync in core:data (G-08). The settings screen only
  writes the flags and never changes the stored categories while push is off.
- `previewContent` and per-category filtering at display time belong to core:notifications.
