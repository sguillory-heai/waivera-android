package com.waivera.waivera.navigation

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {
    @Test
    fun startsOnTodayAtExit() {
        val state = NavState()
        assertEquals(listOf<AppRoute>(TodayRoute), state.visibleBackStack)
        assertTrue(state.isAtExit)
        assertSame(state, state.back())
    }

    @Test
    fun otherTabsSitOnTopOfToday() {
        val state = NavState().select(TopLevelTab.Chat)
        assertEquals(listOf(TodayRoute, ChatListRoute), state.visibleBackStack)
        assertFalse(state.isAtExit)
    }

    @Test
    fun backFromAnotherTabRootReturnsToTodayAndKeepsThatTabsStack() {
        val chat = NavState().select(TopLevelTab.Chat).push(ConversationRoute(42))
        val back = chat.back().back()
        assertEquals(TopLevelTab.Today, back.selected)
        assertEquals(listOf<AppRoute>(ChatListRoute), back.stack(TopLevelTab.Chat))
    }

    @Test
    fun tabsKeepTheirOwnStacks() {
        val state = NavState()
            .select(TopLevelTab.Chat)
            .push(ConversationRoute(42))
            .select(TopLevelTab.Schedule)
            .push(CalendarEventRoute("evt_1"))
            .select(TopLevelTab.Chat)
        assertEquals(ConversationRoute(42), state.top)
        assertEquals(listOf(ScheduleRoute, CalendarEventRoute("evt_1")), state.stack(TopLevelTab.Schedule))
    }

    @Test
    fun reselectingATabPopsToItsRoot() {
        val state = NavState().select(TopLevelTab.Chat).push(ConversationRoute(1)).push(ThreadRoute(1, 2))
        assertEquals(listOf<AppRoute>(ChatListRoute), state.select(TopLevelTab.Chat).stack(TopLevelTab.Chat))
    }

    @Test
    fun pushingTheRouteOnTopIsIgnored() {
        val state = NavState().push(ProfileRoute)
        assertSame(state, state.push(ProfileRoute))
    }

    @Test
    fun pushingATabRootSelectsThatTab() {
        val state = NavState().push(ScheduleRoute)
        assertEquals(TopLevelTab.Schedule, state.selected)
        assertEquals(listOf<AppRoute>(ScheduleRoute), state.stack(TopLevelTab.Schedule))
    }

    @Test
    fun entryKeysAreUniqueWhenARouteRepeats() {
        val state = NavState()
            .push(PersonRoute("7"))
            .push(ConversationRoute(1))
            .push(PersonRoute("7"))
            .select(TopLevelTab.Chat)
            .push(ConversationRoute(1))
        val keys = state.visibleEntries.map(ShellEntry::key)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun pushIntoAnOpenConversationChangesNothing() {
        val state = NavState().open(DeepLinkTarget.Conversation(42))
        assertSame(state, state.open(DeepLinkTarget.Conversation(42)))
    }

    @Test
    fun pushForANewerMessageMovesOnlyTheHighlight() {
        val state = NavState().open(DeepLinkTarget.Conversation(42, messageId = 100))
        val next = state.open(DeepLinkTarget.Conversation(42, messageId = 101))
        assertEquals(listOf(ChatListRoute, ConversationRoute(42, 101)), next.stack(TopLevelTab.Chat))
        assertEquals(state.visibleEntries.last().key, next.visibleEntries.last().key)
    }

    @Test
    fun deepLinkFromAnotherTabSelectsTheTargetTab() {
        val state = NavState().push(SettingsRoute).open(DeepLinkTarget.Conversation(42))
        assertEquals(TopLevelTab.Chat, state.selected)
        assertEquals(listOf(TodayRoute, SettingsRoute, ChatListRoute, ConversationRoute(42)), state.visibleBackStack)
    }

    @Test
    fun deepLinkReplacesADeeperChatStack() {
        val state = NavState()
            .select(TopLevelTab.Chat)
            .push(ConversationRoute(1))
            .push(ThreadRoute(1, 5))
            .open(DeepLinkTarget.Conversation(42))
        assertEquals(listOf(ChatListRoute, ConversationRoute(42)), state.stack(TopLevelTab.Chat))
    }

    @Test
    fun survivesSerialization() {
        val state = NavState()
            .open(DeepLinkTarget.Thread(42, 7, 9))
            .push(PersonRoute("u-1"))
            .select(TopLevelTab.Schedule)
            .push(CalendarEventRoute("evt_8f2b19c4"))
        val json = Json { ignoreUnknownKeys = true }
        val restored = json.decodeFromString(NavState.serializer(), json.encodeToString(NavState.serializer(), state))
        assertEquals(state, restored)
    }
}
