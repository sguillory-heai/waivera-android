package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class PeopleFilterTest {
    private val people = listOf(
        Person("u1", displayName = "Priya Raman", email = "priya.raman@example.com"),
        Person("u2", displayName = "Jordan Lee", email = "jordan@example.com"),
        Person("me", displayName = "Alex Rivera"),
        Person("u3", displayName = "  "),
        Person("", displayName = "No Id"),
    )

    @Test
    fun dropsBlankNamesMissingIdsAndTheSignedInPerson() {
        assertEquals(listOf("u1", "u2"), PeopleFilter.apply(people, myUserId = "me", query = "").map { it.id })
    }

    @Test
    fun searchIsCaseInsensitiveSubstringOverNameAndEmail() {
        assertEquals(listOf("u1"), PeopleFilter.apply(people, "me", "RAM").map { it.id })
        assertEquals(listOf("u2"), PeopleFilter.apply(people, "me", "jordan@").map { it.id })
        assertTrue(PeopleFilter.apply(people, "me", "zzz").isEmpty())
    }

    @Test
    fun theFirebaseUidIsNeverUsedAsTheIdentity() {
        // Only the backend id is compared; a different id with the same name stays listed.
        assertEquals(3, PeopleFilter.apply(people + Person("firebase-uid", displayName = "Alex Rivera"), "me", "").size)
    }
}

class PeopleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val realtime = FakeRealtimeStatus()

    private fun TestScope.viewModel(graph: DemoGraph): PeopleViewModel {
        val vm = PeopleViewModel(graph.people, graph.session, graph.calendar, realtime, TestClock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun populatedListExcludesMeAndCarriesCelebrations() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph)

        val state = vm.uiState.value
        assertEquals(ListPhase.Content, state.phase)
        assertFalse(state.rows.any { it.userId == DemoData.ME })
        assertEquals(state.rows.size, state.totalCount)
        // Jordan's work anniversary is on the demo "today" (1 November in Chicago).
        assertEquals(5, state.rows.single { it.userId == DemoData.JORDAN }.celebration?.anniversaryYears)
    }

    @Test
    fun searchFiltersAndNoMatchIsAnEmptyStateWithTheTotalKept() = runTest {
        val vm = viewModel(DemoGraph(backgroundScope))

        vm.onQueryChange("pri")
        assertEquals(listOf(DemoData.PRIYA), vm.uiState.value.rows.map { it.userId })

        vm.onQueryChange("nobody-by-this-name")
        assertEquals(ListPhase.Empty, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.totalCount > 0)
    }

    @Test
    fun loadingWhileTheFirstSyncRuns() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.people.value = emptyList()
        graph.backend.syncStatus.value = SyncStatus.Refreshing

        val vm = viewModel(graph)

        assertEquals(ListPhase.Loading, vm.uiState.value.phase)
    }

    @Test
    fun emptyDirectory() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.people.value = emptyList()

        val vm = viewModel(graph)

        assertEquals(ListPhase.Empty, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.totalCount)
    }

    @Test
    fun errorWithNothingCachedThenHeaderRefreshRecovers() = runTest {
        val graph = DemoGraph(backgroundScope)
        val everyone = graph.backend.people.value
        graph.backend.people.value = emptyList()
        graph.backend.failRefreshes = WaiveraError.Network()

        val vm = viewModel(graph)
        assertEquals(ListPhase.Error, vm.uiState.value.phase)

        graph.backend.failRefreshes = null
        graph.backend.people.value = everyone
        vm.refresh()

        assertEquals(ListPhase.Content, vm.uiState.value.phase)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun errorWithCachedPeopleKeepsTheList() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.failRefreshes = WaiveraError.Timeout()

        val vm = viewModel(graph)

        assertEquals(ListPhase.Content, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.error is WaiveraError.Timeout)
    }

    @Test
    fun reconnectRefetches() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.failRefreshes = WaiveraError.Network()
        val vm = viewModel(graph)
        assertTrue(vm.uiState.value.error != null)

        graph.backend.failRefreshes = null
        realtime.set(ConnectionState.Reconnecting(1, 1_000))
        assertEquals(ConnectionBannerState.Reconnecting, vm.uiState.value.connection)
        realtime.set(ConnectionState.Connected)

        assertEquals(null, vm.uiState.value.error)
    }
}
