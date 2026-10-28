package com.waivera.waivera.feature.chat.screenshot

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.LocalSignedInAccount
import com.waivera.waivera.core.designsystem.component.SignedInAccount
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.feature.chat.ConversationActions
import com.waivera.waivera.feature.chat.ConversationScreenContent
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.ThreadActions
import com.waivera.waivera.feature.chat.ThreadScreenContent
import com.waivera.waivera.feature.chat.conversation.CallUi
import com.waivera.waivera.feature.chat.conversation.ConversationContent
import com.waivera.waivera.feature.chat.conversation.ConversationUiState
import com.waivera.waivera.feature.chat.message.ChatActions
import com.waivera.waivera.feature.chat.message.ConnectionUi
import com.waivera.waivera.feature.chat.message.FileActionsContent
import com.waivera.waivera.feature.chat.message.MessageActionsContent
import com.waivera.waivera.feature.chat.message.ReactionDetailsContent
import com.waivera.waivera.feature.chat.message.Reactions
import com.waivera.waivera.feature.chat.message.ReadOnlyReason
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.SendErrorKind
import com.waivera.waivera.feature.chat.message.SendErrorUi
import com.waivera.waivera.feature.chat.message.StagedImage
import com.waivera.waivera.feature.chat.message.TimelineBuilder
import com.waivera.waivera.feature.chat.message.TimelineItem
import com.waivera.waivera.feature.chat.thread.ThreadContent
import com.waivera.waivera.feature.chat.thread.ThreadUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Conversation and thread screens in every state, from the demo data set, across the shared
 * matrix. Sheets are captured as their content: Robolectric does not draw a modal window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ConversationScreenshotTest {
    private val time = RowTime(DemoData.NOW, DemoData.ZONE)
    private val messages = DemoData.messages()
    private val conversations = DemoData.conversations().associateBy { it.id }
    private val names = DemoData.people().associate { it.id to it.name }

    private fun timeline(conversationId: Long, bot: Boolean = false): List<TimelineItem> = TimelineBuilder.build(
        messages = messages.filter { it.conversationId == conversationId && !it.isThreadReply },
        myUserId = DemoData.ME,
        zone = DemoData.ZONE,
        botConversation = bot,
    )

    private fun titleOf(c: Conversation): String = ConversationTitles.format(c, DemoData.ME, EnglishConversationTitles)

    private fun state(conversationId: Long = DemoData.DM_PRIYA) = ConversationUiState(
        title = conversations[conversationId]?.let(::titleOf),
        memberCount = conversations[conversationId]?.takeIf { ConversationTitles.isGroup(it, DemoData.ME) }
            ?.participants?.size,
        isDirect = conversationId == DemoData.DM_PRIYA,
        content = ConversationContent.Messages,
        items = timeline(conversationId),
        names = names,
        canWrite = true,
        myUserId = DemoData.ME,
        call = CallUi(available = true),
    )

    @Composable
    private fun ConversationView(
        state: ConversationUiState,
        draft: String = "",
        listState: LazyListState = rememberLazyListState(),
    ) {
        WithDemoChatAvatars {
            ConversationScreenContent(
                state = state,
                draft = draft,
                onDraftChange = {},
                time = time,
                actions = ConversationActions.None,
                listState = listState,
                skeletonDelayMillis = 0,
            )
        }
    }

    @Test
    fun populated() = captureScreenshots("conversation_populated") {
        ConversationView(state().copy(typingNames = listOf("Priya Raman")))
    }

    @Test
    fun loading() = captureScreenshots("conversation_loading") {
        ConversationView(state().copy(content = ConversationContent.Loading, items = emptyList()))
    }

    @Test
    fun empty() = captureScreenshots("conversation_empty") {
        ConversationView(state().copy(content = ConversationContent.Empty, items = emptyList()))
    }

    @Test
    fun error() = captureScreenshots("conversation_error") {
        ConversationView(
            state().copy(
                content = ConversationContent.Error(
                    com.waivera.waivera.core.network.R.string.core_network_error_server,
                ),
                items = emptyList(),
            ),
        )
    }

    /** A conversation that was removed: conversation copy, no composer. */
    @Test
    fun notFound() = captureScreenshots("conversation_not_found") {
        ConversationView(
            state().copy(
                content = ConversationContent.Error(
                    com.waivera.waivera.core.network.R.string.core_network_error_not_found,
                ),
                items = emptyList(),
            ),
        )
    }

    /**
     * The same conversation with every message a minute apart, so runs by one author group:
     * name and avatar on the first message of a run only, tight spacing inside it.
     */
    @Test
    fun grouped() = captureScreenshots("conversation_grouped") {
        val dm = messages.filter { it.conversationId == DemoData.DM_PRIYA && !it.isThreadReply }
        val retimed = dm.mapIndexed { index, message ->
            message.copy(createdAt = DemoData.NOW.minusSeconds((dm.size - index) * SECONDS_PER_MINUTE))
        }
        val items = TimelineBuilder.build(
            messages = retimed,
            myUserId = DemoData.ME,
            zone = DemoData.ZONE,
            botConversation = false,
        )
        ConversationView(state().copy(items = items))
    }

    /**
     * Three incoming messages in a row from Priya, then one of mine: in a DM there is no author
     * line, the avatar sits on the last bubble of her run only, and each run ends with its time.
     */
    @Test
    fun groupedIncoming() = captureScreenshots("conversation_grouped_incoming") {
        val priya = messages.first { it.conversationId == DemoData.DM_PRIYA && it.authorId == DemoData.PRIYA }
        val mine = messages.first { it.conversationId == DemoData.DM_PRIYA && it.authorId == DemoData.ME }
        val run = listOf("Are you around Friday?", "The morning shift is short one person.", "No worries if not.")
            .mapIndexed { index, text ->
                priya.copy(
                    id = INCOMING_RUN_FIRST_ID + index,
                    clientMessageId = null,
                    text = text,
                    doc = null,
                    attachment = null,
                    plainText = text,
                    contentFormat = Message.CONTENT_FORMAT_PLAIN,
                    reactions = emptyList(),
                    threadReplyCount = 0,
                    createdAt = DemoData.NOW.minusSeconds((INCOMING_RUN_SIZE - index) * SECONDS_PER_MINUTE),
                )
            }
        val reply = mine.copy(
            id = INCOMING_RUN_FIRST_ID + INCOMING_RUN_SIZE,
            clientMessageId = null,
            text = "I can do the morning.",
            doc = null,
            attachment = null,
            plainText = null,
            contentFormat = Message.CONTENT_FORMAT_PLAIN,
            sendState = SendState.SENT,
            sendFailure = null,
            createdAt = DemoData.NOW,
        )
        val items = TimelineBuilder.build(run + reply, DemoData.ME, DemoData.ZONE, botConversation = false)
        ConversationView(state().copy(items = items))
    }

    @Test
    fun invalid() = captureScreenshots("conversation_invalid") {
        ConversationView(ConversationUiState(content = ConversationContent.Invalid))
    }

    @Test
    fun reconnectingWithStaleRows() = captureScreenshots("conversation_reconnecting") {
        ConversationView(
            state().copy(
                connection = ConnectionUi(ConnectionBannerState.Reconnecting),
                isStale = true,
            ),
        )
    }

    @Test
    fun offline() = captureScreenshots("conversation_offline") {
        ConversationView(
            state().copy(
                connection = ConnectionUi(ConnectionBannerState.Offline),
            ),
        )
    }

    /** The failed row is on screen: its inline "Failed · Retry" is the only signal, the composer is empty. */
    @Test
    fun sendFailed() = captureScreenshots("conversation_send_failed") {
        ConversationView(state().copy(sendError = SendErrorKind.Offline))
    }

    /** Scrolled up past the failed row: one compact "1 message not sent · Show" banner. */
    @Test
    fun sendFailedOffscreen() = captureScreenshots("conversation_send_failed_offscreen") {
        ConversationView(
            state().copy(sendError = SendErrorKind.Offline),
            listState = rememberLazyListState(initialFirstVisibleItemIndex = SCROLLED_UP_INDEX),
        )
    }

    /** Refused before it reached the outbox: no row to point at, so the full banner keeps the draft. */
    @Test
    fun sendRefused() = captureScreenshots("conversation_send_refused") {
        val sent = state()
        ConversationView(
            sent.copy(sendError = SendErrorKind.ReadOnly, items = sent.items.filterNot(SendErrorUi::isFailedRow)),
            draft = "Sending the updated schedule now",
        )
    }

    @Test
    fun readOnlyBotWithTicket() = captureScreenshots("conversation_read_only_bot") {
        ConversationView(
            state(DemoData.BOT).copy(
                items = timeline(DemoData.BOT, bot = true),
                canWrite = false,
                readOnlyReason = ReadOnlyReason.Automated,
                isBot = true,
                call = CallUi(),
            ),
        )
    }

    @Test
    fun archivedChannel() = captureScreenshots("conversation_read_only_archived") {
        ConversationView(
            state(DemoData.CHANNEL).copy(canWrite = false, readOnlyReason = ReadOnlyReason.ArchivedChannel),
        )
    }

    @Test
    fun liveCallWithCallLog() = captureScreenshots("conversation_call") {
        ConversationView(state(DemoData.GROUP).copy(call = CallUi(available = true, isLive = true)))
    }

    @Test
    fun highlightedAndOlderPageLoading() = captureScreenshots("conversation_highlight") {
        val base = state()
        val target = base.items.filterIsInstance<TimelineItem.MessageRow>().first { it.message.attachment != null }
        ConversationView(base.copy(highlightKey = target.key, hasOlder = true, isLoadingOlder = true))
    }

    @Test
    fun editingWithStagedImage() = captureScreenshots("conversation_composer") {
        ConversationView(
            state().copy(stagedImage = StagedImage("content://media/1", "site-photo.jpg", "image/jpeg", 2_400_000)),
            draft = "Photo from the visit",
        )
    }

    @Test
    fun actionSheet() = captureScreenshots("conversation_actions_sheet") {
        MessageActionsContent(
            model = ChatActions.model(richMessage(), DemoData.ME, canWrite = true, allowReply = true),
            onReact = {},
            onCopy = {},
            onReply = {},
            onEdit = {},
            onBookmark = {},
            onDelete = {},
            onClose = {},
            onLink = {},
        )
    }

    @Test
    fun reactionDetailsSheet() = captureScreenshots("conversation_reactions_sheet") {
        val reacted = messages.first { it.reactions.size > 1 }
        CompositionLocalProvider(LocalSignedInAccount provides SignedInAccount("Alex Rivera")) {
            ReactionDetailsContent(
                reactions = Reactions.summarize(reacted.reactions, DemoData.ME),
                initialEmoji = reacted.reactions.first().emoji,
                nameFor = { id -> names[id] ?: "You" },
                onClose = {},
                myUserId = DemoData.ME,
            )
        }
    }

    @Test
    fun fileSheet() = captureScreenshots("conversation_file_sheet") {
        FileActionsContent(fileName = "Hughes care plan.pdf", busy = false, onSave = {}, onShare = {}, onClose = {})
    }

    @Test
    fun threadPopulated() = captureScreenshots("thread_populated") {
        ThreadView(threadState())
    }

    @Test
    fun threadLoading() = captureScreenshots("thread_loading") {
        ThreadView(ThreadUiState(content = ThreadContent.Loading))
    }

    @Test
    fun threadError() = captureScreenshots("thread_error") {
        ThreadView(
            ThreadUiState(
                content = ThreadContent.Error(com.waivera.waivera.core.network.R.string.core_network_error_server),
            ),
        )
    }

    @Test
    fun threadEmptyAndRootMissing() = captureScreenshots("thread_empty") {
        ThreadView(ThreadUiState(content = ThreadContent.Ready, canWrite = true, myUserId = DemoData.ME))
    }

    @Test
    fun threadReadOnly() = captureScreenshots("thread_read_only") {
        ThreadView(threadState().copy(canWrite = false, readOnlyReason = ReadOnlyReason.ReadOnlyChannel))
    }

    /**
     * What the app really produces when a reply's send fails offline (UI3-07): the reply stays in
     * the thread as a failed row with Retry, Edit and Discard, the composer is empty, and the
     * offline banner is the one connection signal, exactly as in the conversation.
     */
    @Test
    fun threadSendFailed() = captureScreenshots("thread_send_failed") {
        ThreadView(
            threadState(extraReplies = listOf(failedReply())).copy(
                sendError = SendErrorKind.Offline,
                connection = ConnectionUi(ConnectionBannerState.Offline),
            ),
        )
    }

    private fun failedReply() = Message(
        id = 0 - DemoData.NOW.toEpochMilli(),
        conversationId = DemoData.DM_PRIYA,
        clientMessageId = "thread-reply-failed",
        authorId = DemoData.ME,
        text = "Saturday works",
        createdAt = DemoData.NOW.minusSeconds(60),
        parentMessageId = DemoData.THREAD_ROOT,
        threadRootMessageId = DemoData.THREAD_ROOT,
        sendState = SendState.FAILED,
    )

    private fun threadState(extraReplies: List<Message> = emptyList()): ThreadUiState {
        val thread = messages.filter { it.conversationId == DemoData.DM_PRIYA }
        val root = thread.single { it.id == DemoData.THREAD_ROOT }
        val replies = thread.filter {
            it.threadRootMessageId == DemoData.THREAD_ROOT && it.id != DemoData.THREAD_ROOT
        } + extraReplies
        return ThreadUiState(
            content = ThreadContent.Ready,
            root = TimelineBuilder.build(listOf(root), DemoData.ME, DemoData.ZONE, false, daySeparators = false)
                .filterIsInstance<TimelineItem.MessageRow>().single(),
            replies = TimelineBuilder.build(replies, DemoData.ME, DemoData.ZONE, false),
            replyCount = replies.count { !it.isPending },
            names = names,
            conversationTitle = conversations[DemoData.DM_PRIYA]?.let(::titleOf),
            canWrite = true,
            myUserId = DemoData.ME,
        )
    }

    @Composable
    private fun ThreadView(state: ThreadUiState, draft: String = "") {
        WithDemoChatAvatars {
            ThreadScreenContent(
                state = state,
                draft = draft,
                onDraftChange = {},
                time = time,
                actions = ThreadActions.None,
                skeletonDelayMillis = 0,
            )
        }
    }

    private fun richMessage(): Message = messages.first { it.id == RICH_MESSAGE }

    private companion object {
        const val RICH_MESSAGE = 9350L

        /** Far enough up the DM that the newest (failed) row is off screen on every phone. */
        const val SCROLLED_UP_INDEX = 6
        const val INCOMING_RUN_FIRST_ID = 99_001L
        const val INCOMING_RUN_SIZE = 3
    }
}

private const val SECONDS_PER_MINUTE = 60L
