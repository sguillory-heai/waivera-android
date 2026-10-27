package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.feature.today.activity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test

class ActivityRulesTest {
    @Test
    fun `the same id on two pages renders once, using the later page`() {
        val first = activity(1, summary = "first copy")
        val later = activity(1, summary = "later copy", read = true)
        val merged = ActivityRules.merge(listOf(first, activity(2), later))
        assertEquals(listOf(1L, 2L), merged.map { it.id })
        assertEquals("later copy", merged.first { it.id == 1L }.summary)
    }

    @Test
    fun `zone-less UTC timestamps order as UTC instants against zoned ones`() {
        // A zone-less 14:40 is UTC, so 09:45-05:00 (14:45 UTC) is newer. Read as device-local it could flip.
        val zoneless = activity(1, createdAt = WaiveraTime.parseInstant("2026-10-20T14:40:02"))
        val zoned = activity(2, createdAt = WaiveraTime.parseInstant("2026-10-20T09:45:00-05:00"))
        assertEquals(listOf(2L, 1L), ActivityRules.merge(listOf(zoneless, zoned)).map { it.id })
    }

    @Test
    fun `the recent-activity fixture orders newest first by parsed instant`() {
        val dtos = WaiveraJson.decodeFromString(
            ListSerializer(ChatActivityResponse.serializer()),
            Fixtures.text("rest-chat-recent-activity.json"),
        )
        val merged = ActivityRules.merge(dtos.map(Mappers::activity))
        assertEquals(listOf(4412L, 4410L, 4411L), merged.map { it.id })
    }

    @Test
    fun `a reaction is rewritten with the actor and emoji`() {
        val item = activity(1, type = ChatActivityType.REACTION, actor = " Dana ", reaction = "🎉", summary = "x")
        assertEquals(ActivitySummary.Reaction("Dana", "🎉"), ActivityRules.summary(item))
    }

    @Test
    fun `a reaction without an emoji or actor still reads naturally`() {
        val item = activity(1, type = ChatActivityType.REACTION, actor = "  ", reaction = null)
        assertEquals(ActivitySummary.Reaction(null, null), ActivityRules.summary(item))
    }

    @Test
    fun `other types use the server summary, or the fallback when it is blank`() {
        assertEquals(
            ActivitySummary.Server("Jordan replied"),
            ActivityRules.summary(activity(1, summary = " Jordan replied ")),
        )
        assertEquals(ActivitySummary.Fallback, ActivityRules.summary(activity(1, summary = " ")))
    }

    @Test
    fun `an unknown type appears only under All`() {
        ActivityFilter.entries.forEach { filter ->
            assertEquals(
                filter == ActivityFilter.ALL,
                ActivityRules.matches(ChatActivityType.UNKNOWN, read = false, filter = filter, unreadOnly = false),
                "filter $filter",
            )
        }
    }

    @Test
    fun `type chips and the unread toggle combine with AND`() {
        assertTrue(ActivityRules.matches(ChatActivityType.MENTION, false, ActivityFilter.MENTIONS, unreadOnly = true))
        assertFalse(ActivityRules.matches(ChatActivityType.MENTION, true, ActivityFilter.MENTIONS, unreadOnly = true))
        assertFalse(ActivityRules.matches(ChatActivityType.REACTION, false, ActivityFilter.REPLIES, unreadOnly = false))
        assertTrue(ActivityRules.matches(ChatActivityType.THREAD_REPLY, false, ActivityFilter.REPLIES, false))
        assertTrue(ActivityRules.matches(ChatActivityType.REACTION, true, ActivityFilter.REACTIONS, unreadOnly = false))
    }

    @Test
    fun `older pages may hold unread only when the oldest loaded page has unread items and more exist`() {
        val page = (1L..30L).map { activity(it, read = it != 30L) }
        assertTrue(ActivityRules.olderPagesMayHoldUnread(page, hasMore = true) { it.read })
        assertFalse(ActivityRules.olderPagesMayHoldUnread(page, hasMore = false) { it.read })
        val allRead = page.map { it.copy(read = true) }
        assertFalse(ActivityRules.olderPagesMayHoldUnread(allRead, hasMore = true) { it.read })
    }
}
