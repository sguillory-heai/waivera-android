package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.OngoingCall
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.feature.chat.R
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListStateHolderTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val repo = FakeConversationRepository(Synced(emptyList(), SyncStatus.Refreshing, lastSyncedAt = null))
    private val session = FakeSessionRepository()
    private val realtime = FakeRealtimeStatusRepository()
    private val ticker = MutableStateFlow(NOW)

    private fun TestScope.holder(): ChatListStateHolder = ChatListStateHolder(
        scope = backgroundScope,
        conversations = repo,
        session = session,
        realtime = realtime,
        text = EnglishText,
        clock = clock,
        ticker = ticker,
        locale = Locale.US,
    )

    private fun TestScope.effects(holder: ChatListStateHolder): MutableList<ChatListEffect> {
        val effects = mutableListOf<ChatListEffect>()
        backgroundScope.launch { holder.effects.toList(effects) }
        return effects
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    // Loading, empty, error, reconnect

    @Test
    fun firstLoadWithNothingCachedIsLoading() = run {
        val h = holder()
        assertEquals(ChatListContent.Loading, h.state.value.content)
        assertEquals(1, repo.refreshCalls)
    }

    @Test
    fun emptyAfterASyncShowsNoConversations() = run {
        repo.setConversations(emptyList())
        val h = holder()
        assertEquals(ChatListContent.Empty(EmptyReason.NoConversations), h.state.value.content)
    }

    @Test
    fun failedFirstLoadShowsTheErrorAndRetryRefetches() = run {
        val error = WaiveraError.Network()
        repo.setConversations(emptyList(), SyncStatus.Failed(error), syncedAt = null)
        val h = holder()
        assertEquals(ChatListContent.Error(error.messageRes), h.state.value.content)
        val before = repo.refreshCalls
        h.onRefresh()
        assertEquals(before + 1, repo.refreshCalls)
    }

    @Test
    fun failedRefreshWithCachedRowsKeepsRowsAndShowsTheBanner() = run {
        val error = WaiveraError.Server(500)
        repo.setConversations(listOf(dm(7)), SyncStatus.Failed(error))
        val h = holder()
        assertEquals(ChatListContent.Rows, h.state.value.content)
        assertEquals(error.messageRes, h.state.value.refreshErrorRes)
    }

    @Test
    fun connectionStatesMapToThePillAndRetrySkipsTheBackoff() = run {
        repo.setConversations(listOf(dm(7)))
        val h = holder()
        assertEquals(ConnectionUi.Online, h.state.value.connection)
        realtime.mutableStatus.value = RealtimeStatus(
            ConnectionState.Reconnecting(attempt = 3, delayMillis = 8_000),
            isOnline = true,
        )
        assertEquals(ConnectionUi.Reconnecting, h.state.value.connection)
        // The list is unchanged while reconnecting.
        assertEquals(listOf(7L), h.state.value.rows.map { it.id })
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connecting, isOnline = true)
        assertEquals(ConnectionUi.Connecting, h.state.value.connection)
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = false)
        assertEquals(ConnectionUi.Offline, h.state.value.connection)
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Disconnected, isOnline = true)
        assertEquals(ConnectionUi.Offline, h.state.value.connection)
        h.onRetryConnection()
        assertEquals(1, realtime.retries)
    }

    @Test
    fun coldStartWithACachedInboxRendersRowsBeforeTheNetworkAnswers() = run {
        repo.setConversations(listOf(dm(7), dm(8, other = jordan)), SyncStatus.Refreshing, syncedAt = null)
        val h = holder()
        assertEquals(ChatListContent.Rows, h.state.value.content)
        assertEquals(2, h.state.value.rows.size)
        assertEquals(false, h.state.value.isRefreshing, "background refreshes do not show the pull spinner")
    }

    // Ordering and realtime

    @Test
    fun newestFirstAndARealtimeMessageMovesItsRowToTheTop() = run {
        repo.setConversations(
            listOf(
                dm(1, last = preview(10, at = NOW.minusSeconds(120))),
                dm(2, last = preview(20, at = NOW.minusSeconds(60)), other = jordan),
            ),
        )
        val h = holder()
        assertEquals(listOf(2L, 1L), h.state.value.rows.map { it.id })
        repo.update(1) { it.copy(lastMessage = preview(30, text = "new", at = NOW), unreadCount = 1) }
        assertEquals(listOf(1L, 2L), h.state.value.rows.map { it.id })
        assertEquals("new", h.state.value.rows.first().preview)
    }

    @Test
    fun arrivalWashPlaysForSomeoneElsesNewMessageOnly() = run {
        repo.setConversations(listOf(dm(1, last = preview(10)), dm(2, last = preview(20), other = jordan)))
        val h = holder()
        assertNull(h.state.value.rows.first { it.id == 1L }.arrivalKey, "nothing pulses on first load")
        repo.update(1) { it.copy(lastMessage = preview(11, at = NOW)) }
        repo.update(2) { it.copy(lastMessage = preview(21, author = ME, at = NOW)) }
        assertEquals(11L, h.state.value.rows.first { it.id == 1L }.arrivalKey)
        assertNull(h.state.value.rows.first { it.id == 2L }.arrivalKey)
        advanceTimeBy(ChatListStateHolder.ARRIVAL_MILLIS + 1)
        runCurrent()
        assertNull(h.state.value.rows.first { it.id == 1L }.arrivalKey)
    }

    @Test
    fun relativeTimeLabelsFollowTheSharedTicker() = run {
        repo.setConversations(listOf(dm(1, last = preview(10, at = NOW.minusSeconds(30)))))
        val h = holder()
        assertEquals("Now", h.state.value.rows.single().timeLabel)
        ticker.value = NOW.plusSeconds(150)
        assertEquals("3m", h.state.value.rows.single().timeLabel)
    }

    @Test
    fun typingReplacesThePreviewAndRevertsWhenItStops() = run {
        val group = Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan), lastMessage = preview(5))
        repo.setConversations(listOf(group))
        val h = holder()
        repo.typing.value = mapOf(11L to setOf(PRIYA))
        assertEquals("Priya Raman is typing…", h.state.value.rows.single().typingLabel)
        repo.typing.value = mapOf(11L to setOf(PRIYA, JORDAN))
        assertEquals("Several people are typing…", h.state.value.rows.single().typingLabel)
        repo.typing.value = emptyMap()
        assertNull(h.state.value.rows.single().typingLabel)
    }

    // Row content through the holder

    @Test
    fun rowsCarryIndicatorsForChannelsBotsCallsAndAttachments() = run {
        repo.setConversations(
            listOf(
                channel(
                    41,
                    canWrite = false,
                    last = preview(1, text = null, attachmentType = "image/png", hasAttachment = true),
                ),
                Conversation(14, ConversationKind.DIRECT, listOf(me), lastMessage = preview(2)),
                dm(7).copy(ongoingCall = OngoingCall("room", status = CallRecordStatus.ACTIVE)),
            ),
        )
        val rows = holder().state.value.rows.associateBy { it.id }
        assertTrue(rows.getValue(41).isChannel)
        assertTrue(rows.getValue(41).isReadOnly)
        assertTrue(rows.getValue(41).hasAttachment)
        assertEquals("Photo", rows.getValue(41).preview)
        assertEquals("Waivera", rows.getValue(14).title)
        assertEquals(RowLeading.Bot, rows.getValue(14).leading)
        assertTrue(rows.getValue(7).isCallLive)
        assertEquals(PRIYA, rows.getValue(7).profileUserId)
    }

    // Filters and search

    @Test
    fun unreadFilterShowsAllCaughtUpOnceTheLastUnreadIsRead() = run {
        repo.setConversations(listOf(dm(1, unread = 2), dm(2, other = jordan)))
        val h = holder()
        h.onFilterChange(ChatListFilter.Unread)
        assertEquals(listOf(1L), h.state.value.rows.map { it.id })
        repo.update(1) { it.copy(unreadCount = 0) }
        assertEquals(ChatListContent.Empty(EmptyReason.AllCaughtUp), h.state.value.content)
    }

    @Test
    fun channelsFilterAndSearchCompose() = run {
        repo.setConversations(listOf(channel(41, name = "north-team"), channel(42, name = "south-team"), dm(7)))
        val h = holder()
        h.onFilterChange(ChatListFilter.Channels)
        assertEquals(setOf(41L, 42L), h.state.value.rows.map { it.id }.toSet())
        h.onQueryChange("NORTH")
        assertEquals(listOf(41L), h.state.value.rows.map { it.id })
    }

    @Test
    fun searchMatchesPreviewTextAndShowsNoMatchesWhenNothingFits() = run {
        repo.setConversations(listOf(dm(7, last = preview(1, text = "Hughes visit moved"))))
        val h = holder()
        h.onQueryChange("hughes")
        assertEquals(1, h.state.value.rows.size)
        h.onQueryChange("payroll")
        assertEquals(ChatListContent.Empty(EmptyReason.NoMatches), h.state.value.content)
    }

    // Navigation, refresh policy

    @Test
    fun doubleTapNavigatesExactlyOnceUntilTheListIsVisibleAgain() = run {
        repo.setConversations(listOf(dm(7)))
        val h = holder()
        val effects = effects(h)
        h.onConversationClick(7)
        h.onConversationClick(7)
        assertEquals(listOf<ChatListEffect>(ChatListEffect.OpenConversation(7)), effects)
        h.onResumed()
        h.onConversationClick(7)
        assertEquals(2, effects.size)
    }

    @Test
    fun aListSyncedUnderAMinuteAgoIsNotRefetchedOnStartOrReturn() = run {
        repo.setConversations(listOf(dm(7)), syncedAt = NOW.minusSeconds(30))
        val h = holder()
        assertEquals(0, repo.refreshCalls)
        h.onResumed()
        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun aStaleListRefetchesOnReturn() = run {
        repo.setConversations(listOf(dm(7)), syncedAt = NOW.minusSeconds(30))
        val h = holder()
        repo.setConversations(listOf(dm(7)), syncedAt = NOW.minusSeconds(61))
        h.onResumed()
        assertEquals(1, repo.refreshCalls)
    }

    @Test
    fun pullToRefreshShowsTheSpinnerUntilTheFetchEnds() = run {
        repo.setConversations(listOf(dm(7)))
        val h = holder()
        repo.refreshGate = CompletableDeferred()
        h.onRefresh()
        assertEquals(true, h.state.value.isRefreshing)
        repo.refreshGate?.complete(Unit)
        runCurrent()
        assertEquals(false, h.state.value.isRefreshing)
        assertEquals(1, repo.refreshCalls)
    }

    // Hydration

    @Test
    fun blankPreviewsHydrateAtMostSixAtATimeAndOnlyOnce() = run {
        val sparse = (1L..13L).map { dm(it, last = preview(it * 10, text = null)) }
        repo.hydrationGate = CompletableDeferred()
        repo.setConversations(sparse + dm(50))
        holder()
        assertEquals(6, repo.maxConcurrentHydrations)
        repo.hydrationGate?.complete(Unit)
        runCurrent()
        assertEquals((1L..13L).toList(), repo.hydrationCalls.sorted())
        assertEquals(6, repo.maxConcurrentHydrations)
        // A later emission with the same sparse rows does not hydrate them again.
        repo.update(50) { it.copy(unreadCount = 1) }
        assertEquals(13, repo.hydrationCalls.size)
    }

    // Actions

    @Test
    fun markReadIsOptimisticAndRevertsWithARetryOnFailure() = run {
        repo.setConversations(listOf(dm(7, unread = 4, last = preview(700))))
        repo.markReadResult = Result.failure(WaiveraError.Network())
        val h = holder()
        val effects = effects(h)
        h.onMarkRead(7)
        assertEquals(listOf(7L to 700L), repo.markReadCalls)
        assertEquals(4, h.state.value.rows.single().unreadCount)
        val message = assertIs<ChatListEffect.ShowMessage>(effects.single())
        assertEquals(R.string.feature_chat_list_mark_read_failed, message.messageRes)
        assertEquals(ChatListRetry.MarkRead(7), message.retry)
    }

    @Test
    fun markReadSuccessClearsTheBadgeAtOnce() = run {
        repo.setConversations(listOf(dm(7, unread = 4, last = preview(700))))
        val h = holder()
        h.onMarkRead(7)
        assertEquals(0, h.state.value.rows.single().unreadCount)
        // Once the cache confirms, the optimistic mark is dropped and the cache value shows.
        repo.update(7) { it.copy(unreadCount = 0) }
        assertEquals(0, h.state.value.rows.single().unreadCount)
        repo.update(7) { it.copy(unreadCount = 1, lastMessage = preview(701)) }
        assertEquals(1, h.state.value.rows.single().unreadCount)
    }

    @Test
    fun joiningALiveCallOpensTheLobbyAndNeverTheConversation() = run {
        repo.setConversations(listOf(dm(7).copy(ongoingCall = OngoingCall("room", status = CallRecordStatus.ACTIVE))))
        repo.lobbyGate = CompletableDeferred()
        val h = holder()
        val effects = effects(h)
        h.onJoinCall(7)
        assertTrue(h.state.value.rows.single().isOpeningCall)
        h.onJoinCall(7)
        repo.lobbyGate?.complete(Unit)
        runCurrent()
        assertEquals(
            listOf<ChatListEffect>(ChatListEffect.OpenUrl("https://web.example.test/lobby/room?state=x")),
            effects,
        )
        assertEquals(false, h.state.value.rows.single().isOpeningCall)
        assertEquals(listOf(7L), repo.lobbyCalls, "one lobby request while one is opening")
    }

    @Test
    fun aFailedOrNonHttpsLobbyShowsTheCallErrorWithRetry() = run {
        repo.setConversations(listOf(dm(7).copy(ongoingCall = OngoingCall("room", status = CallRecordStatus.ACTIVE))))
        repo.lobbyResult = Result.success("javascript:alert(1)")
        val h = holder()
        val effects = effects(h)
        h.onJoinCall(7)
        assertEquals(
            listOf<ChatListEffect>(
                ChatListEffect.ShowMessage(R.string.feature_chat_list_call_failed, ChatListRetry.JoinCall(7)),
            ),
            effects,
        )
    }

    @Test
    fun viewProfileOpensThePerson() = run {
        repo.setConversations(listOf(dm(7)))
        val h = holder()
        val effects = effects(h)
        h.onOpenProfile(PRIYA)
        assertEquals(listOf<ChatListEffect>(ChatListEffect.OpenPerson(PRIYA)), effects)
    }

    @Test
    fun accountNameAndAvatarIdsComeFromTheSignedInUserAndVisiblePeers() = run {
        repo.setConversations(listOf(dm(7), dm(8, other = jordan), channel(41)))
        val h = holder()
        assertEquals("Alex Rivera", h.state.value.accountName)
        val ids = mutableListOf<List<String>>()
        backgroundScope.launch { h.avatarIds.toList(ids) }
        assertEquals(setOf(PRIYA, JORDAN, ME), ids.last().toSet())
    }

    @Test
    fun relativeLabelsUseTheEmployeesZone() = run {
        repo.setConversations(listOf(dm(1, last = preview(10, at = Instant.parse("2026-11-01T04:30:00Z")))))
        assertEquals("Yesterday", holder().state.value.rows.single().timeLabel)
    }
}
