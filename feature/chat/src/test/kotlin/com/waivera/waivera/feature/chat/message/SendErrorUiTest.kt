package com.waivera.waivera.feature.chat.message

import kotlin.test.assertEquals
import org.junit.Test

class SendErrorUiTest {
    private val offline = SendErrorKind.Offline

    @Test
    fun noFailureShowsNothing() {
        assertEquals(SendErrorUi.None, SendErrorUi.of(null, listOf(0), listOf(1, 2)))
    }

    @Test
    fun aRefusedSendWithNoRowGetsTheFullBanner() {
        assertEquals(SendErrorUi.Banner(offline), SendErrorUi.of(offline, emptyList(), listOf(0, 1)))
    }

    @Test
    fun aFailedRowOnScreenIsItsOwnSignal() {
        assertEquals(SendErrorUi.None, SendErrorUi.of(offline, listOf(0), listOf(0, 1, 2)))
    }

    @Test
    fun failedRowsScrolledAwayGetOneCompactBannerPointingAtTheNewest() {
        assertEquals(
            SendErrorUi.Offscreen(count = 2, index = 0),
            SendErrorUi.of(offline, listOf(0, 3), listOf(6, 7, 8)),
        )
    }

    @Test
    fun beforeTheFirstLayoutNothingShowsYet() {
        assertEquals(SendErrorUi.None, SendErrorUi.of(offline, listOf(0), emptyList()))
    }
}
