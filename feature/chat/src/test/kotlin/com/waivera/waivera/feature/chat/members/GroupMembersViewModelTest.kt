package com.waivera.waivera.feature.chat.members

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.list.FakeConversationRepository
import com.waivera.waivera.feature.chat.list.FakeRealtimeStatusRepository
import com.waivera.waivera.feature.chat.list.FakeSessionRepository
import com.waivera.waivera.feature.chat.list.JORDAN
import com.waivera.waivera.feature.chat.list.ME
import com.waivera.waivera.feature.chat.list.NOW
import com.waivera.waivera.feature.chat.list.PRIYA
import com.waivera.waivera.feature.chat.list.jordan
import com.waivera.waivera.feature.chat.list.me
import com.waivera.waivera.feature.chat.list.priya
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The group members list (v7, item 4): loading, empty, error and reconnect, the sort (me first,
 * then by name), the search threshold, and Message opening the direct chat through the same
 * repository call New chat makes, reusing one that exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembersViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val mei = Person("usr_mei_chen", "Mei", "Chen", "Mei Chen")
    private val sam = Person("usr_sam_okafor", "sam", "okafor", "sam okafor")

    private fun group(people: List<Person>) = Conversation(GROUP, ConversationKind.GROUP, people)

    private val conversations = FakeConversationRepository(
        Synced(listOf(group(listOf(jordan, priya, me, mei))), lastSyncedAt = NOW),
    )
    private val realtime = FakeRealtimeStatusRepository()

    private fun TestScope.viewModel(
        repository: ConversationRepository = conversations,
        session: SessionRepository = FakeSessionRepository(),
        status: RealtimeStatusRepository = realtime,
        id: Long = GROUP,
    ): GroupMembersViewModel {
        val vm = GroupMembersViewModel(id, repository, session, status)
        runCurrent()
        return vm
    }

    // region Loading, empty, error, reconnect

    @Test
    fun loadingWhileAnUncachedGroupIsFetched() = runTest {
        conversations.setConversations(emptyList())
        val gate = CompletableDeferred<Unit>()
        conversations.hydrationGate = gate
        val state = viewModel().state.value
        assertEquals(MembersPhase.Loading, state.phase)
        assertEquals(listOf(GROUP), conversations.hydrationCalls)
        gate.complete(Unit)
    }

    @Test
    fun aGroupWithNoOneShowsTheEmptyState() = runTest {
        conversations.setConversations(listOf(group(listOf(Person(" ")))))
        val state = viewModel().state.value
        assertEquals(MembersPhase.Empty, state.phase)
        assertFalse(state.isNoMatch, "an empty group is not a search with no match")
        assertEquals(0, state.totalCount)
    }

    @Test
    fun aFailedFirstLoadShowsTheErrorWithClientCopy() = runTest {
        conversations.setConversations(emptyList())
        val vm = viewModel()
        val state = vm.state.value
        assertEquals(MembersPhase.Error, state.phase)
        assertEquals(WaiveraError.NotFound().messageRes, state.errorRes)
        conversations.setConversations(listOf(group(listOf(me, priya, jordan))))
        vm.refresh()
        runCurrent()
        assertEquals(MembersPhase.Content, vm.state.value.phase, "try again recovers")
        assertNull(vm.state.value.errorRes)
    }

    @Test
    fun aCachedGroupShowsAtOnceWithoutAFetch() = runTest {
        val state = viewModel().state.value
        assertEquals(MembersPhase.Content, state.phase)
        assertTrue(conversations.hydrationCalls.isEmpty())
    }

    @Test
    fun reconnectingShowsTheBannerAndRefetchesWhenBack() = runTest {
        val vm = viewModel()
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(1, 1_000), isOnline = true)
        runCurrent()
        assertEquals(ConnectionBannerState.Reconnecting, vm.state.value.connection.banner)
        assertTrue(conversations.hydrationCalls.isEmpty())
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = true)
        runCurrent()
        assertEquals(ConnectionBannerState.Connected, vm.state.value.connection.banner)
        assertEquals(listOf(GROUP), conversations.hydrationCalls, "back online, the members are fetched again")
        assertEquals(MembersPhase.Content, vm.state.value.phase)
    }

    // endregion

    // region Order and search

    @Test
    fun meFirstThenEveryoneByNameIgnoringCase() = runTest {
        conversations.setConversations(listOf(group(listOf(sam, jordan, priya, me, mei, priya))))
        val state = viewModel().state.value
        assertEquals(listOf(ME, JORDAN, mei.id, PRIYA, sam.id), state.rows.map { it.userId })
        assertEquals(listOf(true, false, false, false, false), state.rows.map { it.isMe })
        assertEquals(5, state.totalCount, "a duplicate participant counts once")
    }

    @Test
    fun searchShowsOnlyPastTenMembersAndFiltersByName() = runTest {
        val ten = (1..9).map { Person("usr_$it", displayName = "Care worker $it") } + me
        conversations.setConversations(listOf(group(ten)))
        val vm = viewModel()
        assertFalse(vm.state.value.showsSearch, "ten members need no search")
        conversations.setConversations(listOf(group(ten + priya)))
        runCurrent()
        assertTrue(vm.state.value.showsSearch)
        vm.onQueryChange("  PRIYA ")
        runCurrent()
        assertEquals(listOf(PRIYA), vm.state.value.rows.map { it.userId })
        vm.onQueryChange("nobody")
        runCurrent()
        assertEquals(MembersPhase.Empty, vm.state.value.phase)
        assertTrue(vm.state.value.isNoMatch)
        assertEquals(11, vm.state.value.totalCount)
    }

    // endregion

    // region Message

    @Test
    fun messageOpensTheExistingDirectChat() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph.conversations, graph.session, graph.realtimeStatus, id = DemoData.GROUP)
        val effects = mutableListOf<GroupMembersEffect>()
        backgroundScope.launch { vm.effects.toList(effects) }
        vm.onMessage(DemoData.PRIYA)
        runCurrent()
        assertEquals(listOf<GroupMembersEffect>(GroupMembersEffect.OpenConversation(DemoData.DM_PRIYA)), effects)
        assertNull(vm.state.value.openingChatWith)
    }

    @Test
    fun messageStartsADirectChatWhenThereIsNone() = runTest {
        val graph = DemoGraph(backgroundScope)
        val before = graph.conversations.observeConversations().first().data.map { it.id }.toSet()
        val vm = viewModel(graph.conversations, graph.session, graph.realtimeStatus, id = DemoData.GROUP)
        val effects = mutableListOf<GroupMembersEffect>()
        backgroundScope.launch { vm.effects.toList(effects) }
        vm.onMessage(DemoData.JORDAN)
        runCurrent()
        val opened = (effects.single() as GroupMembersEffect.OpenConversation).conversationId
        assertFalse(opened in before, "a new chat was started")
        val chat = graph.conversations.observeConversation(opened).first()
        assertEquals(ConversationKind.DIRECT, chat?.kind)
        assertEquals(setOf(DemoData.ME, DemoData.JORDAN), chat?.participants?.map { it.id }?.toSet())
    }

    @Test
    fun messageUsesNewChatsOnePersonCall() = runTest {
        conversations.openResult = Result.success(DM)
        val vm = viewModel()
        vm.onMessage(PRIYA)
        runCurrent()
        assertEquals(listOf(listOf(PRIYA)), conversations.openCalls)
    }

    @Test
    fun thereIsNoMessageForMe() = runTest {
        val vm = viewModel()
        vm.onMessage(ME)
        runCurrent()
        assertTrue(conversations.openCalls.isEmpty())
        assertNull(vm.state.value.openingChatWith)
    }

    @Test
    fun oneChatOpensAtATime() = runTest {
        conversations.openResult = Result.success(DM)
        val gate = CompletableDeferred<Unit>()
        val slow = object : ConversationRepository by conversations {
            override suspend fun openConversation(participantIds: List<String>): Result<Long> {
                gate.await()
                return conversations.openConversation(participantIds)
            }
        }
        val gated = viewModel(repository = slow)
        gated.onMessage(PRIYA)
        runCurrent()
        assertEquals(PRIYA, gated.state.value.openingChatWith)
        gated.onMessage(JORDAN)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(listOf(PRIYA)), conversations.openCalls)
        assertNull(gated.state.value.openingChatWith)
    }

    @Test
    fun aFailedOpenSaysSoAndCanBeDismissed() = runTest {
        conversations.openResult = Result.failure(WaiveraError.Conflict())
        val vm = viewModel()
        vm.onMessage(PRIYA)
        runCurrent()
        assertEquals(R.string.feature_chat_newchat_create_conflict, vm.state.value.messageErrorRes)
        assertNull(vm.state.value.openingChatWith)
        vm.dismissMessageError()
        runCurrent()
        assertNull(vm.state.value.messageErrorRes)
    }

    // endregion

    private companion object {
        const val GROUP = 11L
        const val DM = 7L
    }
}
