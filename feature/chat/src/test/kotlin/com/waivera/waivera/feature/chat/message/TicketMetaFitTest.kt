package com.waivera.waivera.feature.chat.message

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** When a ticket meta row stays side by side and how wide its value may be (B3, wave 28). */
class TicketMetaFitTest {
    @Test
    fun aValueThatFitsStaysBesideItsLabelAtItsOwnWidth() {
        assertEquals(PAIR - GAP - SHORT_LABEL, ticketMetaValueMaxWidth(PAIR, GAP, SHORT_LABEL, valueWidth = 60))
    }

    @Test
    fun aLongValueWrapsInTheRoomTheLabelLeaves() {
        assertEquals(PAIR - GAP - SHORT_LABEL, ticketMetaValueMaxWidth(PAIR, GAP, SHORT_LABEL, valueWidth = 900))
    }

    @Test
    fun aShortValueBesideAWideLabelStillFits() {
        assertEquals(PAIR - GAP - WIDE_LABEL, ticketMetaValueMaxWidth(PAIR, GAP, WIDE_LABEL, valueWidth = 40))
    }

    @Test
    fun aLongValueBesideAWideLabelStacks() {
        assertNull(ticketMetaValueMaxWidth(PAIR, GAP, WIDE_LABEL, valueWidth = 900))
    }

    @Test
    fun aLabelWiderThanTheRowStacks() {
        assertNull(ticketMetaValueMaxWidth(PAIR, GAP, labelWidth = PAIR + 1, valueWidth = 0))
    }

    private companion object {
        const val PAIR = 400
        const val GAP = 16
        const val SHORT_LABEL = 100

        // Leaves 134 px, less than half the row.
        const val WIDE_LABEL = 250
    }
}
