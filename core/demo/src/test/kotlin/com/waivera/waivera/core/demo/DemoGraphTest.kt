package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.HubEvent
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class DemoGraphTest {
    private val today: LocalDate = ZonedDateTime.ofInstant(DemoData.NOW, DemoData.ZONE).toLocalDate()

    @Test
    fun inboxCoversEveryConversationKindWithUnreadAndACall() = runTest {
        val graph = DemoGraph(backgroundScope)

        val list = graph.conversations.observeConversations().first().data

        assertEquals(ConversationKind.entries.toSet(), list.map { it.kind }.toSet())
        assertTrue(list.first { it.id == DemoData.GROUP }.unreadCount > 0)
        assertEquals(DemoData.CALL_RECORD, list.first { it.id == DemoData.GROUP }.ongoingCall?.callRecordId)
        // The tab badge uses the inbox's rule: a chat whose newest message is mine isn't unread for me.
        val unreadForMe = list.filter { it.isUnreadFor(DemoData.ME) }
        assertEquals(unreadForMe.sumOf { it.unreadCount }, graph.conversations.observeUnreadTotal().first())
        assertEquals(setOf(DemoData.DM_PRIYA), graph.conversations.observeFailedSends().first())
    }

    @Test
    fun timelinesIncludeReactionsCallLogsTicketsAndAFailedSend() = runTest {
        val graph = DemoGraph(backgroundScope)

        val group = graph.messages.observeTimeline(DemoData.GROUP).first().data.messages
        val bot = graph.messages.observeTimeline(DemoData.BOT).first().data.messages
        val all = graph.backend.messages.value.values.flatten()

        assertTrue(group.any { it.reactions.isNotEmpty() })
        assertTrue(group.any { it.callRecordId != null })
        assertTrue(bot.any { it.systemEventType == "ticket.update" })
        assertTrue(all.any { it.attachment != null })
        assertEquals(SendState.FAILED, all.single { it.clientMessageId == DemoData.FAILED_CLIENT_ID }.sendState)
        assertTrue(
            graph.threads.observeThread(DemoData.DM_PRIYA, DemoData.THREAD_ROOT).first().data.replies.isNotEmpty(),
        )
    }

    @Test
    fun sendSucceedsUnlessTheTextSaysFailAndRetryRecovers() = runTest {
        val graph = DemoGraph(backgroundScope)

        graph.messages.send(DemoData.DM_PRIYA, "On my way", null, null, null)
        val failedId = graph.messages.send(DemoData.DM_PRIYA, "this will fail", null, null, null)

        val before = graph.messages.observeTimeline(DemoData.DM_PRIYA).first().data.messages
        assertEquals(SendState.SENT, before.single { it.text == "On my way" }.sendState)
        assertEquals(SendState.FAILED, before.single { it.clientMessageId == failedId }.sendState)

        assertTrue(graph.messages.retry(failedId).isSuccess)
        val after = graph.messages.observeTimeline(DemoData.DM_PRIYA).first().data.messages
        assertEquals(SendState.SENT, after.single { it.clientMessageId == failedId }.sendState)
    }

    @Test
    fun failingRefreshesSurfaceAsErrors() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.failRefreshes = WaiveraError.Network()

        assertTrue(graph.conversations.refresh().isFailure)
    }

    @Test
    fun scheduleHasTodayAllDayAndAPendingInvitationInChicago() = runTest {
        val graph = DemoGraph(backgroundScope)

        val feed = graph.calendar.observeRange(CalendarMath.day(today)).first().data
        val events = CalendarMath.eventsOn(feed, today)

        assertEquals("America/Chicago", graph.calendar.zone.first().id)
        assertTrue(events.first().allDayStart != null, "all-day events sort first")
        val invite = graph.backend.events.value.single { it.myInvitation == MyInvitation.PENDING }
        graph.calendar.respondToInvitation(invite.id, accept = true)
        assertEquals(MyInvitation.ACCEPTED, graph.backend.events.value.single { it.id == invite.id }.myInvitation)
    }

    @Test
    fun overnightEventAcrossTheDstChangeTouchesBothDays() = runTest {
        val graph = DemoGraph(backgroundScope)
        // 2026-11-01 is the US fall-back day; the demo has an event from Oct 31 evening into Nov 1.
        val dstNight = graph.backend.events.value.filter { e ->
            CalendarMath.occursOn(e, LocalDate.of(2026, 10, 31), DemoData.ZONE) &&
                CalendarMath.occursOn(e, LocalDate.of(2026, 11, 1), DemoData.ZONE) &&
                e.allDayStart == null
        }

        assertTrue(dstNight.isNotEmpty())
    }

    @Test
    fun milestonesLandTodayAndTomorrow() = runTest {
        val graph = DemoGraph(backgroundScope)
        val milestones = graph.calendar.observeMilestones().first().data

        assertTrue(CalendarMath.milestonesOn(milestones, today).isNotEmpty())
        assertTrue(CalendarMath.milestonesOn(milestones, today.plusDays(1)).isNotEmpty())
    }

    @Test
    fun hubScriptTypesSendsAMentionAndReacts() = runTest {
        val graph = DemoGraph(backgroundScope)
        val events = mutableListOf<HubEvent>()
        backgroundScope.launch { graph.realtime.events.collect { events += it } }
        val before = graph.backend.messages.value[DemoData.DM_PRIYA].orEmpty().size

        graph.hub.start()
        graph.hub.start()
        // The script runs in backgroundScope, which advanceUntilIdle does not wait for.
        advanceTimeBy(SCRIPT_MILLIS)
        runCurrent()

        assertEquals(before + 1, graph.backend.messages.value.getValue(DemoData.DM_PRIYA).size)
        assertTrue(events.any { it is HubEvent.TypingStatus && it.isTyping })
        assertEquals(1, events.count { it is HubEvent.MessageReceived })
        assertTrue(events.any { it is HubEvent.ChatAttention })
        assertTrue(graph.backend.message(9312)?.reactions.orEmpty().any { it.userId == DemoData.PRIYA })
        assertEquals(emptySet(), graph.backend.typing.value[DemoData.DM_PRIYA])
    }

    @Test
    fun signOutThenDemoPasswordSignsBackIn() = runTest {
        val graph = DemoGraph(backgroundScope)

        graph.session.signOut()
        assertTrue(graph.session.signIn("alex.rivera@example.com", "wrong").isFailure)
        assertTrue(graph.session.signIn("alex.rivera@example.com", "demo").isSuccess)
        assertEquals(DemoData.ME, graph.backend.myUserId)
    }

    private companion object {
        const val SCRIPT_MILLIS = 20_000L
    }
}
