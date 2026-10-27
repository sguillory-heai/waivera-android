package com.waivera.waivera.feature.auth

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.network.R as NetworkR
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class BootstrapFailedViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = Instant.parse("2026-10-27T15:31:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `error - shows the mapped reason for the failed bootstrap`() {
        val session = FakeSessionRepository(SessionState.BootstrapFailed(WaiveraError.Server(500)))
        val vm = BootstrapFailedViewModel(session, clock)
        assertEquals(NetworkR.string.core_network_error_server, vm.state.value.message)
        assertFalse(vm.state.value.isOffline)
    }

    @Test
    fun `offline failures say so`() {
        val session = FakeSessionRepository(SessionState.BootstrapFailed(WaiveraError.Timeout()))
        val vm = BootstrapFailedViewModel(session, clock)
        assertTrue(vm.state.value.isOffline)
        assertEquals(NetworkR.string.core_network_error_timeout, vm.state.value.message)
    }

    @Test
    fun `loading - retry runs once while in flight`() {
        val session = FakeSessionRepository(SessionState.BootstrapFailed(WaiveraError.Network()))
        val gate = CompletableDeferred<Unit>()
        session.gate = gate
        val vm = BootstrapFailedViewModel(session, clock)
        vm.retry()
        vm.retry()
        assertTrue(vm.state.value.retrying)
        assertEquals(1, session.refreshes)
        gate.complete(Unit)
        assertFalse(vm.state.value.retrying)
    }

    @Test
    fun `reconnect - a retry that fails again updates the reason and buzzes`() = runTest {
        val session = FakeSessionRepository(SessionState.BootstrapFailed(WaiveraError.Network()))
        session.nextRefresh = Result.failure(WaiveraError.Server(503))
        val vm = BootstrapFailedViewModel(session, clock)
        vm.effects.test {
            vm.retry()
            assertEquals(AuthEffect.Warning, awaitItem(), "the server is unreachable; nothing the person did wrong")
        }
        assertEquals(NetworkR.string.core_network_error_server, vm.state.value.message)
        assertFalse(vm.state.value.isOffline)
        assertEquals(now, vm.state.value.lastFailedRetryAt, "a failed retry is visibly different")
    }

    @Test
    fun `the first failure has no retry time`() {
        val session = FakeSessionRepository(SessionState.BootstrapFailed(WaiveraError.Network()))
        assertNull(BootstrapFailedViewModel(session, clock).state.value.lastFailedRetryAt)
    }

    @Test
    fun `reconnect - a retry that works leaves it to the gate`() {
        val session = FakeSessionRepository(SessionState.BootstrapFailed(WaiveraError.Network()))
        session.stateAfterRefresh = SessionState.SignedIn(FakeSessionRepository.USER)
        val vm = BootstrapFailedViewModel(session, clock)
        vm.retry()
        assertFalse(vm.state.value.retrying)
        assertEquals(1, session.refreshes)
        assertTrue(session.state.value is SessionState.SignedIn)
    }
}

class OnboardingRequiredViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val pending = SessionState.SignedIn(FakeSessionRepository.USER.copy(requiresOnboarding = true))

    @Test
    fun `empty - starts with nothing to report`() {
        val vm = OnboardingRequiredViewModel(FakeSessionRepository(pending))
        assertEquals(OnboardingUiState(), vm.state.value)
    }

    @Test
    fun `still pending after a check says so`() {
        val session = FakeSessionRepository(pending)
        val vm = OnboardingRequiredViewModel(session)
        vm.checkAgain()
        assertTrue(vm.state.value.stillPending)
        assertNull(vm.state.value.errorMessage)
        assertEquals(1, session.refreshes)
    }

    @Test
    fun `finished setup clears the notice and the gate moves on`() {
        val session = FakeSessionRepository(pending)
        session.stateAfterRefresh = SessionState.SignedIn(FakeSessionRepository.USER)
        val vm = OnboardingRequiredViewModel(session)
        vm.checkAgain()
        assertEquals(OnboardingUiState(), vm.state.value)
    }

    @Test
    fun `error - a failed check shows the reason, not still pending`() {
        val session = FakeSessionRepository(pending)
        session.nextRefresh = Result.failure(WaiveraError.Network())
        val vm = OnboardingRequiredViewModel(session)
        vm.checkAgain()
        assertFalse(vm.state.value.stillPending)
        assertEquals(NetworkR.string.core_network_error_network, vm.state.value.errorMessage)
    }

    @Test
    fun `loading - one check at a time`() {
        val session = FakeSessionRepository(pending)
        val gate = CompletableDeferred<Unit>()
        session.gate = gate
        val vm = OnboardingRequiredViewModel(session)
        vm.checkAgain()
        vm.checkAgain()
        assertTrue(vm.state.value.checking)
        assertEquals(1, session.refreshes)
        gate.complete(Unit)
        assertFalse(vm.state.value.checking)
    }
}
