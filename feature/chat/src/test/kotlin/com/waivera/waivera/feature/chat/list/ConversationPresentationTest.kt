package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.live.ConversationMerge
import com.waivera.waivera.core.data.model.ChannelInfo
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.MeetingInfo
import com.waivera.waivera.core.data.model.OngoingCall
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.dto.MessageType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Row content rules from spec 02 §4 and its acceptance checks (§9). */
class ConversationPresentationTest {
    private val p = ConversationPresentation
    private val text = EnglishText

    @Test
    fun channelObjectWithoutChannelKindRendersAsChannel() {
        val c = Conversation(
            41,
            ConversationKind.DIRECT,
            listOf(me, priya),
            channel = ChannelInfo(name = " north-team "),
        )
        assertTrue(p.isChannel(c))
        assertEquals(RowLeading.Channel(archived = false), p.leading(c, ME))
        assertEquals("north-team", p.title(c, ME, text))
    }

    @Test
    fun channelWithoutNameFallsBackToChannel() {
        assertEquals("Channel", p.title(channel(41, name = "  "), ME, text))
    }

    @Test
    fun archivedChannelUsesTheArchiveTile() {
        val c = channel(58).copy(channel = ChannelInfo(name = "old", isArchived = true))
        assertEquals(RowLeading.Channel(archived = true), p.leading(c, ME))
    }

    @Test
    fun conversationWhoseOnlyParticipantIsMeIsTheWaiveraBot() {
        val c = Conversation(14, ConversationKind.DIRECT, listOf(me))
        assertTrue(p.isBot(c, ME))
        assertEquals("Waivera", p.title(c, ME, text))
        assertEquals(RowLeading.Bot, p.leading(c, ME))
    }

    @Test
    fun botParticipantOrBotAuthorMarksABotConversation() {
        val withBot = Conversation(14, ConversationKind.DIRECT, listOf(me, Person("bot:3", displayName = "Assistant")))
        assertTrue(p.isBot(withBot, ME))
        val botAuthored = dm(15, last = preview(1, author = "bot:3"))
        assertTrue(p.isBot(botAuthored, ME))
        val flagged = dm(17, last = preview(1, author = "usr_helpdesk").copy(isBot = true))
        assertTrue(p.isBot(flagged, ME), "the preview's bot flag (isBot or botId) counts")
        assertTrue(p.isBot(Conversation(16, ConversationKind.BOT), ME))
        assertFalse(p.isBot(channel(41).copy(kind = ConversationKind.BOT), ME))
    }

    @Test
    fun singleParticipantWithUnknownIdentityIsTreatedAsBot() {
        assertTrue(p.isBot(Conversation(14, ConversationKind.DIRECT, listOf(priya)), myUserId = null))
    }

    @Test
    fun groupOfFourIncludingMeReadsFirstAndTwoOthers() {
        val sam = Person("usr_sam", "Sam", "Okafor")
        val c = Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan, sam))
        assertTrue(p.isGroup(c, ME))
        assertEquals(RowLeading.Group, p.leading(c, ME))
        assertEquals("Priya Raman & 2 others", p.title(c, ME, text))
    }

    @Test
    fun aGroupTitleMarksOnlyItsNameAsShortenable() {
        val sam = Person("usr_sam", "Sam", "Okafor")
        val c = Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan, sam))
        val title = p.title(c, ME, text)
        val range = requireNotNull(ConversationTitles.groupNameRange(c, ME, text))
        assertEquals("Priya Raman", title.substring(range))
        assertEquals(" & 2 others", title.substring(range.last + 1))
        assertNull(
            ConversationTitles.groupNameRange(
                Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan)),
                ME,
                text,
            ),
        )
        assertNull(ConversationTitles.groupNameRange(dm(7), ME, text))
        assertNull(ConversationTitles.groupNameRange(channel(41), ME, text))
    }

    @Test
    fun groupOfThreeNamesBothOthersInFull() {
        val c = Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan))
        assertEquals("Priya Raman, Jordan Lee", p.title(c, ME, text))
    }

    @Test
    fun directTitleIsThePeersName() {
        assertEquals("Priya Raman", p.title(dm(7), ME, text))
        assertEquals(RowLeading.Direct(PRIYA, "Priya Raman"), p.leading(dm(7), ME))
    }

    @Test
    fun conversationWithNoOtherNamedPeopleFallsBack() {
        val c = Conversation(9, ConversationKind.GROUP, emptyList())
        assertEquals("Conversation", p.title(c, ME, text))
    }

    @Test
    fun displayNamePrefersFirstAndLastThenDisplayNameThenEmail() {
        assertEquals("Priya Raman", p.displayName(Person("a", "Priya", "Raman", displayName = "PR")))
        assertEquals("PR", p.displayName(Person("a", " ", null, displayName = "PR")))
        assertEquals("x@y.z", p.displayName(Person("a", email = "x@y.z")))
        assertEquals("", p.displayName(Person("a")))
    }

    /**
     * Spec 02 §9 says this compacts to "operations.tea…", but the Expo source it cites cuts the
     * local part only when it is longer than 16 characters; the 15-character local part stays
     * whole. The code is the behaviour reference (spec 11 §4), so the test follows it.
     */
    @Test
    fun emailParticipantCompactsToItsLocalPart() {
        assertEquals("operations.team", p.compactLabel("operations.team@waivera.example"))
        assertEquals("operations.team…", p.compactLabel("operations.team.north@waivera.example"))
    }

    @Test
    fun longNamesCompactToInitialAndLastNameOrTruncate() {
        assertEquals("Priya Raman", p.compactLabel("Priya Raman"))
        assertEquals("A. Montgomery", p.compactLabel("Alexandria Montgomery"))
        assertEquals("Alexandria Montgo…", p.compactLabel("Alexandria Montgomery-Wells"))
        assertEquals("Bartholomewsworth…", p.compactLabel("Bartholomewsworthington"))
    }

    @Test
    fun nullMessagePreviewsAsNoMessagesYet() {
        assertEquals(Preview("No messages yet", PreviewKind.Placeholder), RowSubtitle.preview(null, text))
    }

    @Test
    fun previewIsTheFirstLineWithWhitespaceCollapsed() {
        val result = RowSubtitle.preview(preview(1, text = "  Shift   swap?\r\nsecond line"), text)
        assertEquals(Preview("Shift swap?", PreviewKind.Text), result)
    }

    @Test
    fun imageOnlyMessagePreviewsAsPhoto() {
        val message = preview(1, text = null, attachmentType = "image/jpeg", hasAttachment = true)
        assertEquals(Preview("Photo", PreviewKind.Attachment), RowSubtitle.preview(message, text))
    }

    @Test
    fun namedPdfWithoutTextPreviewsAsTheFileName() {
        val message = preview(
            1,
            text = "",
            attachmentName = " roster.pdf ",
            attachmentType = "application/pdf",
            hasAttachment = true,
        )
        assertEquals(Preview("roster.pdf", PreviewKind.Attachment), RowSubtitle.preview(message, text))
    }

    @Test
    fun unnamedFilePreviewsAsFileAttachment() {
        val message = preview(1, text = null, hasAttachment = true)
        assertEquals("File attachment", RowSubtitle.preview(message, text).text)
    }

    @Test
    fun systemMessageWithoutBodyPreviewsAsConversationUpdate() {
        val message = preview(1, text = null, type = MessageType.SYSTEM)
        assertEquals(Preview("Conversation update", PreviewKind.System), RowSubtitle.preview(message, text))
    }

    @Test
    fun systemMessageDropsTheEmbeddedTimestampToken() {
        val message = preview(
            1,
            text = "Video call ended - {[TIMESTAMP- 08/10/2026 02:20:12]}",
            type = MessageType.SYSTEM,
        )
        assertEquals("Video call ended", RowSubtitle.preview(message, text).text)
    }

    @Test
    fun unextractableBodyIsBlankSoTheRowHydrates() {
        assertEquals(PreviewKind.Blank, RowSubtitle.preview(preview(1, text = null), text).kind)
        assertEquals(PreviewKind.Blank, RowSubtitle.preview(preview(1, text = "   "), text).kind)
    }

    @Test
    fun deletedMessageHasAPlaceholderInsteadOfABlankRow() {
        assertEquals(
            Preview("Message deleted", PreviewKind.Placeholder),
            RowSubtitle.preview(preview(1, deleted = true), text),
        )
    }

    @Test
    fun serializedTipTapDocumentPreviewsAsPlainText() {
        val doc = """{"editor":{"type":"doc","content":[{"type":"paragraph","content":[""" +
            """{"type":"mention","attrs":{"id":"usr_priya_raman","label":"Priya Raman"}},""" +
            """{"type":"text","text":" can you confirm?"}]},""" +
            """{"type":"paragraph","content":[{"type":"text","text":"Second"}]}]}}"""
        assertEquals("@Priya Raman can you confirm?", RowSubtitle.preview(preview(1, text = doc), text).text)
    }

    @Test
    fun theServerPlainTextWinsOverTheRichBody() {
        val rich = preview(1, text = """{"type":"doc","content":[]}""").copy(plainText = "Shift swap approved")
        assertEquals("Shift swap approved", RowSubtitle.preview(rich, text).text)
        val blankPlain = preview(1).copy(plainText = " ")
        assertEquals("hello", RowSubtitle.preview(blankPlain, text).text, "blank plain text is ignored")
    }

    @Test
    fun simpleHtmlPreviewsAsText() {
        val html = preview(1, text = "<p>Hello &amp; <b>welcome</b></p><p>x</p>")
        assertEquals("Hello & welcome", RowSubtitle.preview(html, text).text)
    }

    @Test
    fun malformedJsonFallsBackToTheRawText() {
        assertEquals("{\"type\": oops", RowSubtitle.preview(preview(1, text = "{\"type\": oops"), text).text)
    }

    @Test
    fun senderPrefixIsYouForMyMessages() {
        val c = dm(7, last = preview(1, author = ME))
        assertEquals("You", RowSubtitle.senderPrefix(c, RowSubtitle.preview(c.lastMessage, text), ME, text))
    }

    @Test
    fun senderPrefixIsTheFirstNameInGroupsAndChannels() {
        val group = Conversation(
            11,
            ConversationKind.GROUP,
            listOf(me, priya, jordan),
            lastMessage = preview(1, author = JORDAN),
        )
        assertEquals("Jordan", RowSubtitle.senderPrefix(group, RowSubtitle.preview(group.lastMessage, text), ME, text))
        val ch = channel(41, last = preview(1, author = "usr_unknown", authorName = "Mei Chen"))
        assertEquals("Mei", RowSubtitle.senderPrefix(ch, RowSubtitle.preview(ch.lastMessage, text), ME, text))
    }

    @Test
    fun noSenderPrefixForTheOtherPersonInADirectChatOrForSystemRows() {
        val c = dm(7)
        assertNull(RowSubtitle.senderPrefix(c, RowSubtitle.preview(c.lastMessage, text), ME, text))
        val sys = channel(41, last = preview(1, text = "Jordan joined", type = MessageType.CHANNEL_EVENT))
        assertNull(RowSubtitle.senderPrefix(sys, RowSubtitle.preview(sys.lastMessage, text), ME, text))
    }

    @Test
    fun typingLabelNamesOneTyperAndSummarisesSeveral() {
        val group = Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan))
        assertNull(RowSubtitle.typingLabel(group, emptySet(), ME, text))
        assertEquals("Priya Raman is typing…", RowSubtitle.typingLabel(group, setOf(PRIYA), ME, text))
        assertEquals("Someone is typing…", RowSubtitle.typingLabel(group, setOf("usr_stranger"), ME, text))
        assertEquals("Several people are typing…", RowSubtitle.typingLabel(group, setOf(PRIYA, JORDAN), ME, text))
        assertNull(RowSubtitle.typingLabel(group, setOf(ME), ME, text))
    }

    @Test
    fun typingNamesEndMarksTheOneTypersName() {
        val group = Conversation(11, ConversationKind.GROUP, listOf(me, priya, jordan))
        val label = requireNotNull(RowSubtitle.typingLabel(group, setOf(PRIYA), ME, text))
        val end = requireNotNull(RowSubtitle.typingNamesEnd(group, setOf(PRIYA), ME, text))
        assertEquals("Priya Raman", label.substring(0, end))
        assertEquals(" is typing…", label.substring(end))
        assertEquals("Someone".length, RowSubtitle.typingNamesEnd(group, setOf("usr_stranger"), ME, text))
        assertNull(RowSubtitle.typingNamesEnd(group, setOf(PRIYA, JORDAN), ME, text))
        assertNull(RowSubtitle.typingNamesEnd(group, emptySet(), ME, text))
    }

    @Test
    fun liveCallOrMeetingRoomShowsCallInProgressButUnknownStatusIsInert() {
        assertTrue(p.isCallLive(dm(7).copy(ongoingCall = OngoingCall("room", status = CallRecordStatus.ACTIVE))))
        assertTrue(p.isCallLive(dm(7).copy(activeMeeting = MeetingInfo(roomId = "room"))))
        assertFalse(p.isCallLive(dm(7).copy(ongoingCall = OngoingCall("room", status = CallRecordStatus.UNKNOWN))))
        assertFalse(p.isCallLive(dm(7).copy(ongoingCall = OngoingCall("room", status = CallRecordStatus.END))))
    }

    @Test
    fun orderIsNewestFirstWithMissingTimesLast() {
        val newer = dm(1, last = preview(10, at = NOW))
        val older = dm(2, last = preview(20, at = NOW.minusSeconds(60)))
        val none = dm(3, last = preview(30, at = null))
        val empty = dm(4, last = null)
        assertEquals(listOf(1L, 2L, 4L, 3L), listOf(none, older, empty, newer).sortedWith(p.Order).map { it.id })
    }

    @Test
    fun filtersAndSearchCompose() {
        val unreadDm = ChatRowUi(
            1,
            "Priya Raman",
            leading = RowLeading.Group,
            preview = "Shift swap",
            previewKind = PreviewKind.Text,
            unreadCount = 2,
        )
        val readChannel = ChatRowUi(
            2,
            "north-team",
            leading = RowLeading.Channel(false),
            preview = "roster",
            previewKind = PreviewKind.Text,
            isChannel = true,
        )
        assertTrue(ChatListFilters.matches(unreadDm, ChatListFilter.Unread, ""))
        assertFalse(ChatListFilters.matches(readChannel, ChatListFilter.Unread, ""))
        assertTrue(ChatListFilters.matches(readChannel, ChatListFilter.Channels, "ROSTER"))
        assertFalse(ChatListFilters.matches(unreadDm, ChatListFilter.Channels, ""))
        assertTrue(ChatListFilters.matches(unreadDm, ChatListFilter.All, " swap "))
        assertFalse(ChatListFilters.matches(unreadDm, ChatListFilter.All, "hughes"))
    }

    @Test
    fun emptyReasonFollowsQueryThenFilter() {
        assertEquals(EmptyReason.NoMatches, ChatListFilters.emptyReason(ChatListFilter.Unread, "x"))
        assertEquals(EmptyReason.AllCaughtUp, ChatListFilters.emptyReason(ChatListFilter.Unread, ""))
        assertEquals(EmptyReason.NoChannels, ChatListFilters.emptyReason(ChatListFilter.Channels, " "))
        assertEquals(EmptyReason.NoConversations, ChatListFilters.emptyReason(ChatListFilter.All, ""))
    }

    /** The list relies on the repository merge for sparse-row hydration (spec 02 §9, G-05, G-14). */
    @Test
    fun hydrationMergeKeepsAttachmentMetadataAndTheListsUnreadCount() {
        val cached = dm(
            7,
            unread = 3,
            last = preview(1, text = null, attachmentName = "roster.pdf", hasAttachment = true),
        )
        val detail = dm(
            7,
            unread = 0,
            last = preview(1, text = "roster attached", attachmentName = null, hasAttachment = false),
        )
        val merged = ConversationMerge.refresh(cached, detail, keepUnread = true)
        assertEquals(3, merged.unreadCount)
        assertEquals("roster.pdf", merged.lastMessage?.attachmentName)
        assertTrue(merged.lastMessage?.hasAttachment == true)
        assertEquals("roster attached", merged.lastMessage?.text)
    }
}

class RelativeTimeTest {
    private val time = RelativeTime(EnglishText, Locale.US)
    private val chicago = ZoneId.of("America/Chicago")

    @Test
    fun recentMessagesAreNowOrMinutes() {
        assertEquals("Now", time.label(NOW.minusSeconds(30), NOW, chicago))
        assertEquals("5m", time.label(NOW.minusSeconds(300), NOW, chicago))
        assertEquals("59m", time.label(NOW.minusSeconds(59 * 60), NOW, chicago))
        // Clock skew: a message stamped slightly in the future reads Now.
        assertEquals("Now", time.label(NOW.plusSeconds(20), NOW, chicago))
        assertEquals("", time.label(null, NOW, chicago))
    }

    /**
     * 1 November 2026 is the fall-back day in Chicago (02:00 CDT becomes 01:00 CST at 07:00Z).
     * 05:30Z is 00:30 CDT on the 1st, so it is "today" at 12-hour time; 04:30Z is 23:30 CDT on
     * 31 October, so it is "Yesterday" even though both are within 13 hours of now.
     */
    @Test
    fun calendarDaysAreTakenInTheEmployeesZoneAcrossTheDstBoundary() {
        assertEquals("12:30 AM", time.label(Instant.parse("2026-11-01T05:30:00Z"), NOW, chicago))
        assertEquals("Yesterday", time.label(Instant.parse("2026-11-01T04:30:00Z"), NOW, chicago))
        assertEquals("1:30 AM", time.label(Instant.parse("2026-11-01T07:30:00Z"), NOW, chicago))
        assertEquals("1:30 AM", time.label(Instant.parse("2026-11-01T06:30:00Z"), NOW, chicago))
    }

    @Test
    fun olderMessagesShowAShortDate() {
        assertEquals("Oct 20", time.label(Instant.parse("2026-10-20T14:32:11Z"), NOW, chicago))
    }

    @Test
    fun theSameInstantCanBeTodayInOneZoneAndYesterdayInAnother() {
        val at = Instant.parse("2026-11-01T03:00:00Z")
        assertEquals("Yesterday", time.label(at, NOW, chicago))
        assertEquals("3:00 AM", time.label(at, NOW, ZoneId.of("Europe/London")))
    }
}
