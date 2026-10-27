package com.waivera.waivera.core.designsystem.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `milestone badge scales with the avatar above a minimum`() {
        assertEquals(17.dp, milestoneBadgeSize(32.dp))
        assertEquals(19.dp, milestoneBadgeSize(44.dp))
        assertEquals(41.dp, milestoneBadgeSize(96.dp))
    }

    @Test
    fun `milestone years show on large anniversary badges only`() {
        val anniversary = MilestoneBadge(MilestoneKind.WorkAnniversary, years = 5)
        assertTrue(showsMilestoneYears(anniversary, 40.dp))
        assertFalse(showsMilestoneYears(anniversary, 32.dp))
        assertFalse(showsMilestoneYears(anniversary.copy(years = null), 64.dp))
        assertFalse(showsMilestoneYears(MilestoneBadge(MilestoneKind.Multiple, years = 5), 64.dp))
    }
}
