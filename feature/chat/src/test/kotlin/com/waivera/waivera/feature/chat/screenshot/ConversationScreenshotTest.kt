package com.waivera.waivera.feature.chat.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.TaskState
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
import com.waivera.waivera.feature.chat.message.FakeLinkLookups
import com.waivera.waivera.feature.chat.message.FileActionsContent
import com.waivera.waivera.feature.chat.message.FocusOverlayFixtures
import com.waivera.waivera.feature.chat.message.LinkKind
import com.waivera.waivera.feature.chat.message.LinkPreview
import com.waivera.waivera.feature.chat.message.LinkTarget
import com.waivera.waivera.feature.chat.message.MessageFocusActions
import com.waivera.waivera.feature.chat.message.MessageFocusLayer
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Conversation and thread screens in every state, from the demo data set, across the shared
 * matrix. Sheets and the long-press overlay are captured as their content: Robolectric does not draw a modal window.
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

    /**
     * One of my own bubbles carrying a mention and other people's reactions (v5 decision 3, option
     * B): the bold underlined mention and the reaction pills on the surface, under the lilac tint.
     * The demo's mention is copied into the group, where two other people can react, as its newest
     * message so it is in frame on every phone. Test data only.
     */
    @Test
    fun mineWithMentionAndReactions() = captureScreenshots("conversation_mine_mention_reactions") {
        val mine = richMessage().copy(
            id = MINE_REACTED_ID,
            clientMessageId = null,
            conversationId = DemoData.GROUP,
            createdAt = DemoData.NOW.minusSeconds(SECONDS_PER_MINUTE),
            reactions = listOf(Reaction(DemoData.PRIYA, "👍"), Reaction(DemoData.JORDAN, "🎉")),
        )
        val group = messages.filter { it.conversationId == DemoData.GROUP && !it.isThreadReply } + mine
        val items = TimelineBuilder.build(group, DemoData.ME, DemoData.ZONE, botConversation = false)
        ConversationView(state(DemoData.GROUP).copy(items = items))
    }

    /**
     * Waivera links resolved into previews attached under the text: a task, a resource and an
     * FAQ article, plus a link still being checked and one the reader can't open. The rows go
     * through the same link rules the app uses ([ChatLinkResolver.decorate]).
     */
    @Test
    fun linkPreviews() = captureScreenshots("conversation_link_previews", heightDp = 1600) {
        ConversationView(state(DemoData.GROUP).copy(items = linkPreviewRows()))
    }

    /**
     * A link at the end of a message leaves the text; addresses inside a sentence turn into the
     * short names of what they point at, in the same order as the previews. Long titles and
     * details stop at one line.
     */
    private fun linkPreviewRows(): List<TimelineItem> {
        val group = messages.filter { it.conversationId == DemoData.GROUP && !it.isThreadReply }
        val base = group.first { it.authorId != DemoData.ME }
        val from = { author: String -> group.firstOrNull { it.authorId == author } ?: base.copy(authorId = author) }
        val lines = linkLines()
        val sent = lines.mapIndexed { index, (author, parts) ->
            val text = parts.joinToString("") { it.first }
            from(author).copy(
                id = LINK_RUN_FIRST_ID + index,
                clientMessageId = null,
                text = text,
                doc = linkDoc(parts),
                attachment = null,
                plainText = text,
                reactions = emptyList(),
                threadReplyCount = 0,
                sendState = SendState.SENT,
                sendFailure = null,
                createdAt = DemoData.NOW.minusSeconds((lines.size - index) * SECONDS_PER_MINUTE * 7),
            )
        }
        val items = TimelineBuilder.build(group.takeLast(2) + sent, DemoData.ME, DemoData.ZONE, botConversation = false)
        return FakeLinkLookups.resolver().decorate(items, linkPreviewData()).items
    }

    /** Each message as text parts; a part with an address is a link. */
    private fun linkLines(): List<Pair<String, List<Pair<String, String?>>>> {
        val web = FakeLinkLookups.WEB
        return listOf(
            DemoData.PRIYA to
                listOf("Can you take a look before Friday? " to null, "$web/task/HEA-1042" to "$web/task/HEA-1042"),
            DemoData.ME to listOf(
                "Here's the transport provider we used last time." to null,
                "Accessible Rides Co-op" to "/resources/$RIDES",
            ),
            DemoData.JORDAN to listOf(
                "Start with " to null,
                "$web/task/HEA-1077" to "$web/task/HEA-1077",
                ", then read " to null,
                "$web/faqs/article/$MILEAGE" to "$web/faqs/article/$MILEAGE",
                " before you log the trip, and call " to null,
                "$web/resources/$CO_OP" to "$web/resources/$CO_OP",
                " for the pickup." to null,
            ),
            DemoData.PRIYA to listOf(
                "Also these two: " to null,
                "$web/task/1100" to "$web/task/1100",
                " and " to null,
                "$web/task/HEA-999" to "$web/task/HEA-999",
            ),
        )
    }

    private fun linkPreviewData(): Map<LinkTarget, LinkPreview> {
        val day = java.time.LocalDate.of(2026, 11, 6)
        return mapOf(
            LinkTarget.Task(1042, "HEA") to
                LinkPreview.Task(1042, "HEA-1042", "Annual plan review", TaskState.InProgress, day),
            LinkTarget.Resource(RIDES) to LinkPreview.Resource("Accessible Rides Co-op", "Metro Paratransit", "Austin"),
            LinkTarget.Task(1077, "HEA") to LinkPreview.Task(
                1077,
                "HEA-1077",
                "Quarterly support plan review with guardian and case manager",
                TaskState.NotStarted,
                day.plusDays(3),
            ),
            LinkTarget.Faq(MILEAGE, null) to LinkPreview.Faq(
                "How do I submit mileage across two pay periods?",
                "Log each leg on the day you drove it. Trips over 50 miles need a supervisor's note.",
            ),
            LinkTarget.Resource(CO_OP) to LinkPreview.Resource(
                "Accessible Rides Co-op of Central Texas",
                "Metro Paratransit and Capital Area Rural Transportation",
                "Austin, Round Rock and Georgetown",
            ),
            LinkTarget.Task(1100, null) to LinkPreview.Loading(LinkKind.Task),
            LinkTarget.Task(999, "HEA") to LinkPreview.Unavailable(LinkKind.Task),
        )
    }

    /** A TipTap document of one paragraph; parts with an address carry a link mark. */
    private fun linkDoc(parts: List<Pair<String, String?>>) = buildJsonObject {
        put("type", "doc")
        putJsonArray("content") {
            addJsonObject {
                put("type", "paragraph")
                putJsonArray("content") {
                    parts.forEach { (text, href) ->
                        addJsonObject {
                            put("type", "text")
                            put("text", text)
                            if (href != null) {
                                putJsonArray("marks") {
                                    addJsonObject {
                                        put("type", "link")
                                        putJsonObject("attrs") { put("href", href) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

    /**
     * Scrolled up the DM: "Jump to latest" floats over the timeline as a compact pill, with the
     * messages running on underneath it and no band behind it (owner's device review, 2026-10-30).
     */
    @Test
    fun jumpToLatestFloating() = captureScreenshots("conversation_jump_to_latest") {
        ConversationView(state(), listState = rememberLazyListState(initialFirstVisibleItemIndex = SCROLLED_UP_INDEX))
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

    /**
     * The long-press focus overlay (option B) over the conversation, as its layer: a rich message,
     * then the wave 28 long message and link, each clamped with the menu whole under or beside it.
     */
    @Test
    fun actionSheet() = captureScreenshots("conversation_actions_sheet") { FocusView(richMessage()) }

    @Test
    fun actionSheetLongMessage() = captureScreenshots("conversation_actions_focus_long") {
        FocusView(FocusOverlayFixtures.mine(FocusOverlayFixtures.PARAGRAPHS_3000))
    }

    @Test
    fun actionSheetLongLink() = captureScreenshots("conversation_actions_focus_link") {
        FocusView(FocusOverlayFixtures.mine(FocusOverlayFixtures.URL_400))
    }

    @Composable
    private fun FocusView(message: Message) {
        Box {
            ConversationView(state())
            MessageFocusLayer(
                model = ChatActions.model(message, DemoData.ME, canWrite = true, allowReply = true),
                mine = message.isMine(DemoData.ME),
                actions = MessageFocusActions.None,
            )
        }
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
        const val MINE_REACTED_ID = 99_101L
        const val LINK_RUN_FIRST_ID = 99_201L
        const val RIDES = "6a1d3e52-8f07-4c2b-a9d4-17e0b6c35f81"
        const val CO_OP = "0c94f7a2-3b58-4e1d-8a6f-d2b5e9c71034"
        const val MILEAGE = "e7b21c48-95d3-4a06-b8f1-3c6d0a2e9f57"
    }
}

private const val SECONDS_PER_MINUTE = 60L
