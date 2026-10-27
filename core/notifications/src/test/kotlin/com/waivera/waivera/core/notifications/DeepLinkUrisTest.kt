package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkUrisTest {
    private val everyTarget = listOf(
        DeepLinkTarget.Today,
        DeepLinkTarget.Inbox,
        DeepLinkTarget.Conversation(7),
        DeepLinkTarget.Conversation(7, 9312),
        DeepLinkTarget.Thread(7, 9312),
        DeepLinkTarget.Thread(7, 9312, 9330),
        DeepLinkTarget.Schedule,
        DeepLinkTarget.CalendarEvent("evt_8f2b19c4"),
        DeepLinkTarget.Profile,
        DeepLinkTarget.People,
        DeepLinkTarget.Person("usr_priya_raman"),
        DeepLinkTarget.Person("4471"),
        DeepLinkTarget.Bookmarks,
        DeepLinkTarget.Settings,
        DeepLinkTarget.NotificationSettings,
    )

    @Test
    fun `every target round-trips through its uri`() {
        everyTarget.forEach { target -> assertEquals(
            target,
            DeepLinkUris.parse(DeepLinkUris.toUri(target)),
            "$target",
        ) }
    }

    @Test
    fun `uris have the documented shapes`() {
        assertEquals("waivera://chats/7?messageId=9312", DeepLinkUris.toUri(DeepLinkTarget.Conversation(7, 9312)))
        assertEquals("waivera://chats/7/thread/9312", DeepLinkUris.toUri(DeepLinkTarget.Thread(7, 9312)))
        assertEquals(
            "waivera://calendar/evt_8f2b19c4",
            DeepLinkUris.toUri(DeepLinkTarget.CalendarEvent("evt_8f2b19c4")),
        )
    }

    @Test
    fun `calendar host without an id is the schedule`() {
        assertEquals(DeepLinkTarget.Schedule, DeepLinkUris.parse("waivera://calendar"))
    }

    @Test
    fun `scheme and host are case-insensitive`() {
        assertEquals(DeepLinkTarget.Conversation(7), DeepLinkUris.parse("WAIVERA://Chats/7"))
    }

    @Test
    fun `malformed or foreign uris are rejected`() {
        listOf(
            null,
            "",
            "https://waivera.com/chats/7",
            "waivera://chats/0",
            "waivera://chats/-1",
            "waivera://chats/abc",
            "waivera://chats/7/thread",
            "waivera://chats/7/thread/x",
            "waivera://chats/7/other/9",
            "waivera://people/../../etc",
            "waivera://people/a%2Fb",
            "waivera://calendar/evt 1",
            "waivera://settings/unknown",
            "waivera://today/extra",
            "waivera://admin",
            "waivera:chats/7",
            "waivera://chats/" + "1".repeat(600),
        ).forEach { uri -> assertNull(DeepLinkUris.parse(uri), "$uri") }
    }

    @Test
    fun `bad message id query is dropped but the conversation still opens`() {
        assertEquals(DeepLinkTarget.Conversation(7, null), DeepLinkUris.parse("waivera://chats/7?messageId=nope"))
        assertEquals(DeepLinkTarget.Conversation(7, 5), DeepLinkUris.parse("waivera://chats/7?x=1&messageId=5"))
    }

    @Test
    fun `positive id parsing`() {
        assertEquals(42L, DeepLinkUris.positiveId(" 42 "))
        assertNull(DeepLinkUris.positiveId("0"))
        assertNull(DeepLinkUris.positiveId(null))
        assertNull(DeepLinkUris.positiveId("99999999999999999999"))
    }
}
