# Requests from `:feature:auth`

Changes outside `:feature:auth` that the auth and onboarding screens need. Each one is small.
The feature code already works without them; the notes say what is missing until they land.

## 1. App gate: route the two new gate screens (`:app`)

`:feature:auth` now has `BootstrapFailedScreen(onSignOut, modifier)` and
`OnboardingRequiredScreen(onSignOut, modifier)`.

- `WaiveraApp`: `AppGate.BootstrapFailed -> BootstrapFailedScreen(onSignOut = actions.signOut)`,
  and delete the private `BootstrapFailedScreen` and `shell_bootstrap_failed` in `:app`. The new
  screen shows the mapped reason (offline vs server), retries through `SessionRepository.refresh`
  itself, and adds **Sign out**. Today the person has no way out of that gate except Try again.
- `AppGate`: add `data object OnboardingRequired`, counted in `isSignedIn`, and in `from()`:
  `is SessionState.SignedIn -> when { session.user.requiresOnboarding -> OnboardingRequired;
  primerPending -> NotificationPrimer; else -> Main }`. Spec 01 §5 rule 5: the onboarding gate
  shows when `requiresOnboarding` is true and no password change is required. Deep links keep
  waiting, as for the other gates.
- `WaiveraApp`: `AppGate.OnboardingRequired -> OnboardingRequiredScreen(onSignOut = actions.signOut)`.

Until this lands, a pending-signup account reaches the tabs (01 acceptance check 3 fails).

## 2. App gate: keep the sign-in screen up while signing in (`:app`)

`LiveSessionRepository.signIn` sets `SessionState.Initializing` while Firebase and the backend
bootstrap run, and `AppGate.from` maps `Initializing` to `Loading`, so the sign-in screen is
replaced by the full-screen loader the moment the person taps Sign in. Spec 01 §8 check 1 wants
the button spinner with "Connecting to Waivera…" during the bootstrap (the screen already shows
it while its `signIn` call is in flight).

Smallest fix, in `ShellState.onGate`: when the current gate is `SignIn` and the next is
`Loading`, stay on `SignIn`. A failure returns to `SignIn` anyway, and success goes straight to
the next gate. (Alternative in `:core:data`: a `SessionState.SigningIn` state; heavier.)

## 3. Settings: voluntary password change (`:app` + `:feature:settings`)

`PasswordChangeScreen(onSignOut, modifier, onBack = null, onChanged = {})`: with `onBack` set it
is the voluntary flow (top bar with back, no sign-out). Needs:

- a `ChangePasswordRoute` in `:app` (`RouteContent`:
  `PasswordChangeScreen(onSignOut = {}, onBack = back, onChanged = back)`), and
- a "Change password" row in Settings → Security that opens it (owned by `:feature:settings`;
  `SettingsScreen` would need an `onOpenChangePassword` lambda with a default).

## 4. `SessionRepository.changePassword` (`:core:data`)

1. **Do not re-bootstrap through `Initializing` for a voluntary change.** `changePassword` ends
   with `refresh()`, and `bootstrap` publishes `Initializing` first, so the shell drops to the
   full-screen loader and back while the person is in Settings. Only re-run the bootstrap when
   the current state is `PasswordChangeRequired`, or refresh without publishing `Initializing`.
2. **Make it resumable.** If Firebase accepted the new password but `confirm-password-change`
   failed (offline), a retry re-authenticates with the *old* password, which Firebase now
   rejects, and the screen says "That isn't your current password". Suggest remembering that the
   Firebase step succeeded (in memory, for this uid) and only retrying the confirm call, or
   trying the confirm call first when re-authentication fails with the new password.

## 5. Design system: autofill hints on `WaiveraTextField` (`:core:designsystem`)

Add `contentType: ContentType? = null` to `WaiveraTextField` and apply it with
`Modifier.semantics { contentType = it }` on the inner `OutlinedTextField` (not the outer
column, which is what a feature can reach today). Sign-in would pass
`ContentType.EmailAddress + ContentType.Username` and `ContentType.Password`; the password
change screen `ContentType.NewPassword`. Without it, password managers do not offer to fill or
save Waivera credentials.

## 6. Design system: visibility icons (`:core:designsystem`, optional)

`:feature:auth` ships private `feature_auth_ic_visibility`, `_visibility_off` and `_lock`
drawables. If another module needs them, move them to `WaiveraIcons`.
