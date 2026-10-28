package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One connection signal per screen: which banner the inbox header shows for each state. */
class ChatListHeaderRulesTest {
    private val rows = ChatListUiState(content = ChatListContent.Rows)

    @Test
    fun `online and connecting show no banner`() {
        assertEquals(
            ConnectionBannerState.Connected,
            connectionBannerState(rows.copy(connection = ConnectionUi.Online)),
        )
        assertEquals(
            ConnectionBannerState.Connected,
            connectionBannerState(rows.copy(connection = ConnectionUi.Connecting)),
        )
    }

    @Test
    fun `offline and reconnecting own the banner`() {
        assertEquals(ConnectionBannerState.Offline, connectionBannerState(rows.copy(connection = ConnectionUi.Offline)))
        assertEquals(
            ConnectionBannerState.Reconnecting,
            connectionBannerState(rows.copy(connection = ConnectionUi.Reconnecting)),
        )
    }

    @Test
    fun `a full screen load error is the only signal even while offline`() {
        val error = ChatListUiState(
            content = ChatListContent.Error(WaiveraError.Network().messageRes),
            connection = ConnectionUi.Offline,
        )
        assertEquals(ConnectionBannerState.Connected, connectionBannerState(error))
    }

    @Test
    fun `online never shows a pill, connecting does when nothing else is up`() {
        fun pill(state: ChatListUiState) = showsConnectingPill(state, connectionBannerState(state))
        assertFalse(pill(rows.copy(connection = ConnectionUi.Online)), "the normal state isn't news")
        assertTrue(pill(rows.copy(connection = ConnectionUi.Connecting)))
        assertFalse(pill(rows.copy(connection = ConnectionUi.Offline)), "the banner owns offline")
        assertFalse(
            pill(
                rows.copy(connection = ConnectionUi.Connecting, refreshErrorRes = WaiveraError.Server(500).messageRes),
            ),
            "the refresh banner is already up",
        )
        assertFalse(
            pill(ChatListUiState(content = ChatListContent.Error(WaiveraError.Network().messageRes))),
            "the full-screen error owns the message",
        )
    }

    @Test
    fun `rows share one card, rounded at its first and last row only`() {
        assertEquals(InboxCardPosition.Single, InboxCardPosition.of(index = 0, count = 1))
        assertEquals(InboxCardPosition.First, InboxCardPosition.of(index = 0, count = 3))
        assertEquals(InboxCardPosition.Middle, InboxCardPosition.of(index = 1, count = 3))
        assertEquals(InboxCardPosition.Last, InboxCardPosition.of(index = 2, count = 3))
        assertTrue(InboxCardPosition.Single.isLast, "a lone row has no divider under it")
        assertFalse(InboxCardPosition.First.isLast, "rows above the last keep their divider")
    }
}
