package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.chat.newchat.NewChatEffect
import com.waivera.waivera.feature.chat.newchat.NewChatViewModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real ViewModels against the shared demo graph from core:testing. Robolectric, because the
 * list decodes the demo avatar with BitmapFactory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class ChatListViewModelDemoTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun run(block: suspend TestScope.() -> Unit) = runTest(dispatcher, testBody = block)

    private fun TestScope.listViewModel(graph: DemoGraph) = ChatListViewModel(
        conversations = graph.conversations,
        session = graph.session,
        realtime = graph.realtimeStatus,
        people = graph.people,
        text = EnglishText,
        clock = graph.backend.clock,
    )

    @Test
    fun demoInboxRendersEveryKindOfRow() = run {
        val graph = DemoGraph(backgroundScope)
        val state = listViewModel(graph).state.value
        assertEquals(ChatListContent.Rows, state.content)
        val rows = state.rows.associateBy { it.id }
        assertEquals(RowLeading.Bot, rows.getValue(DemoData.BOT).leading)
        assertTrue(rows.getValue(DemoData.CHANNEL).isChannel)
        assertTrue(rows.getValue(DemoData.GROUP).isCallLive)
        assertTrue(rows.getValue(DemoData.GROUP).unreadCount > 0)
        assertEquals("Priya Raman", rows.getValue(DemoData.DM_PRIYA).title)
    }

    @Test
    fun demoLoadingWithNoCacheShowsLoading() = run {
        val graph = DemoGraph(backgroundScope)
        graph.backend.conversations.value = emptyMap()
        graph.backend.syncStatus.value = SyncStatus.Refreshing
        assertEquals(ChatListContent.Loading, listViewModel(graph).state.value.content)
        graph.backend.syncStatus.value = SyncStatus.Idle
    }

    @Test
    fun demoHubDropShowsReconnectingAndRetryReconnects() = run {
        val graph = DemoGraph(backgroundScope)
        val vm = listViewModel(graph)
        graph.realtime.drop()
        assertEquals(ConnectionUi.Reconnecting, vm.state.value.connection)
        vm.onRetryConnection()
        assertEquals(ConnectionUi.Online, vm.state.value.connection)
    }

    @Test
    fun markReadClearsTheDemoBadge() = run {
        val graph = DemoGraph(backgroundScope)
        val vm = listViewModel(graph)
        vm.onMarkRead(DemoData.GROUP)
        assertEquals(0, vm.state.value.rows.first { it.id == DemoData.GROUP }.unreadCount)
        assertEquals(0, graph.backend.conversation(DemoData.GROUP)?.unreadCount)
    }

    @Test
    fun choosingSomeoneWithAnExistingDirectChatOpensItWithoutCreatingOne() = run {
        val graph = DemoGraph(backgroundScope)
        val before = graph.backend.conversations.value.keys
        val vm = NewChatViewModel(graph.people, graph.conversations, graph.session, graph.backend.clock)
        val effects = mutableListOf<NewChatEffect>()
        backgroundScope.launch { vm.effects.toList(effects) }
        vm.onToggle(DemoData.PRIYA)
        vm.onSubmit()
        assertEquals(listOf<NewChatEffect>(NewChatEffect.OpenConversation(DemoData.DM_PRIYA)), effects)
        assertEquals(before, graph.backend.conversations.value.keys)
    }

    @Test
    fun choosingSeveralPeopleCreatesAGroup() = run {
        val graph = DemoGraph(backgroundScope)
        val vm = NewChatViewModel(graph.people, graph.conversations, graph.session, graph.backend.clock)
        val effects = mutableListOf<NewChatEffect>()
        backgroundScope.launch { vm.effects.toList(effects) }
        vm.onToggle("usr_sam_okafor")
        vm.onToggle("usr_mei_chen")
        vm.onSubmit()
        val opened = (effects.single() as NewChatEffect.OpenConversation).conversationId
        assertTrue(opened !in DemoData.conversations().map { it.id })
    }
}
