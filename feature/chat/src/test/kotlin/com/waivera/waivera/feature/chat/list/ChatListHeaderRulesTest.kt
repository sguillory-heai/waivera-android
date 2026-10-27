package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import kotlin.test.Test
import kotlin.test.assertEquals

/** One connection signal per screen: which banner the inbox header shows for each state. */
class ChatListHeaderRulesTest {
    private val rows = ChatListUiState(content = ChatListContent.Rows)

    @Test
    fun `online and connecting show no banner`() {
        assertEquals(ConnectionBannerState.Connected, connectionBannerState(rows.copy(connection = ConnectionUi.Online)))
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
}
