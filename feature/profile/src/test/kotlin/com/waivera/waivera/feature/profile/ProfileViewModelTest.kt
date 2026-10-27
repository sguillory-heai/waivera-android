package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class ProfileViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val organizations = FakeOrganizationSource()
    private val realtime = FakeRealtimeStatus()

    private fun TestScope.viewModel(graph: DemoGraph = DemoGraph(backgroundScope)): ProfileViewModel {
        val vm = ProfileViewModel(
            session = graph.session,
            profiles = profiles,
            organizations = organizations,
            calendar = graph.calendar,
            people = graph.people,
            realtime = realtime,
            clock = TestClock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun ProfileViewModel.ready(): ProfileUiState.Ready = assertIs<ProfileUiState.Ready>(uiState.value)

    @Test
    fun openingIssuesFourRequestsAndRendersEverySection() = runTest {
        val vm = viewModel()

        assertEquals(1, profiles.refreshCalls)
        assertEquals(1, profiles.timeOffCalls)
        assertEquals(1, profiles.fileCalls)
        assertEquals(1, organizations.calls)
        val state = vm.ready()
        assertIs<Section.Ready<*>>(state.details)
        assertIs<Section.Ready<*>>(state.timeOff)
        assertIs<Section.Ready<*>>(state.files)
        assertEquals(Section.Ready(OrganizationInfo(1, "Waivera Care")), state.organization)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun loadingShowsEachSectionLoadingWithoutBlockingTheOthers() = runTest {
        val gate = CompletableDeferred<Unit>()
        profiles.gate = gate
        val vm = viewModel()

        val loading = vm.ready()
        assertTrue(loading.isRefreshing)
        assertEquals(Section.Loading, loading.timeOff)
        assertEquals(Section.Loading, loading.files)
        // The organization request is not gated, so it lands while the others still load.
        assertIs<Section.Ready<*>>(loading.organization)

        gate.complete(Unit)
        assertFalse(vm.ready().isRefreshing)
        assertIs<Section.Ready<*>>(vm.ready().timeOff)
    }

    @Test
    fun refreshStaysBusyUntilAllFourSettleAndReissuesAll() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        profiles.gate = gate

        vm.refresh()
        vm.refresh() // a second tap while busy is ignored

        assertTrue(vm.ready().isRefreshing)
        gate.complete(Unit)
        assertFalse(vm.ready().isRefreshing)
        assertEquals(2, profiles.refreshCalls)
        assertEquals(2, profiles.timeOffCalls)
        assertEquals(2, profiles.fileCalls)
        assertEquals(2, organizations.calls)
    }

    @Test
    fun profileErrorShowsOneErrorAndRetryReissuesAllFour() = runTest {
        val cold = FakeProfileRepository(profile = null).apply { refreshError = WaiveraError.Server(500) }
        val graph = DemoGraph(backgroundScope)
        val vm = ProfileViewModel(graph.session, cold, organizations, graph.calendar, graph.people, realtime, TestClock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertIs<Section.Failed>(vm.ready().details)

        cold.refreshError = null
        cold.nextProfile = DemoData.profile()
        vm.refresh()

        assertEquals(2, cold.refreshCalls)
        assertEquals(2, cold.timeOffCalls)
        assertEquals(2, cold.fileCalls)
        assertEquals(2, organizations.calls)
        assertIs<Section.Ready<*>>(vm.ready().details)
    }

    @Test
    fun aFailedRefreshKeepsCachedDetailsAndSaysSoInline() = runTest {
        val vm = viewModel()
        profiles.refreshError = WaiveraError.Network()
        profiles.timeOffResult = Result.failure(WaiveraError.Network())

        vm.refresh()

        val state = vm.ready()
        assertIs<Section.Ready<*>>(state.details)
        assertIs<WaiveraError.Network>(state.detailsRefreshError)
        // Time off had loaded before, so the last good copy stays.
        assertIs<Section.Ready<*>>(state.timeOff)
    }

    @Test
    fun sectionErrorsStayInsideTheirSection() = runTest {
        profiles.filesResult = Result.failure(WaiveraError.Timeout())
        val vm = viewModel()

        val state = vm.ready()
        assertIs<Section.Failed>(state.files)
        assertIs<Section.Ready<*>>(state.details)
        assertIs<Section.Ready<*>>(state.timeOff)
    }

    @Test
    fun missingBackendIdShowsOneUnavailableState() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.session.signOut()

        val vm = viewModel(graph)

        assertEquals(ProfileUiState.Unavailable, vm.uiState.value)
        assertEquals(0, profiles.refreshCalls)
    }

    @Test
    fun twelveFilesShowExactlyFive() = runTest {
        profiles.filesResult = Result.success((1..12).map(::file))
        val vm = viewModel()

        assertEquals(5, (vm.ready().files as Section.Ready).value.size)
    }

    @Test
    fun noFilesIsAnEmptyListNotAnError() = runTest {
        profiles.filesResult = Result.success(emptyList())
        val vm = viewModel()

        assertEquals(Section.Ready(emptyList()), vm.ready().files)
    }

    @Test
    fun emptyTimeOffOmitsTheCard() = runTest {
        profiles.timeOffResult = Result.success(TimeOffSummary())
        val vm = viewModel()

        val summary = (vm.ready().timeOff as Section.Ready).value
        assertFalse(TimeOffMath.hasContent(summary))
    }

    @Test
    fun unknownOrganizationIsUnavailableWithNoNameFallback() = runTest {
        organizations.result = Result.success(null)
        val vm = viewModel()

        assertEquals(Section.Ready(null), vm.ready().organization)
    }

    @Test
    fun reconnectRefreshesAndOfflineShowsTheBanner() = runTest {
        val vm = viewModel()

        realtime.set(ConnectionState.Reconnecting(attempt = 1, delayMillis = 2_000), online = true)
        assertEquals(ConnectionBannerState.Reconnecting, vm.ready().connection)
        realtime.set(ConnectionState.Connected)

        assertEquals(2, profiles.refreshCalls)
        assertEquals(ConnectionBannerState.Connected, vm.ready().connection)

        realtime.set(ConnectionState.Connected, online = false)
        assertEquals(ConnectionBannerState.Offline, vm.ready().connection)

        vm.retryConnection()
        assertEquals(1, realtime.retries)
    }

    @Test
    fun zoneIsTheEmployeesSavedZone() = runTest {
        val vm = viewModel()

        assertEquals(DemoData.ZONE, vm.ready().zone)
        assertNull(vm.ready().detailsRefreshError)
    }
}
