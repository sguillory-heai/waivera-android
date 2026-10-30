package com.waivera.waivera.feature.chat.conversation

import app.cash.turbine.Event
import app.cash.turbine.test
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.feedback.HapticMoment
import com.waivera.waivera.core.network.dto.MessageType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.chat.FakeAttachmentIo
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.RecordingConversations
import com.waivera.waivera.feature.chat.message.AttachmentDownloads
import com.waivera.waivera.feature.chat.message.ChatClock
import com.waivera.waivera.feature.chat.message.ChatEffect
import com.waivera.waivera.feature.chat.message.ChatSheet
import com.waivera.waivera.feature.chat.message.ChatTime
import com.waivera.waivera.feature.chat.message.ReadOnlyReason
import com.waivera.waivera.feature.chat.message.SendErrorKind
import com.waivera.waivera.feature.chat.message.TimelineItem
import com.waivera.waivera.feature.chat.message.plainText
import com.waivera.waivera.feature.chat.testChatClock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Subject(
        val viewModel: ConversationViewModel,
        val graph: DemoGraph,
        val conversations: RecordingConversations,
    )

    private fun TestScope.subject(
        conversationId: Long = DemoData.DM_PRIYA,
        lobby: Result<String>? = Result.success("https://app.example.test/lobby/11"),
        prepare: (DemoGraph) -> Unit = {},
        clock: (DemoGraph) -> ChatClock = { testChatClock() },
    ): Subject {
        val graph = DemoGraph(backgroundScope)
        prepare(graph)
        val conversations = RecordingConversations(graph.conversations, lobby)
        val viewModel = ConversationViewModel(
            conversationId = conversationId,
            conversations = conversations,
            messages = graph.messages,
            bookmarks = graph.bookmarks,
            people = graph.people,
            session = graph.session,
            realtime = graph.realtimeStatus,
            io = FakeAttachmentIo(),
            downloads = AttachmentDownloads(graph.attachments),
            clock = clock(graph),
            computeDispatcher = main.testDispatcher,
            titles = EnglishConversationTitles,
        )
        runCurrent()
        return Subject(viewModel, graph, conversations)
    }

    private val ConversationUiState.rows get() = items.filterIsInstance<TimelineItem.MessageRow>()

    // region Loading, empty, error, reconnect

    @Test
    fun loadingWhileTheFirstSyncRuns() = runTest {
        val s = subject(conversationId = UNKNOWN) { it.backend.syncStatus.value = SyncStatus.Refreshing }
        assertEquals(ConversationContent.Loading, s.viewModel.state.value.content)
    }

    @Test
    fun emptyConversationShowsTheEmptyState() = runTest {
        val s = subject(conversationId = UNKNOWN)
        assertEquals(ConversationContent.Empty, s.viewModel.state.value.content)
    }

    @Test
    fun failedFirstLoadShowsTheErrorWithClientCopy() = runTest {
        val error = WaiveraError.Network()
        val s = subject(conversationId = UNKNOWN) { it.backend.syncStatus.value = SyncStatus.Failed(error) }
        assertEquals(ConversationContent.Error(error.messageRes), s.viewModel.state.value.content)
    }

    @Test
    fun failedRefreshKeepsCachedRowsAndMarksThemStale() = runTest {
        val s = subject { it.backend.syncStatus.value = SyncStatus.Failed(WaiveraError.Timeout()) }
        val state = s.viewModel.state.value
        assertEquals(ConversationContent.Messages, state.content)
        assertTrue(state.isStale)
    }

    @Test
    fun groupHeaderUsesTheSharedTitleAndCountsMembers() = runTest {
        val group = subject(conversationId = DemoData.GROUP).viewModel.state.value
        assertEquals("Priya Raman, Jordan Lee", group.title, "the same string as the inbox row")
        assertEquals(3, group.memberCount)
        assertFalse(group.isDirect)
        val dm = subject().viewModel.state.value
        assertEquals("Priya Raman", dm.title)
        assertNull(dm.memberCount)
        assertTrue(dm.isDirect)
    }

    @Test
    fun reconnectingAndBackShowsTheBannerThenClearsIt() = runTest {
        val s = subject()
        assertEquals(ConnectionBannerState.Connected, s.viewModel.state.value.connection.banner)
        s.graph.realtime.drop()
        runCurrent()
        assertEquals(ConnectionBannerState.Reconnecting, s.viewModel.state.value.connection.banner)
        s.viewModel.retryConnection()
        runCurrent()
        assertEquals(ConnectionBannerState.Connected, s.viewModel.state.value.connection.banner)
    }

    @Test
    fun aNonPositiveIdIsInvalidAndLoadsNothing() = runTest {
        val s = subject(conversationId = 0)
        assertEquals(ConversationContent.Invalid, s.viewModel.state.value.content)
        s.viewModel.send("hello")
        runCurrent()
        assertTrue(s.graph.backend.messages.value[0L].isNullOrEmpty())
    }

    // endregion

    // region Timeline

    @Test
    fun threadRepliesStayOutOfTheMainTimeline() = runTest {
        val rows = subject().viewModel.state.value.rows
        val ids = rows.map { it.message.id }
        assertTrue(DemoData.THREAD_ROOT in ids)
        assertFalse(9331L in ids)
        assertFalse(9332L in ids)
    }

    @Test
    fun rowsAlignByTheBackendUserId() = runTest {
        val rows = subject().viewModel.state.value.rows
        assertTrue(rows.any { it.isMine } && rows.any { !it.isMine })
        rows.forEach { row -> assertEquals(row.message.authorId == DemoData.ME, row.isMine) }
    }

    @Test
    fun bookmarkedIdsAreMergedIntoRows() = runTest {
        val root = subject().viewModel.state.value.rows.single { it.message.id == DemoData.THREAD_ROOT }
        assertTrue(root.message.isBookmarked)
    }

    @Test
    fun aCallRecordRendersAsOneCard() = runTest {
        val state = subject(conversationId = DemoData.GROUP).viewModel.state.value
        val cards = state.items.filterIsInstance<TimelineItem.CallLog>()
        assertEquals(listOf(DemoData.CALL_RECORD), cards.map { it.callRecordId })
        assertEquals("12m 4s", cards.single().summary.duration)
    }

    @Test
    fun aBotTicketMessageRendersAsATicketCard() = runTest {
        val state = subject(conversationId = DemoData.BOT).viewModel.state.value
        assertEquals("WAIV-2527", state.rows.single { it.message.id == 9510L }.ticket?.ticketId)
    }

    @Test
    fun typingShowsOtherPeopleByName() = runTest {
        val s = subject { it.backend.typing.value = mapOf(DemoData.DM_PRIYA to setOf(DemoData.PRIYA, DemoData.ME)) }
        assertEquals(listOf("Priya Raman"), s.viewModel.state.value.typingNames)
    }

    @Test
    fun composerChangesFeedTheThrottledTypingSignal() = runTest {
        val s = subject()
        s.viewModel.onDraftChanged("h")
        s.viewModel.onDraftChanged("")
        assertEquals(listOf(false, true), s.conversations.composerEvents)
    }

    @Test
    fun timestampsToggleByRowKey() = runTest {
        val s = subject()
        val key = s.viewModel.state.value.rows.first().key
        s.viewModel.toggleTimestamp(key)
        assertEquals(setOf(key), s.viewModel.state.value.visibleTimestamps)
        s.viewModel.toggleTimestamp(key)
        assertEquals(emptySet(), s.viewModel.state.value.visibleTimestamps)
    }

    // endregion

    // region Sending

    @Test
    fun sendIsOptimisticClearsTheDraftAndDoesNotBuzz() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.send("  On my way  ")
            runCurrent()
            assertEquals(ChatEffect.SetDraft(""), awaitItem())
            // HAPTIC-SENT: a successful send has no vibration; the row says "Sent".
            assertEquals(ChatEffect.ScrollToLatest, awaitItem())
            expectNoEvents()
        }
        val sent = s.viewModel.state.value.rows.single { it.message.text == "On my way" }
        assertTrue(sent.isMine)
    }

    @Test
    fun aQuickRunOfSendsNeverBuzzes() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            listOf("On my way", "Five minutes out", "Parking now").forEach { text ->
                s.viewModel.send(text)
                runCurrent()
            }
            // HAPTIC-SENT: each send is confirmed by its bubble and its "Sent" line, never a vibration.
            val events = cancelAndConsumeRemainingEvents()
            assertTrue(events.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no sent cue")
        }
        val mine = s.viewModel.state.value.rows.filter { it.isMine }.map { it.message.text }
        assertTrue(mine.containsAll(listOf("On my way", "Five minutes out", "Parking now")))
    }

    @Test
    fun aNewlyFailedSendRaisesTheBannerAndRetryRecovers() = runTest {
        val s = subject()
        assertNull(s.viewModel.state.value.sendError, "a failure already there on open shows inline only")
        s.viewModel.effects.test {
            s.viewModel.send("this will fail")
            runCurrent()
            // Corrects a wrong expectation, it checks no less: the send is optimistic, so the queued
            // state (draft cleared, scroll) comes first and the one failure cue follows it.
            assertEquals(ChatEffect.SetDraft(""), awaitItem())
            assertEquals(ChatEffect.ScrollToLatest, awaitItem())
            assertEquals(ChatEffect.Haptic(HapticMoment.SendFailed), awaitItem())
            val rest = cancelAndConsumeRemainingEvents()
            assertTrue(rest.none { it is Event.Item && it.value is ChatEffect.Haptic }, "one cue per failure")
        }
        assertEquals(SendErrorKind.Server, s.viewModel.state.value.sendError)
        val failed = s.viewModel.state.value.rows.last { it.message.sendState == SendState.FAILED }.message

        s.viewModel.effects.test {
            s.viewModel.retry(requireNotNull(failed.clientMessageId))
            runCurrent()
            // HAPTIC-SENT: the retried row sends without a vibration.
            val events = cancelAndConsumeRemainingEvents()
            assertTrue(events.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no sent cue")
        }
        assertNull(s.viewModel.state.value.sendError)
        assertTrue(
            s.viewModel.state.value.rows.none {
                it.message.clientMessageId == failed.clientMessageId &&
                    it.message.isPending
            },
        )
    }

    @Test
    fun typingAfterAFailureClearsTheBanner() = runTest {
        val s = subject()
        s.viewModel.send("fail please")
        runCurrent()
        assertNotNull(s.viewModel.state.value.sendError)
        s.viewModel.onDraftChanged("x")
        assertNull(s.viewModel.state.value.sendError)
    }

    @Test
    fun discardRemovesTheFailedRow() = runTest {
        val s = subject()
        s.viewModel.discard(DemoData.FAILED_CLIENT_ID)
        runCurrent()
        assertTrue(s.viewModel.state.value.rows.none { it.message.clientMessageId == DemoData.FAILED_CLIENT_ID })
    }

    @Test
    fun editingAFailedRowRestoresItsTextAndDropsIt() = runTest {
        val s = subject()
        val failed = s.viewModel.state.value.rows.single {
            it.message.clientMessageId == DemoData.FAILED_CLIENT_ID
        }.message
        s.viewModel.effects.test {
            s.viewModel.editFailed(failed)
            runCurrent()
            assertEquals(ChatEffect.SetDraft("Sending the updated schedule now"), awaitItem())
        }
        assertTrue(s.viewModel.state.value.rows.none { it.message.clientMessageId == DemoData.FAILED_CLIENT_ID })
    }

    @Test
    fun aStagedImageSendsWithTheMessage() = runTest {
        val s = subject()
        s.viewModel.onImagePicked("content://media/1")
        runCurrent()
        assertNotNull(s.viewModel.state.value.stagedImage)
        s.viewModel.send("")
        runCurrent()
        assertNull(s.viewModel.state.value.stagedImage)
        assertTrue(s.viewModel.state.value.rows.any { it.isMine && it.message.attachment?.originalName == "photo.png" })
    }

    @Test
    fun editingAnOwnMessageSavesTheNewText() = runTest {
        val s = subject()
        val mine = s.viewModel.state.value.rows.first {
            it.isMine && !it.message.isPending && !it.message.isDeleted
        }.message
        s.viewModel.startEdit(mine)
        assertEquals(mine.id, s.viewModel.state.value.editingMessageId)
        s.viewModel.effects.test {
            s.viewModel.send("Corrected text")
            runCurrent()
            // UI4-09: saving an edit is a send, and a successful send has no vibration.
            val events = cancelAndConsumeRemainingEvents()
            assertTrue(events.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no saved cue")
        }
        val edited = s.viewModel.state.value.rows.single { it.message.id == mine.id }.message
        assertEquals("Corrected text", edited.text)
        assertTrue(edited.isEdited)
        assertNull(s.viewModel.state.value.editingMessageId)
    }

    // endregion

    // region Read-only and calls

    @Test
    fun botConversationsAreReadOnlyAndRefuseSends() = runTest {
        val s = subject(conversationId = DemoData.BOT)
        val state = s.viewModel.state.value
        assertEquals(false, state.canWrite)
        assertEquals(ReadOnlyReason.Automated, state.readOnlyReason)
        val before = state.items.size
        s.viewModel.send("hello bot")
        runCurrent()
        assertEquals(before, s.viewModel.state.value.items.size)
    }

    @Test
    fun aLiveCallShowsTheBannerAndOpensTheWebLobby() = runTest {
        val s = subject(conversationId = DemoData.GROUP)
        assertTrue(s.viewModel.state.value.call.isLive)
        s.viewModel.effects.test {
            s.viewModel.openCall()
            runCurrent()
            assertEquals(ChatEffect.OpenUrl("https://app.example.test/lobby/11"), awaitItem())
        }
        assertEquals(listOf(DemoData.GROUP), s.conversations.lobbyCalls)
        assertFalse(s.viewModel.state.value.call.isOpening)
    }

    @Test
    fun anUnavailableLobbyExplainsTheFailure() = runTest {
        // The demo has no web base, like a build without waivera.webBaseUrl.
        val s = subject(conversationId = DemoData.GROUP, lobby = null)
        s.viewModel.effects.test {
            s.viewModel.openCall()
            runCurrent()
            val snackbar = assertIs<ChatEffect.Snackbar>(awaitItem())
            assertEquals(R.string.feature_chat_call_open_failed, snackbar.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Actions

    @Test
    fun reactingWithoutALiveConnectionChangesNothingAndSaysWhy() = runTest {
        val s = subject()
        val target = s.viewModel.state.value.rows.first { !it.message.isPending }.message
        s.graph.realtime.drop()
        runCurrent()
        s.viewModel.effects.test {
            s.viewModel.react(target.id, "👍")
            runCurrent()
            val snackbar = assertIs<ChatEffect.Snackbar>(awaitItem())
            assertEquals(R.string.feature_chat_reaction_failed, snackbar.message)
            assertEquals(R.string.feature_chat_reaction_needs_connection, snackbar.detail)
            // Haptics policy: only a failed send vibrates, never a reaction that could not be sent.
            val rest = cancelAndConsumeRemainingEvents()
            assertTrue(rest.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no reaction cue")
        }
        assertEquals(
            target.reactions,
            s.viewModel.state.value.rows.single {
                it.message.id == target.id
            }.message.reactions,
        )
    }

    @Test
    fun reactingTogglesMyReaction() = runTest {
        val s = subject()
        val target = s.viewModel.state.value.rows.first {
            !it.message.isPending && it.reactions.none { r -> r.mine }
        }.message
        s.viewModel.react(target.id, "❤️")
        runCurrent()
        val after = s.viewModel.state.value.rows.single { it.message.id == target.id }
        assertTrue(after.reactions.any { it.emoji == "❤️" && it.mine })
        s.viewModel.react(target.id, "❤️")
        runCurrent()
        assertTrue(s.viewModel.state.value.rows.single { it.message.id == target.id }.reactions.none { it.mine })
    }

    @Test
    fun deleteAsksFirstThenDeletes() = runTest {
        val s = subject()
        val mine = s.viewModel.state.value.rows.first {
            it.isMine && !it.message.isPending && !it.message.isDeleted
        }.message
        s.viewModel.requestDelete(mine.id)
        assertEquals(ChatSheet.ConfirmDelete(mine.id), s.viewModel.state.value.sheet)
        s.viewModel.confirmDelete()
        runCurrent()
        assertNull(s.viewModel.state.value.sheet)
        assertTrue(s.viewModel.state.value.rows.single { it.message.id == mine.id }.message.isDeleted)
    }

    @Test
    fun theActionSheetOffersEditAndDeleteOnlyOnMyMessages() = runTest {
        val s = subject()
        val rows = s.viewModel.state.value.rows.filter { !it.message.isPending && !it.message.isDeleted }
        s.viewModel.openActions(rows.first { it.isMine }.message)
        val mine = assertIs<ChatSheet.Actions>(s.viewModel.state.value.sheet).model
        assertTrue(mine.canEdit && mine.canDelete && mine.canReply && mine.canReact)
        s.viewModel.openActions(rows.first { !it.isMine }.message)
        val theirs = assertIs<ChatSheet.Actions>(s.viewModel.state.value.sheet).model
        assertFalse(theirs.canEdit || theirs.canDelete)
    }

    @Test
    fun copyPutsThePlainTextOnTheClipboardAndMarksTheSheet() = runTest {
        val s = subject()
        val message = s.viewModel.state.value.rows.first {
            !it.message.isPending &&
                !it.message.isDeleted &&
                it.message.type == MessageType.USER &&
                it.message.plainText().isNotEmpty()
        }.message
        s.viewModel.effects.test {
            // Haptics policy: neither the long press nor the copy vibrates; the sheet says "Copied".
            s.viewModel.openActions(message)
            s.viewModel.copy(message)
            assertIs<ChatEffect.CopyText>(awaitItem())
            expectNoEvents()
        }
        assertTrue(assertIs<ChatSheet.Actions>(s.viewModel.state.value.sheet).model.copied)
    }

    @Test
    fun bookmarkingOffersUndo() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.toggleBookmark(9310)
            runCurrent()
            val snackbar = assertIs<ChatEffect.Snackbar>(awaitItem())
            assertEquals(R.string.feature_chat_bookmark_saved, snackbar.message)
            assertEquals(9310L, snackbar.undoBookmarkOf)
        }
        assertTrue(s.viewModel.state.value.rows.single { it.message.id == 9310L }.message.isBookmarked)
        s.viewModel.undoBookmark(9310)
        runCurrent()
        assertFalse(s.viewModel.state.value.rows.single { it.message.id == 9310L }.message.isBookmarked)
    }

    @Test
    fun replyOpensTheThreadAtItsRoot() = runTest {
        val s = subject()
        val root = s.viewModel.state.value.rows.single { it.message.id == DemoData.THREAD_ROOT }.message
        s.viewModel.effects.test {
            s.viewModel.openThread(root)
            assertEquals(ChatEffect.OpenThread(DemoData.THREAD_ROOT), awaitItem())
        }
    }

    @Test
    fun unsafeLinksNeverOpen() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.openLink("javascript:alert(1)")
            s.viewModel.openLink("https://waivera.example/a")
            assertEquals(ChatEffect.OpenUrl("https://waivera.example/a"), awaitItem())
        }
    }

    // endregion

    // region Highlight and read state

    @Test
    fun highlightScrollsThenClearsAfterThreeSeconds() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.highlight(9320)
            runCurrent()
            advanceTimeBy(ConversationViewModel.HIGHLIGHT_SCROLL_DELAY_MILLIS + 1)
            val scroll = assertIs<ChatEffect.ScrollTo>(awaitItem())
            assertEquals(scroll.key, s.viewModel.state.value.highlightKey)
            advanceTimeBy(ConversationViewModel.HIGHLIGHT_MILLIS + 1)
            assertNull(s.viewModel.state.value.highlightKey)
        }
    }

    @Test
    fun highlightingACallMessageHighlightsItsCard() = runTest {
        val s = subject(conversationId = DemoData.GROUP)
        s.viewModel.highlight(9501)
        runCurrent()
        advanceTimeBy(ConversationViewModel.HIGHLIGHT_SCROLL_DELAY_MILLIS + 1)
        assertEquals("call-${DemoData.CALL_RECORD}", s.viewModel.state.value.highlightKey)
    }

    @Test
    fun highlightingAThreadReplyOpensItsThread() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.highlight(9331)
            runCurrent()
            assertEquals(ChatEffect.OpenThread(DemoData.THREAD_ROOT), awaitItem())
        }
    }

    @Test
    fun aMissingHighlightTargetKeepsTheConversationAndSaysSo() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.highlight(123_456)
            runCurrent()
            advanceTimeBy(ConversationViewModel.HIGHLIGHT_WAIT_MILLIS * 2)
            val note = assertIs<ChatEffect.Snackbar>(expectMostRecentItem())
            assertEquals(R.string.feature_chat_message_missing, note.message)
        }
        assertNull(s.viewModel.state.value.highlightKey)
        assertEquals(ConversationContent.Messages, s.viewModel.state.value.content)
    }

    @Test
    fun readThroughRunsOnceForTheNewestMessageAndRetriesAfterAFailure() = runTest {
        val s = subject()
        s.conversations.failing = true
        s.viewModel.onResumed()
        runCurrent()
        assertEquals(1, s.conversations.markReadCalls.size)
        val newest = s.conversations.markReadCalls.single()
        assertTrue(newest > 0, "outbox rows are never marked read")

        s.conversations.failing = false
        s.viewModel.onPaused()
        s.viewModel.onResumed()
        runCurrent()
        assertEquals(listOf(newest, newest), s.conversations.markReadCalls)

        s.viewModel.onResumed()
        runCurrent()
        assertEquals(2, s.conversations.markReadCalls.size, "the same newest message is not sent twice")
    }

    @Test
    fun nothingIsMarkedReadWhileTheScreenIsPaused() = runTest {
        val s = subject()
        assertTrue(s.conversations.markReadCalls.isEmpty())
    }

    // endregion

    // region Receive (silent)

    private fun DemoGraph.incoming(text: String, ageSeconds: Long = 0) = Message(
        id = backend.nextMessageId(),
        conversationId = DemoData.DM_PRIYA,
        authorId = DemoData.PRIYA,
        text = text,
        createdAt = backend.now().minusSeconds(ageSeconds),
    )

    @Test
    fun someoneElsesMessageArrivingOnScreenNeverVibrates() = runTest {
        val s = subject()
        s.viewModel.onResumed()
        runCurrent()
        s.viewModel.effects.test {
            s.graph.backend.append(s.graph.incoming("Running ten minutes late"))
            runCurrent()
            // Haptics policy: the bubble's arrival motion is the cue, never a vibration.
            val events = cancelAndConsumeRemainingEvents()
            assertTrue(events.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no receive cue")
        }
        assertTrue(s.viewModel.state.value.rows.any { it.message.text == "Running ten minutes late" })
    }

    @Test
    fun arrivalsWhilePausedAndOlderBackfillStayQuiet() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.graph.backend.append(s.graph.incoming("While you were away"))
            runCurrent()
            s.viewModel.onResumed()
            runCurrent()
            s.graph.backend.append(s.graph.incoming("From last week", ageSeconds = BACKFILL_AGE_SECONDS))
            runCurrent()
            expectNoEvents()
        }
    }

    // endregion

    // region Saved zone

    @Test
    fun timesFollowTheEmployeesSavedZoneNotTheDeviceZone() = runTest {
        val device = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            // The DI wiring: the employee's clock over the session (Chicago in the demo).
            val s = subject(clock = { ChatClock.employee(Clock.fixed(DemoData.NOW, ZoneOffset.UTC), it.session) })
            // 1 November 10:29 CST in Chicago, but already 2 November 01:29 in Tokyo.
            val at = DemoData.NOW.minusSeconds(60)
            s.graph.backend.append(
                Message(
                    id = s.graph.backend.nextMessageId(),
                    conversationId = DemoData.DM_PRIYA,
                    authorId = DemoData.PRIYA,
                    text = "Saved zone",
                    createdAt = at,
                ),
            )
            runCurrent()

            val time = s.viewModel.rowTime()
            assertEquals(DemoData.ZONE, time.zone)
            assertEquals("10:29 AM", ChatTime.clock(at, time.zone, Locale.US))
            val days = s.viewModel.state.value.items.filterIsInstance<TimelineItem.DaySeparator>().map { it.day }
            assertTrue(LocalDate.of(2026, 11, 1) in days, "$days")
            assertFalse(LocalDate.of(2026, 11, 2) in days, "no Tokyo day: $days")
        } finally {
            TimeZone.setDefault(device)
        }
    }

    // endregion

    private companion object {
        const val UNKNOWN = 999L
        const val BACKFILL_AGE_SECONDS = 7 * 24 * 3_600L
    }
}
