package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.HandledTally
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.TicketUpdate
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.feature.today.ME
import com.waivera.waivera.feature.today.TEST_NOW
import com.waivera.waivera.feature.today.TEST_ZONE
import com.waivera.waivera.feature.today.activity
import com.waivera.waivera.feature.today.allDayEvent
import com.waivera.waivera.feature.today.conversation
import com.waivera.waivera.feature.today.timedEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TodayAssemblerTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val range = DateRange(today, today.plusDays(14))

    private fun sources(
        conversations: List<Conversation> = emptyList(),
        activity: List<ActivityItem> = emptyList(),
        hasMore: Boolean = false,
        events: List<ScheduleEvent> = emptyList(),
        milestones: List<Milestone> = emptyList(),
        synced: Instant? = TEST_NOW,
        failedSends: Set<Long> = emptySet(),
    ) = TodaySources(
        conversations = Synced(conversations, lastSyncedAt = synced),
        activity = Synced(ActivityFeed(activity, hasMore), lastSyncedAt = synced),
        schedule = Synced(ScheduleFeed(range, TEST_ZONE.id, events), lastSyncedAt = synced),
        milestones = Synced(milestones, lastSyncedAt = synced),
        failedSends = failedSends,
    )

    private fun context(
        local: TodayLocalState = TodayLocalState(),
        now: Instant = TEST_NOW,
        realtime: RealtimeStatus = RealtimeStatus(ConnectionState.Connected, isOnline = true),
        firstName: String? = null,
        handled: HandledTally? = null,
    ) = TodayContext(
        accountName = "Alex Rivera",
        myUserId = ME,
        zone = TEST_ZONE,
        now = now,
        realtime = realtime,
        local = local,
        titles = EnglishConversationTitles,
        firstName = firstName,
        handled = handled,
    )

    private fun assemble(sources: TodaySources = sources(), context: TodayContext = context()) =
        TodayAssembler.assemble(sources, context)

    // Needs you

    @Test
    fun `unread DMs, unread mentions, pending invitations and ticket updates need you`() {
        val state = assemble(
            sources(
                conversations = listOf(
                    conversation(7, unread = 2),
                    conversation(11, kind = ConversationKind.GROUP, unread = 4),
                    conversation(14, kind = ConversationKind.BOT, unread = 1, text = "Ticket updated"),
                    conversation(8, unread = 0),
                ),
                activity = listOf(
                    activity(1, type = ChatActivityType.MENTION, conversationId = 11),
                    activity(2, type = ChatActivityType.MENTION, conversationId = 11, read = true),
                    activity(3, type = ChatActivityType.THREAD_REPLY, conversationId = 11),
                ),
                events = listOf(
                    timedEvent("invite", "2026-11-02T15:00:00Z", "2026-11-02T16:00:00Z", MyInvitation.PENDING),
                    timedEvent("started", "2026-11-01T16:00:00Z", "2026-11-01T17:00:00Z", MyInvitation.PENDING),
                ),
            ),
        )
        val keys = state.needsYou.items.map { it.key }
        assertEquals(listOf("mention-1", "dm-7", "invite-invite", "ticket-14"), keys)
        val ticket = assertIs<NeedsYouItem.Ticket>(state.needsYou.items.last())
        assertEquals(MessageSnippet.Text("Ticket updated"), ticket.snippet)
        val direct = assertIs<NeedsYouItem.DirectMessage>(state.needsYou.items[1])
        assertEquals("usr_other_7", direct.otherUserId, "the avatar loads the other person's image")
    }

    @Test
    fun `a ticket card shows the ticket number and title from the last message's payload`() {
        val bot = conversation(14, kind = ConversationKind.BOT, unread = 1, text = "Ticket updated")
        val ticket = TicketUpdate("4821", title = "Replace the van's back tire", url = "https://example.test/t")
        val withTicket = bot.copy(lastMessage = bot.lastMessage?.copy(ticket = ticket))
        val row = assertIs<NeedsYouItem.Ticket>(
            assemble(sources(conversations = listOf(withTicket))).needsYou.items.single(),
        )
        assertEquals("4821", row.ticketId)
        assertEquals("Replace the van's back tire", row.ticketTitle)

        val plainState = assemble(sources(conversations = listOf(bot)))
        val plain = assertIs<NeedsYouItem.Ticket>(plainState.needsYou.items.single())
        assertNull(plain.ticketId, "without a payload the card keeps the generic eyebrow")
        assertNull(plain.ticketTitle)
    }

    @Test
    fun `a pinned mention is not repeated in the feed, but the feed badge still counts it`() {
        val state = assemble(
            sources(
                activity = listOf(
                    activity(1, type = ChatActivityType.MENTION, conversationId = 11),
                    activity(2, type = ChatActivityType.REACTION),
                ),
            ),
        )
        assertEquals(listOf(2L), state.activity.rows.map { it.id })
        assertEquals(2, state.activity.unreadCount)
    }

    @Test
    fun `a DM whose last message is my own does not need me`() {
        val mine = conversation(7, unread = 1, text = "@Priya Raman can you confirm?")
        val sentByMe = mine.copy(lastMessage = mine.lastMessage?.copy(authorId = ME))
        val state = assemble(
            sources(
                conversations = listOf(sentByMe, conversation(9, unread = 2)),
                activity = listOf(activity(1, type = ChatActivityType.MENTION, conversationId = 7)),
            ),
        )
        assertEquals(listOf("dm-9"), state.needsYou.items.map { it.key })
        assertEquals(listOf(1L), state.activity.rows.map { it.id }, "the older mention stays in the feed")
    }

    @Test
    fun `a message I couldn't send comes first and names the chat like the inbox`() {
        val group = conversation(11, kind = ConversationKind.GROUP, unread = 0)
        val state = assemble(
            sources(conversations = listOf(conversation(9, unread = 2), group), failedSends = setOf(11L, 99L)),
        )
        assertEquals(listOf("not-sent-11", "dm-9"), state.needsYou.items.map { it.key }, "unknown chats are skipped")
        val row = assertIs<NeedsYouItem.NotSent>(state.needsYou.items.first())
        assertEquals(ConversationTitles.format(group, ME, EnglishConversationTitles), row.name)
        assertFalse(state.isAllCaughtUp, "a failed send is never 'all caught up'")
    }

    @Test
    fun `Today and the inbox share one unread rule`() {
        val mine = conversation(7, unread = 1).let { it.copy(lastMessage = it.lastMessage?.copy(authorId = ME)) }
        val theirs = conversation(9, unread = 1)
        assertFalse(mine.isUnreadFor(ME))
        assertTrue(theirs.isUnreadFor(ME))
        val keys = assemble(sources(conversations = listOf(mine, theirs))).needsYou.items.map { it.key }
        assertEquals(listOf("dm-9"), keys)
    }

    @Test
    fun `the offline banner can name when the oldest data was saved, in the employee's zone`() {
        // 2026-11-01T06:30Z is 1:30 AM CDT, before the fall-back hour.
        val savedAt = Instant.parse("2026-11-01T06:30:00Z")
        val state = assemble(sources(synced = savedAt))
        assertEquals(LocalTime.of(1, 30), state.savedAt)
        assertNull(assemble(sources(synced = null)).savedAt)
    }

    @Test
    fun `a DM with an unknown last author still needs me`() {
        val unknown = conversation(7, unread = 1).let { it.copy(lastMessage = it.lastMessage?.copy(authorId = null)) }
        assertEquals(listOf("dm-7"), assemble(sources(conversations = listOf(unknown))).needsYou.items.map { it.key })
    }

    @Test
    fun `a mention inside an unread DM stays in the feed instead of being listed twice`() {
        val state = assemble(
            sources(
                conversations = listOf(conversation(7, unread = 1)),
                activity = listOf(activity(1, type = ChatActivityType.MENTION, conversationId = 7)),
            ),
        )
        assertEquals(listOf("dm-7"), state.needsYou.items.map { it.key })
        assertEquals(listOf(1L), state.activity.rows.map { it.id })
    }

    @Test
    fun `an answered invitation leaves Needs you at once`() {
        val events = listOf(timedEvent("invite", "2026-11-02T15:00:00Z", "2026-11-02T16:00:00Z", MyInvitation.PENDING))
        val state = assemble(sources(events = events), context(TodayLocalState(answeredInvitations = setOf("invite"))))
        assertTrue(state.needsYou.items.isEmpty())
    }

    @Test
    fun `Needs you collapses to five rows with a count of the rest`() {
        val conversations = (1L..7L).map { conversation(it, unread = 1, at = TEST_NOW.minusSeconds(it * 60)) }
        val collapsed = assemble(sources(conversations = conversations))
        assertEquals(5, collapsed.needsYou.items.size)
        assertEquals(2, collapsed.needsYou.hiddenCount)
        assertFalse(collapsed.needsYou.isExpanded)
        val expandedContext = context(TodayLocalState(needsYouExpanded = true))
        val expanded = assemble(sources(conversations = conversations), expandedContext)
        assertEquals(7, expanded.needsYou.items.size)
        assertTrue(expanded.needsYou.isExpanded)
    }

    @Test
    fun `DM snippets fall back to the attachment when there is no text`() {
        val photo = conversation(7, unread = 1, text = null).let {
            it.copy(lastMessage = it.lastMessage?.copy(hasAttachment = true, attachmentContentType = "image/jpeg"))
        }
        val file = conversation(8, unread = 1, text = "  \n").let {
            it.copy(lastMessage = it.lastMessage?.copy(hasAttachment = true, attachmentName = "rota.pdf"))
        }
        val snippets = assemble(sources(conversations = listOf(photo, file))).needsYou.items
            .map { (it as NeedsYouItem.DirectMessage).snippet }
        assertEquals(listOf(MessageSnippet.Photo, MessageSnippet.File("rota.pdf")), snippets)
    }

    // All caught up

    @Test
    fun `all caught up when nothing needs you and nothing is unread`() {
        val state = assemble(sources(activity = listOf(activity(1, read = true))))
        assertTrue(state.isAllCaughtUp)
    }

    @Test
    fun `not caught up while activity is unread or before the first sync`() {
        assertFalse(assemble(sources(activity = listOf(activity(1)))).isAllCaughtUp)
        assertFalse(assemble(sources(synced = null)).isAllCaughtUp)
    }

    // Next up

    @Test
    fun `next up shows the next three items, all-day first, skipping ended, declined and pending ones`() {
        val events = listOf(
            timedEvent("ended", "2026-11-01T14:00:00Z", "2026-11-01T15:00:00Z"),
            timedEvent("declined", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z", MyInvitation.DECLINED),
            timedEvent("pending", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z", MyInvitation.PENDING),
            timedEvent("visit", "2026-11-01T16:55:00Z", "2026-11-01T18:00:00Z"),
            timedEvent("now", "2026-11-01T16:00:00Z", "2026-11-01T17:00:00Z"),
            timedEvent("tomorrow", "2026-11-02T15:00:00Z", "2026-11-02T16:00:00Z"),
            allDayEvent("holiday", today),
        )
        val items = assemble(sources(events = events)).nextUp.items
        assertEquals(listOf("holiday", "now", "visit"), items.map { it.eventId })
        assertEquals(UpcomingLabel.AllDayToday, items[0].time)
        assertNull(items[0].start)
        assertEquals(UpcomingLabel.Now(LocalTime.of(11, 0)), items[1].time)
        assertEquals(UpcomingLabel.InMinutes(25), items[2].time)
        assertEquals(LocalTime.of(10, 55), items[2].start)
    }

    @Test
    fun `a multi-day all-day item that started earlier counts as today`() {
        val items = assemble(sources(events = listOf(allDayEvent("trip", today.minusDays(2), today.plusDays(2)))))
            .nextUp.items
        assertEquals(UpcomingLabel.AllDayToday, items.single().time)
        val ended = assemble(sources(events = listOf(allDayEvent("past", today.minusDays(2), today)))).nextUp.items
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `an overnight shift across the fall-back hour shows its local times and ends next day`() {
        // 22:00 CDT on 31 Oct to 06:00 CST on 1 Nov, seen at 21:00 CDT on 31 Oct.
        val shift = timedEvent("overnight", "2026-11-01T03:00:00Z", "2026-11-01T12:00:00Z")
        val evening = Instant.parse("2026-11-01T02:00:00Z")
        val item = assemble(sources(events = listOf(shift)), context(now = evening)).nextUp.items.single()
        assertEquals(LocalTime.of(22, 0), item.start)
        assertEquals(LocalTime.of(6, 0), item.end)
        assertTrue(item.endsOnLaterDay)
        assertEquals(UpcomingLabel.InHours(1, 0), item.time)
    }

    @Test
    fun `an item ending exactly at midnight does not end on a later day`() {
        val event = timedEvent("late", "2026-11-02T04:00:00Z", "2026-11-02T06:00:00Z") // 22:00 to 00:00 CST
        val item = assemble(sources(events = listOf(event))).nextUp.items.single()
        assertFalse(item.endsOnLaterDay)
    }

    @Test
    fun `next up follows the employee zone, not UTC, for the day boundary`() {
        // 05:30 UTC on 2 Nov is still 23:30 on 1 Nov in Chicago: "today", not "tomorrow".
        val late = timedEvent("late", "2026-11-02T05:30:00Z", "2026-11-02T06:00:00Z")
        val item = assemble(sources(events = listOf(late))).nextUp.items.single()
        assertEquals(UpcomingLabel.TodayAt(LocalTime.of(23, 30)), item.time)
    }

    // Celebrations

    @Test
    fun `a birthday and an anniversary on the same day become one celebration`() {
        val milestones = listOf(
            Milestone(MilestoneType.BIRTHDAY, "u1", "Jordan Lee", month = 11, day = 1),
            Milestone(MilestoneType.WORK_ANNIVERSARY, "u1", "Jordan Lee", month = 11, day = 1, startYear = 2021),
            Milestone(MilestoneType.WORK_ANNIVERSARY, "u2", "New Hire", month = 11, day = 1, startYear = 2026),
            Milestone(MilestoneType.BIRTHDAY, "u3", "Tomorrow", month = 11, day = 2),
        )
        val celebrations = assemble(sources(milestones = milestones)).milestones
        assertEquals(listOf(CelebrationItem("u1", "Jordan Lee", isBirthday = true, anniversaryYears = 5)), celebrations)
    }

    @Test
    fun `milestones resolve on the employee's date across the fall-back change`() {
        // 04:30 UTC on 1 Nov is 23:30 CDT on 31 Oct: a 31 October birthday is still today.
        val milestones = listOf(Milestone(MilestoneType.BIRTHDAY, "u1", "Sam", month = 10, day = 31))
        val state = assemble(sources(milestones = milestones), context(now = Instant.parse("2026-11-01T04:30:00Z")))
        assertEquals(listOf("u1"), state.milestones.map { it.userId })
    }

    @Test
    fun `my own celebration is marked so it offers no wishes to myself`() {
        val milestones = listOf(
            Milestone(MilestoneType.BIRTHDAY, ME, "Alex Rivera", month = 11, day = 1),
            Milestone(MilestoneType.BIRTHDAY, "u1", "Jordan Lee", month = 11, day = 1),
        )
        val celebrations = assemble(sources(milestones = milestones)).milestones.associateBy { it.userId }
        assertTrue(celebrations.getValue(ME).isMe)
        assertFalse(celebrations.getValue("u1").isMe)
    }

    @Test
    fun `the greeting names the part of the day in the employee's zone`() {
        // TEST_NOW is 10:30 in Chicago on the day DST ends.
        assertEquals(Greeting(DayPart.MORNING, "Alex"), assemble(context = context(firstName = "Alex")).greeting)
        // 22:30 UTC is 16:30 CST (afternoon); the summer offset, -05:00, would make it 17:30 (evening).
        val boundary = context(now = Instant.parse("2026-11-01T22:30:00Z"), firstName = null)
        assertEquals(Greeting(DayPart.AFTERNOON, null), assemble(context = boundary).greeting)
    }

    @Test
    fun `a failed milestone load is surfaced instead of staying silent`() {
        val failing = sources().copy(milestones = Synced(emptyList(), SyncStatus.Failed(WaiveraError.Network()), null))
        assertTrue(assemble(failing).milestonesFailed)
    }

    // Activity

    @Test
    fun `the unread count covers every loaded page and is a lower bound only past the auto-load cap`() {
        val items = (1L..60L).map { activity(it, read = it % 2L == 0L) }
        val section = assemble(sources(activity = items, hasMore = true)).activity
        assertEquals(30, section.unreadCount)
        assertFalse(section.unreadIsLowerBound)
        val cappedContext = context(TodayLocalState(autoPagingCapped = true))
        val capped = assemble(sources(activity = items, hasMore = true), cappedContext)
        assertTrue(capped.activity.unreadIsLowerBound)
    }

    @Test
    fun `with Unread on, reading the last unread item shows the filtered empty state`() {
        val local = TodayLocalState(unreadOnly = true)
        val before = assemble(sources(activity = listOf(activity(1), activity(2, read = true))), context(local))
        assertEquals(listOf(1L), before.activity.rows.map { it.id })
        val allRead = listOf(activity(1, read = true), activity(2, read = true))
        val after = assemble(sources(activity = allRead), context(local))
        assertTrue(after.activity.isFilteredEmpty)
        assertFalse(after.activity.isEmpty)
    }

    @Test
    fun `has-more is only trusted after a real sync`() {
        assertFalse(assemble(sources(activity = listOf(activity(1)), hasMore = true, synced = null)).activity.hasMore)
        assertTrue(assemble(sources(activity = listOf(activity(1)), hasMore = true)).activity.hasMore)
    }

    // Screen states

    @Test
    fun `nothing cached and still loading is the initial load`() {
        val state = assemble(sources(synced = null))
        assertTrue(state.isInitialLoad)
        assertNull(state.loadError)
        val noRange = assemble(sources(conversations = listOf(conversation(7, unread = 1))).copy(schedule = null))
        assertFalse(noRange.isInitialLoad, "cached chats render while the schedule range resolves")
    }

    @Test
    fun `nothing cached and every source failed is the full error state`() {
        val error = WaiveraError.Network()
        val failed = SyncStatus.Failed(error)
        val state = assemble(
            TodaySources(
                conversations = Synced(emptyList(), failed),
                activity = Synced(ActivityFeed(), failed),
                schedule = Synced(ScheduleFeed(range, TEST_ZONE.id), failed),
                milestones = Synced(emptyList(), failed),
            ),
        )
        assertEquals(error, state.loadError)
        assertFalse(state.isInitialLoad)
    }

    @Test
    fun `cached data with a failed refresh keeps the content and no full error`() {
        val cached = sources(conversations = listOf(conversation(7, unread = 1)))
        val failed = SyncStatus.Failed(WaiveraError.Network())
        val failing = cached.copy(conversations = cached.conversations.copy(status = failed))
        val state = assemble(failing)
        assertNull(state.loadError)
        assertEquals(1, state.needsYou.items.size)
    }

    @Test
    fun `the stale line appears only when data is older than five minutes and no refresh is running`() {
        assertNull(assemble(sources(synced = TEST_NOW.minusSeconds(240))).staleSince)
        assertEquals(PastLabel.MinutesAgo(12), assemble(sources(synced = TEST_NOW.minusSeconds(720))).staleSince)
        val refreshing = context(TodayLocalState(isRefreshing = true))
        assertNull(assemble(sources(synced = TEST_NOW.minusSeconds(720)), refreshing).staleSince)
    }

    @Test
    fun `offline and reconnecting show the banner, connecting does not`() {
        val offline = RealtimeStatus(ConnectionState.Reconnecting(3, 8_000), isOnline = false)
        assertEquals(ConnectionBannerState.Offline, TodayAssembler.connectionBanner(offline))
        val reconnecting = RealtimeStatus(ConnectionState.Reconnecting(1, 2_000), isOnline = true)
        assertEquals(ConnectionBannerState.Reconnecting, TodayAssembler.connectionBanner(reconnecting))
        val connecting = RealtimeStatus(ConnectionState.Connecting, isOnline = true)
        assertEquals(ConnectionBannerState.Connected, TodayAssembler.connectionBanner(connecting))
        assertNotNull(assemble().date)
    }

    @Test
    fun `handled - the observation lists every open item, collapsed ones too, and marks invitations undoable`() {
        val conversations = (1L..6L).map { conversation(it, unread = 1, at = TEST_NOW.minusSeconds(it * 60)) }
        val invite = timedEvent("review", "2026-11-03T15:00:00Z", "2026-11-03T16:00:00Z", MyInvitation.PENDING)
        val state = assemble(sources(conversations = conversations, events = listOf(invite)))
        val observation = assertNotNull(state.handledObservation)
        assertTrue(state.needsYou.items.size < observation.open.size, "some items are collapsed")
        assertEquals(7, observation.open.size)
        assertEquals(7, state.needsYou.totalCount)
        assertEquals(setOf(NeedsYouItem.Invitation.KEY_PREFIX + "review"), observation.undoable)
        assertEquals(ME, observation.userId)
        assertEquals(today, observation.day)
    }

    @Test
    fun `handled - nothing is observed while activity is still loading or has failed`() {
        val loading = sources(conversations = listOf(conversation(1, unread = 1))).let {
            it.copy(activity = Synced(ActivityFeed(emptyList(), false), lastSyncedAt = null))
        }
        assertNull(assemble(loading).handledObservation)
        val failed = loading.copy(
            activity = Synced(
                ActivityFeed(emptyList(), false),
                status = SyncStatus.Failed(WaiveraError.Network()),
                lastSyncedAt = null,
            ),
        )
        assertNull(assemble(failed).handledObservation)
    }

    @Test
    fun `handled - the ring counts what left the list since the saved tally`() {
        val saved = HandledTally(userId = ME, day = today, handled = 2, open = setOf("gone-1", "gone-2"))
        val state = assemble(sources(conversations = listOf(conversation(1, unread = 1))), context(handled = saved))
        val progress = assertNotNull(state.progress)
        assertEquals(4, progress.handled, "both saved items left the list")
        assertEquals(1, progress.open)
    }

    @Test
    fun `handled - yesterday's tally starts the ring again at zero`() {
        val yesterday = HandledTally(userId = ME, day = today.minusDays(1), handled = 6, open = setOf("old"))
        val state = assemble(sources(conversations = listOf(conversation(1, unread = 1))), context(handled = yesterday))
        assertEquals(TodayProgress(handled = 0, open = 1), state.progress)
    }

    @Test
    fun `stats and the day track come from the same schedule`() {
        val visit = timedEvent("visit", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z")
        val state = assemble(sources(conversations = listOf(conversation(1, unread = 2)), events = listOf(visit)))
        assertEquals(TodayStats(eventsToday = 1, unreadChats = 1, eventsThisWeek = 1), state.stats)
        assertEquals(1, assertNotNull(state.day).eventCount)
    }
}
