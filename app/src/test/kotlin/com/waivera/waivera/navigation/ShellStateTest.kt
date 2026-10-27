package com.waivera.waivera.navigation

import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellStateTest {
    private val user = CurrentUser(userId = "u-1")

    @Test
    fun gateFollowsTheSession() {
        assertEquals(AppGate.Loading, AppGate.from(SessionState.Initializing, primerPending = true))
        assertEquals(AppGate.SignIn(), AppGate.from(SessionState.SignedOut(), primerPending = true))
        assertEquals(
            AppGate.SignIn(configurationMissing = true),
            AppGate.from(SessionState.ConfigurationMissing(listOf("apiKey")), primerPending = false),
        )
        assertEquals(
            AppGate.PasswordChange,
            AppGate.from(SessionState.PasswordChangeRequired(user), primerPending = true),
        )
        assertEquals(AppGate.NotificationPrimer, AppGate.from(SessionState.SignedIn(user), primerPending = true))
        assertEquals(AppGate.Main, AppGate.from(SessionState.SignedIn(user), primerPending = false))
    }

    @Test
    fun deepLinkWhileSignedOutWaitsAndOpensAfterSignIn() {
        val waiting = ShellState()
            .onGate(AppGate.SignIn())
            .onDeepLink(DeepLinkTarget.Conversation(42))
        assertEquals(DeepLinkTarget.Conversation(42), waiting.pendingDeepLink)
        assertEquals(TodayRoute, waiting.nav.top)

        val signedIn = waiting.onGate(AppGate.Loading).onGate(AppGate.NotificationPrimer).onGate(AppGate.Main)
        assertNull(signedIn.pendingDeepLink)
        assertEquals(ConversationRoute(42), signedIn.nav.top)
        assertEquals(TopLevelTab.Chat, signedIn.nav.selected)
    }

    @Test
    fun signingOutDiscardsThePendingLinkAndTheStacks() {
        val state = ShellState()
            .onGate(AppGate.Main)
            .onDeepLink(DeepLinkTarget.Conversation(1))
            .onGate(AppGate.PasswordChange)
            .onDeepLink(DeepLinkTarget.Conversation(42))
            .onGate(AppGate.SignIn())
        assertNull(state.pendingDeepLink)
        assertEquals(NavState(), state.nav)
    }

    @Test
    fun deepLinkInMainOpensAtOnce() {
        val state = ShellState().onGate(AppGate.Main).onDeepLink(DeepLinkTarget.CalendarEvent("evt_1"))
        assertEquals(CalendarEventRoute("evt_1"), state.nav.top)
    }

    @Test
    fun navigationIsIgnoredOutsideMain() {
        val state = ShellState().onGate(AppGate.SignIn())
        assertEquals(state, state.onNav { it.push(SettingsRoute) })
    }

    @Test
    fun visibleChatOnlyForConversationAndThread() {
        assertEquals(42L to null, ConversationRoute(42, 3).visibleChat)
        assertEquals(42L to 7L, ThreadRoute(42, 7).visibleChat)
        assertNull(ChatListRoute.visibleChat)
    }

    @Test
    fun unfinishedSignupGetsTheOnboardingGateBeforeThePrimer() {
        val pending = SessionState.SignedIn(user.copy(requiresOnboarding = true))
        assertEquals(AppGate.OnboardingRequired, AppGate.from(pending, primerPending = true))
        assertEquals(AppGate.OnboardingRequired, AppGate.from(pending, primerPending = false))
        assertTrue(AppGate.OnboardingRequired.isSignedIn)

        val waiting = ShellState().onGate(AppGate.OnboardingRequired).onDeepLink(DeepLinkTarget.Conversation(42))
        assertEquals(DeepLinkTarget.Conversation(42), waiting.pendingDeepLink)
        assertNull("signing out from the gate drops the link", waiting.onGate(AppGate.SignIn()).pendingDeepLink)
    }

    @Test
    fun signInScreenStaysUpWhileTheBootstrapRuns() {
        val signingIn = ShellState().onGate(AppGate.SignIn()).onGate(AppGate.Loading)
        assertEquals(AppGate.SignIn(), signingIn.gate)
        assertEquals(AppGate.Main, signingIn.onGate(AppGate.Main).gate)
        val main = ShellState().onGate(AppGate.Main)
        assertEquals("only from sign-in", AppGate.Loading, main.onGate(AppGate.Loading).gate)
    }
}
