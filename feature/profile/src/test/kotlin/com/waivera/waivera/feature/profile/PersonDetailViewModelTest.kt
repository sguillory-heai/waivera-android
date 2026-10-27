package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class PersonDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val realtime = FakeRealtimeStatus()

    /** Details with a gate (loading), a scripted result and a call counter. */
    private class FakeDetails(var result: Result<PersonDetails>) : PersonDetailsSource {
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0

        override suspend fun details(userId: String): Result<PersonDetails> {
            calls++
            gate?.await()
            return result
        }
    }

    /** Fails or holds `openConversation`; everything else is the demo's. */
    private class ScriptedConversations(delegate: ConversationRepository) : ConversationRepository by delegate {
        private val demo = delegate
        var failWith: WaiveraError? = null
        var opened = mutableListOf<List<String>>()

        override suspend fun openConversation(participantIds: List<String>): Result<Long> {
            opened += participantIds
            return failWith?.let { Result.failure(it) } ?: demo.openConversation(participantIds)
        }
    }

    private val priya = PersonDetails(
        displayName = "Priya Raman",
        email = "priya.raman@example.com",
        phoneNumber = "(512) 555-0100",
        roles = listOf("Care lead"),
        organization = null,
    )

    private fun TestScope.viewModel(
        userId: String,
        graph: DemoGraph,
        details: PersonDetailsSource = FakeDetails(Result.success(priya)),
        conversations: ConversationRepository = graph.conversations,
    ): PersonDetailViewModel {
        val vm = PersonDetailViewModel(
            userId, details, conversations, graph.people, graph.session, graph.calendar, realtime, TestClock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun showsTheCachedNameAtOnceWhileDetailsLoad() = runTest {
        val details = FakeDetails(Result.success(priya)).apply { gate = CompletableDeferred() }
        val vm = viewModel(DemoData.PRIYA, DemoGraph(backgroundScope), details)

        val loading = vm.uiState.value
        assertEquals(Section.Loading, loading.details)
        assertEquals("Priya Raman", loading.name)

        requireNotNull(details.gate).complete(Unit)
        assertEquals(Section.Ready(priya), vm.uiState.value.details)
    }

    @Test
    fun errorOffersRetryThatReloads() = runTest {
        val details = FakeDetails(Result.failure(WaiveraError.Server(500)))
        val vm = viewModel(DemoData.PRIYA, DemoGraph(backgroundScope), details)
        assertIs<Section.Failed>(vm.uiState.value.details)

        details.result = Result.success(priya)
        vm.load()

        assertEquals(2, details.calls)
        assertEquals(Section.Ready(priya), vm.uiState.value.details)
    }

    @Test
    fun reconnectRetriesAFailedLoad() = runTest {
        val details = FakeDetails(Result.failure(WaiveraError.Network()))
        val vm = viewModel(DemoData.PRIYA, DemoGraph(backgroundScope), details)

        details.result = Result.success(priya)
        realtime.set(ConnectionState.Reconnecting(1, 1_000))
        realtime.set(ConnectionState.Connected)

        assertEquals(Section.Ready(priya), vm.uiState.value.details)
    }

    @Test
    fun messageOpensTheExistingDirectConversation() = runTest {
        val vm = viewModel(DemoData.PRIYA, DemoGraph(backgroundScope))
        val effect = backgroundScope.launchEffect(vm)

        vm.message()

        assertEquals(PersonDetailEffect.OpenConversation(DemoData.DM_PRIYA), effect.await())
        assertFalse(vm.uiState.value.isOpeningChat)
    }

    @Test
    fun messageCreatesTheConversationWhenThereIsNone() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel("usr_sam_okafor", graph)
        val effect = backgroundScope.launchEffect(vm)

        vm.message()

        val opened = assertIs<PersonDetailEffect.OpenConversation>(effect.await())
        assertNotEquals(DemoData.DM_PRIYA, opened.conversationId)
        assertTrue(graph.backend.conversations.value.containsKey(opened.conversationId))
    }

    @Test
    fun messageFailureStaysOnTheProfileAndSaysWhy() = runTest {
        val graph = DemoGraph(backgroundScope)
        val conversations = ScriptedConversations(graph.conversations).apply { failWith = WaiveraError.Network() }
        val vm = viewModel(DemoData.PRIYA, graph, conversations = conversations)
        val effect = backgroundScope.launchEffect(vm)

        vm.message()

        assertIs<PersonDetailEffect.MessageFailed>(effect.await())
        assertEquals(listOf(listOf(DemoData.PRIYA)), conversations.opened)
        assertFalse(vm.uiState.value.isOpeningChat)
    }

    @Test
    fun ownEntryHasNoMessageButton() = runTest {
        val vm = viewModel(DemoData.ME, DemoGraph(backgroundScope))

        assertFalse(vm.uiState.value.canMessage)
        vm.message()
        assertFalse(vm.uiState.value.isOpeningChat)
    }

    private fun CoroutineScope.launchEffect(vm: PersonDetailViewModel) =
        async(start = CoroutineStart.UNDISPATCHED) { vm.effect.first() }
}
