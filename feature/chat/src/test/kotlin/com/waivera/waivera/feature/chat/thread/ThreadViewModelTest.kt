package com.waivera.waivera.feature.chat.thread

import app.cash.turbine.Event
import app.cash.turbine.test
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.feedback.HapticMoment
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.chat.FakeAttachmentIo
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.RecordingConversations
import com.waivera.waivera.feature.chat.RecordingThreads
import com.waivera.waivera.feature.chat.ThrowingSends
import com.waivera.waivera.feature.chat.message.AttachmentDownloads
import com.waivera.waivera.feature.chat.message.ChatEffect
import com.waivera.waivera.feature.chat.message.ChatSheet
import com.waivera.waivera.feature.chat.message.ReadOnlyReason
import com.waivera.waivera.feature.chat.message.SendErrorKind
import com.waivera.waivera.feature.chat.message.TimelineItem
import com.waivera.waivera.feature.chat.testChatClock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
class ThreadViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Subject(val viewModel: ThreadViewModel, val graph: DemoGraph, val threads: RecordingThreads)

    private fun TestScope.subject(
        conversationId: Long = DemoData.DM_PRIYA,
        rootId: Long = DemoData.THREAD_ROOT,
        messages: ((MessageRepository) -> MessageRepository)? = null,
        prepare: (DemoGraph) -> Unit = {},
    ): Subject {
        val graph = DemoGraph(backgroundScope)
        prepare(graph)
        val threads = RecordingThreads(graph.threads)
        val viewModel = ThreadViewModel(
            conversationId = conversationId,
            rootMessageId = rootId,
            threads = threads,
            conversations = RecordingConversations(graph.conversations),
            messages = messages?.invoke(graph.messages) ?: graph.messages,
            bookmarks = graph.bookmarks,
            people = graph.people,
            session = graph.session,
            realtime = graph.realtimeStatus,
            io = FakeAttachmentIo(),
            downloads = AttachmentDownloads(graph.attachments),
            clock = testChatClock(),
            computeDispatcher = main.testDispatcher,
            titles = EnglishConversationTitles,
        )
        runCurrent()
        return Subject(viewModel, graph, threads)
    }

    private val ThreadUiState.replyRows get() = replies.filterIsInstance<TimelineItem.MessageRow>()

    // region States

    @Test
    fun loadingUntilTheThreadArrives() = runTest {
        val s = subject(rootId = UNKNOWN_ROOT) { it.backend.syncStatus.value = SyncStatus.Refreshing }
        assertEquals(ThreadContent.Loading, s.viewModel.state.value.content)
    }

    @Test
    fun aFailedLoadShowsTheError() = runTest {
        val error = WaiveraError.Server(500)
        val s = subject(rootId = UNKNOWN_ROOT) { it.backend.syncStatus.value = SyncStatus.Failed(error) }
        assertEquals(ThreadContent.Error(error.messageRes), s.viewModel.state.value.content)
    }

    @Test
    fun aThreadWithNothingInItIsReadyWithoutARoot() = runTest {
        val state = subject(rootId = UNKNOWN_ROOT).viewModel.state.value
        assertEquals(ThreadContent.Ready, state.content)
        assertNull(state.root)
        assertTrue(state.replies.isEmpty())
    }

    @Test
    fun repliesRenderOldestFirstUnderTheRoot() = runTest {
        val state = subject().viewModel.state.value
        assertEquals(DemoData.THREAD_ROOT, state.root?.message?.id)
        assertEquals(listOf(9331L, 9332L), state.replyRows.map { it.message.id })
        assertEquals(2, state.replyCount)
    }

    @Test
    fun collectingTheThreadJoinsItsConversationOnce() = runTest {
        assertEquals(1, subject().threads.observed)
    }

    @Test
    fun reconnectingShowsTheBanner() = runTest {
        val s = subject()
        s.graph.realtime.drop()
        runCurrent()
        assertEquals(ConnectionBannerState.Reconnecting, s.viewModel.state.value.connection.banner)
        s.graph.realtime.reconnect()
        runCurrent()
        assertEquals(ConnectionBannerState.Connected, s.viewModel.state.value.connection.banner)
    }

    @Test
    fun aNonPositiveRootIsRejectedLocally() = runTest {
        val s = subject(rootId = 0)
        assertEquals(ThreadContent.Invalid, s.viewModel.state.value.content)
        val before = s.graph.backend.messages.value.getValue(DemoData.DM_PRIYA).size
        s.viewModel.send("hello")
        runCurrent()
        assertEquals(before, s.graph.backend.messages.value.getValue(DemoData.DM_PRIYA).size)
        assertEquals(0, s.threads.observed)
    }

    // endregion

    // region Replies

    @Test
    fun aReplyIsSentUnderTheRootAndAppearsAtTheBottom() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.send("Count me in")
            runCurrent()
            assertEquals(ChatEffect.SetDraft(""), awaitItem())
            // HAPTIC-SENT: a sent reply has no vibration.
            assertEquals(ChatEffect.ScrollToLatest, awaitItem())
            expectNoEvents()
        }
        val newest = s.viewModel.state.value.replyRows.last().message
        assertEquals("Count me in", newest.text)
        assertEquals(DemoData.THREAD_ROOT, newest.parentMessageId)
        assertEquals(3, s.graph.backend.message(DemoData.THREAD_ROOT)?.threadReplyCount)
    }

    @Test
    fun editingAnOwnReplySavesTheNewTextWithoutAVibration() = runTest {
        val s = subject()
        s.viewModel.send("Count me in")
        runCurrent()
        val mine = s.viewModel.state.value.replyRows.last().message
        s.viewModel.startEdit(mine)
        assertEquals(mine.id, s.viewModel.state.value.editingMessageId)
        s.viewModel.effects.test {
            s.viewModel.send("Count me in for Saturday")
            runCurrent()
            // UI4-09: saving an edit is a send, and a successful send has no vibration.
            val events = cancelAndConsumeRemainingEvents()
            assertTrue(events.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no saved cue")
        }
        val edited = s.viewModel.state.value.replyRows.single { it.message.id == mine.id }.message
        assertEquals("Count me in for Saturday", edited.text)
        assertTrue(edited.isEdited)
        assertNull(s.viewModel.state.value.editingMessageId)
    }

    @Test
    fun aRefusedReplyRestoresTheDraftVerbatimWithReplyCopy() = runTest {
        val s = subject(messages = { ThrowingSends(it, WaiveraError.RealtimeUnavailable()) })
        s.viewModel.effects.test {
            s.viewModel.send("  Saturday works  ")
            runCurrent()
            assertEquals(ChatEffect.SetDraft(""), awaitItem())
            assertEquals(ChatEffect.SetDraft("  Saturday works  "), awaitItem())
            assertEquals(ChatEffect.Haptic(HapticMoment.SendFailed), awaitItem())
            expectNoEvents()
        }
        assertEquals(SendErrorKind.Offline, s.viewModel.state.value.sendError)
    }

    @Test
    fun aFailedOutboxReplyOffersRetry() = runTest {
        val s = subject()
        s.viewModel.send("this will fail")
        runCurrent()
        assertEquals(SendErrorKind.Server, s.viewModel.state.value.sendError)
        val failed = s.viewModel.state.value.replyRows.last().message
        assertTrue(failed.isPending)
        s.viewModel.retry(requireNotNull(failed.clientMessageId))
        runCurrent()
        assertNull(s.viewModel.state.value.sendError)
        assertFalse(s.viewModel.state.value.replyRows.last().message.isPending)
    }

    @Test
    fun aReadOnlyConversationCannotReplyByAnyPath() = runTest {
        val s = subject(conversationId = DemoData.BOT, rootId = 9510)
        val state = s.viewModel.state.value
        assertEquals(false, state.canWrite)
        assertEquals(ReadOnlyReason.Automated, state.readOnlyReason)
        val before = s.graph.backend.messages.value.getValue(DemoData.BOT).size
        s.viewModel.send("reply")
        runCurrent()
        assertEquals(before, s.graph.backend.messages.value.getValue(DemoData.BOT).size)
    }

    // endregion

    // region Actions (the reference app has none here)

    @Test
    fun longPressOpensTheFullActionSheetAndReplyFocusesTheComposer() = runTest {
        val s = subject()
        val mine = s.viewModel.state.value.replyRows.single { it.message.id == 9331L }.message
        s.viewModel.effects.test {
            s.viewModel.openActions(mine)
            // Haptics policy: a long press opens the sheet without a vibration.
            expectNoEvents()
            val model = assertIs<ChatSheet.Actions>(s.viewModel.state.value.sheet).model
            assertTrue(model.canCopy && model.canReply && model.canDelete && model.canReact && model.canEdit)
            s.viewModel.replyFromSheet()
            assertEquals(ChatEffect.FocusComposer, awaitItem())
        }
        assertNull(s.viewModel.state.value.sheet)
    }

    @Test
    fun reactionsWorkInsideTheThread() = runTest {
        val s = subject()
        s.viewModel.react(9332, "😂")
        runCurrent()
        assertTrue(
            s.viewModel.state.value.replyRows.single { it.message.id == 9332L }.reactions.any {
                it.emoji == "😂" &&
                    it.mine
            },
        )
    }

    @Test
    fun bookmarkingFromTheThreadUpdatesTheRow() = runTest {
        val s = subject()
        s.viewModel.toggleBookmark(9332)
        runCurrent()
        assertTrue(s.viewModel.state.value.replyRows.single { it.message.id == 9332L }.message.isBookmarked)
    }

    // endregion

    // region Read-through and highlight

    @Test
    fun aReplyFromSomeoneElseArrivingOnScreenNeverVibrates() = runTest {
        val s = subject()
        fun reply(text: String) = Message(
            id = s.graph.backend.nextMessageId(),
            conversationId = DemoData.DM_PRIYA,
            authorId = DemoData.PRIYA,
            text = text,
            createdAt = s.graph.backend.now(),
            parentMessageId = DemoData.THREAD_ROOT,
            threadRootMessageId = DemoData.THREAD_ROOT,
        )
        s.viewModel.effects.test {
            s.graph.backend.append(reply("Before you looked"))
            runCurrent()
            expectNoEvents()
            s.viewModel.onResumed()
            runCurrent()
            s.graph.backend.append(reply("Got it, thanks"))
            runCurrent()
            // Haptics policy: not even while the thread is on screen.
            val events = cancelAndConsumeRemainingEvents()
            assertTrue(events.none { it is Event.Item && it.value is ChatEffect.Haptic }, "no receive cue")
        }
        assertTrue(s.viewModel.state.value.replyRows.any { it.message.text == "Got it, thanks" })
    }

    @Test
    fun readThroughCarriesTheRootAndRetriesAfterFailure() = runTest {
        val s = subject()
        val read = DemoData.THREAD_ROOT to 9332L
        s.threads.failing = true
        s.viewModel.onResumed()
        runCurrent()
        assertEquals(listOf(read), s.threads.markReadCalls)

        // The next render pass (here a bookmark change) retries the same read.
        s.threads.failing = false
        s.viewModel.toggleBookmark(9331)
        runCurrent()
        assertEquals(listOf(read, read), s.threads.markReadCalls)

        s.viewModel.onResumed()
        runCurrent()
        assertEquals(2, s.threads.markReadCalls.size, "an unchanged newest reply is not sent again")

        s.viewModel.send("new reply")
        runCurrent()
        val newestId = s.viewModel.state.value.replyRows.last().message.id
        assertEquals(3, s.threads.markReadCalls.size)
        assertEquals(DemoData.THREAD_ROOT to newestId, s.threads.markReadCalls.last())
    }

    @Test
    fun aTargetReplyIsHighlightedForThreeSeconds() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.highlight(9332)
            runCurrent()
            advanceTimeBy(ThreadViewModel.HIGHLIGHT_SCROLL_DELAY_MILLIS + 1)
            val scroll = assertIs<ChatEffect.ScrollTo>(awaitItem())
            assertEquals(scroll.key, s.viewModel.state.value.highlightKey)
            advanceTimeBy(HIGHLIGHT_MILLIS + 1)
            assertNull(s.viewModel.state.value.highlightKey)
        }
    }

    @Test
    fun aTargetOutsideTheThreadKeepsTheThreadAndSaysSo() = runTest {
        val s = subject()
        s.viewModel.effects.test {
            s.viewModel.highlight(9310)
            runCurrent()
            advanceTimeBy(HIGHLIGHT_MILLIS * 2)
            val note = assertIs<ChatEffect.Snackbar>(expectMostRecentItem())
            assertEquals(R.string.feature_chat_reply_missing, note.message)
        }
        assertNull(s.viewModel.state.value.highlightKey)
        assertEquals(ThreadContent.Ready, s.viewModel.state.value.content)
    }

    // endregion

    private companion object {
        const val UNKNOWN_ROOT = 99_999L
        const val HIGHLIGHT_MILLIS = 3_200L
    }
}
