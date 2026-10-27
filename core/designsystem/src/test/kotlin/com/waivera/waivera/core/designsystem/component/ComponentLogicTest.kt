package com.waivera.waivera.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComponentLogicTest {
    @Test
    fun `initials use the first and last word`() {
        assertEquals("AR", initialsFor("Alex Rivera"))
        assertEquals("AR", initialsFor("  alex   maria   rivera "))
        assertEquals("P", initialsFor("Priya"))
        assertEquals("RO", initialsFor("R. Okonkwo"))
    }

    @Test
    fun `initials are empty when there is nothing to show`() {
        assertEquals("", initialsFor(""))
        assertEquals("", initialsFor("   "))
        assertEquals("", initialsFor("- ."))
    }

    @Test
    fun `initials keep non latin letters`() {
        assertEquals("ÉL", initialsFor("élodie laurent"))
        assertEquals("李", initialsFor("李"))
    }

    @Test
    fun `badge count caps at 99 plus and hides zero`() {
        assertNull(formatBadgeCount(0))
        assertNull(formatBadgeCount(-3))
        assertEquals("1", formatBadgeCount(1))
        assertEquals("99", formatBadgeCount(99))
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("9+", formatBadgeCount(12, max = 9))
    }
}
