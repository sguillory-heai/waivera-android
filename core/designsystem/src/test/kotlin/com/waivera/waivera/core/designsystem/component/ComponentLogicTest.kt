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
    fun `bottom actions stay pinned up to large font and flow inline above it`() {
        assertEquals(BottomActionMode.Pinned, bottomActionMode(largeFont = false))
        assertEquals(BottomActionMode.Inline, bottomActionMode(largeFont = true))
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

    @Test
    fun `banner action stays inline when it is short and the font is normal`() {
        assertFalse(bannerActionStacks(fontScale = 1f, actionWidthPx = 200, availableWidthPx = 1000))
    }

    @Test
    fun `banner action stacks at large font scales`() {
        assertTrue(bannerActionStacks(fontScale = 1.5f, actionWidthPx = 100, availableWidthPx = 1000))
        assertTrue(bannerActionStacks(fontScale = 2f, actionWidthPx = 100, availableWidthPx = 1000))
    }

    @Test
    fun `banner action stacks when its label is wider than about a third of the banner`() {
        // "Open Android settings" on a 360dp phone at 100% font.
        assertTrue(bannerActionStacks(fontScale = 1f, actionWidthPx = 400, availableWidthPx = 1000))
        assertFalse(bannerActionStacks(fontScale = 1f, actionWidthPx = 350, availableWidthPx = 1000))
    }

    @Test
    fun `segments share the width while the widest label fits`() {
        assertFalse(segmentsNeedScroll(count = 3, segmentWidthPx = 200, availableWidthPx = 900))
        assertFalse(segmentsNeedScroll(count = 3, segmentWidthPx = 300, availableWidthPx = 900))
    }

    @Test
    fun `segments scroll instead of breaking a word when the widest label does not fit`() {
        assertTrue(segmentsNeedScroll(count = 3, segmentWidthPx = 301, availableWidthPx = 900))
        assertFalse(segmentsNeedScroll(count = 0, segmentWidthPx = 301, availableWidthPx = 900))
    }

    @Test
    fun `segments that cannot share equally but fit side by side fill the row by label width`() {
        // "All", "Unread", "Channels" at 200% font: the widest is over a third, the sum fits.
        assertEquals(SegmentFit.Equal, segmentFit(listOf(200, 300, 250), availableWidthPx = 900))
        assertEquals(
            SegmentFit.Proportional(listOf(150f, 300f, 400f)),
            segmentFit(listOf(150, 300, 400), availableWidthPx = 900),
        )
        assertEquals(SegmentFit.Scroll, segmentFit(listOf(250, 300, 400), availableWidthPx = 900))
        assertEquals(SegmentFit.Equal, segmentFit(emptyList(), availableWidthPx = 900))
    }
}
