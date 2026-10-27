package com.waivera.waivera.feature.settings

import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

/** Counts sign-outs and can hold or fail them; the demo session does the rest. */
private class CountingSession(private val delegate: SessionRepository) : SessionRepository by delegate {
    var signOuts = 0
    var gate: CompletableDeferred<Unit>? = null
    var failWith: WaiveraError? = null

    override suspend fun signOut(): Result<Unit> {
        signOuts++
        gate?.await()
        return failWith?.let { Result.failure(it) } ?: delegate.signOut()
    }
}

private class FakeSystemStatus(var allowed: Boolean = true, var blocked: Set<String> = emptySet()) :
    SystemNotificationStatus {
    override fun appNotificationsAllowed(): Boolean = allowed

    override fun blockedChannelIds(): Set<String> = blocked
}

private fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun showsTheSignedInAccount() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = SettingsViewModel(graph.session)
        keepCollecting(vm.uiState)

        val account = requireNotNull(vm.uiState.value.account)
        assertEquals("usr_alex_rivera", account.userId)
        assertTrue(account.name.isNotBlank())
    }

    @Test
    fun signOutAsksFirstThenSignsOutOnceEvenWhenTappedTwice() = runTest {
        val graph = DemoGraph(backgroundScope)
        val session = CountingSession(graph.session).apply { gate = CompletableDeferred() }
        val vm = SettingsViewModel(session)
        keepCollecting(vm.uiState)

        vm.requestSignOut()
        assertTrue(vm.uiState.value.confirmingSignOut)
        assertEquals(0, session.signOuts)

        vm.confirmSignOut()
        vm.confirmSignOut()
        assertTrue(vm.uiState.value.signingOut)
        requireNotNull(session.gate).complete(Unit)

        assertEquals(1, session.signOuts)
        assertIs<SessionState.SignedOut>(graph.session.state.value)
        assertNull(vm.uiState.value.account)
        assertFalse(vm.uiState.value.confirmingSignOut)

        // Already signed out: a further attempt is a no-op.
        vm.requestSignOut()
        vm.confirmSignOut()
        assertEquals(1, session.signOuts)
        assertFalse(vm.uiState.value.confirmingSignOut)
    }

    @Test
    fun dismissingTheSheetDoesNotSignOut() = runTest {
        val graph = DemoGraph(backgroundScope)
        val session = CountingSession(graph.session)
        val vm = SettingsViewModel(session)
        keepCollecting(vm.uiState)

        vm.requestSignOut()
        vm.dismissSignOut()

        assertFalse(vm.uiState.value.confirmingSignOut)
        assertEquals(0, session.signOuts)
    }

    @Test
    fun aFailedSignOutStaysSignedInAndSaysSo() = runTest {
        val graph = DemoGraph(backgroundScope)
        val session = CountingSession(graph.session).apply { failWith = WaiveraError.Network() }
        val vm = SettingsViewModel(session)
        keepCollecting(vm.uiState)
        val effect = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { vm.effect.first() }

        vm.requestSignOut()
        vm.confirmSignOut()

        assertIs<SettingsEffect.SignOutFailed>(effect.await())
        assertIs<SessionState.SignedIn>(graph.session.state.value)
        assertFalse(vm.uiState.value.signingOut)
    }
}

class AppearanceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun themeAndHapticsArePersisted() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = AppearanceViewModel(graph.settings)
        keepCollecting(vm.uiState)
        assertEquals(ThemePreference.SYSTEM, vm.uiState.value.theme)
        assertTrue(vm.uiState.value.loaded)

        vm.setTheme(ThemePreference.DARK)
        vm.setHapticsEnabled(false)

        assertEquals(ThemePreference.DARK, graph.settings.settings.first().theme)
        assertFalse(graph.settings.settings.first().hapticsEnabled)
        assertEquals(ThemePreference.DARK, vm.uiState.value.theme)
        assertFalse(vm.uiState.value.hapticsEnabled)
    }
}

class NotificationSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val system = FakeSystemStatus()

    private fun TestScope.viewModel(graph: DemoGraph): NotificationSettingsViewModel =
        NotificationSettingsViewModel(graph.settings, system).also { keepCollecting(it.uiState) }

    @Test
    fun turningPushOffDimsTheRestWithoutChangingThem() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph)
        vm.setCategoryEnabled(NotificationCategory.DIGEST, false)

        vm.setPushEnabled(false)

        val state = vm.uiState.value
        assertFalse(state.categoriesEnabled)
        assertFalse(state.preferences.enabled)
        assertFalse(state.isChecked(NotificationCategory.DIGEST))
        assertTrue(state.isChecked(NotificationCategory.MESSAGES))
        assertTrue(state.preferences.previewContent)
    }

    @Test
    fun categoryAndPreviewChangesAreIgnoredWhilePushIsOff() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph)
        vm.setPushEnabled(false)

        vm.setCategoryEnabled(NotificationCategory.MESSAGES, false)
        vm.setPreviewContent(false)

        val stored = graph.settings.settings.first().notifications
        assertTrue(stored.categories[NotificationCategory.MESSAGES] != false)
        assertTrue(stored.previewContent)
    }

    @Test
    fun turningPushBackOnRestoresThePreviousChoices() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph)
        vm.setCategoryEnabled(NotificationCategory.GROUP_ACTIVITY, false)
        vm.setPreviewContent(false)
        vm.setPushEnabled(false)

        vm.setPushEnabled(true)

        val state = vm.uiState.value
        assertTrue(state.categoriesEnabled)
        assertFalse(state.isChecked(NotificationCategory.GROUP_ACTIVITY))
        assertTrue(state.isChecked(NotificationCategory.SCHEDULE_REMINDERS))
        assertFalse(state.preferences.previewContent)
    }

    @Test
    fun androidBlocksAreReadAgainOnResume() = runTest {
        val vm = viewModel(DemoGraph(backgroundScope))
        assertTrue(vm.uiState.value.systemAllowed)

        system.allowed = false
        system.blocked = setOf(NotificationCategory.DIGEST.id)
        vm.refreshSystemState()

        assertFalse(vm.uiState.value.systemAllowed)
        assertEquals(setOf("digest"), vm.uiState.value.blockedChannels)
        assertFalse(vm.uiState.value.categoriesEnabled, "switches can't promise what Android blocks")
        assertTrue(vm.uiState.value.preferences.enabled, "the saved choice is kept for when Android allows it")

        system.allowed = true
        vm.refreshSystemState()
        assertTrue(vm.uiState.value.categoriesEnabled)
    }

    @Test
    fun categoryCopyCoversEveryCategoryOnce() {
        assertEquals(NotificationCategory.entries.toSet(), CategoryCopy.entries.map { it.category }.toSet())
        assertEquals(NotificationCategory.entries.size, CategoryCopy.entries.size)
    }
}
