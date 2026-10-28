package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
        assertEquals(BottomActionMode.Pinned, bottomActionMode(largeFont = true, pinnedAtLargeFont = true))
        assertEquals(BottomActionMode.Pinned, bottomActionMode(largeFont = false, pinnedAtLargeFont = true))
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
    fun `every avatar is the one soft rounded square, never a circle`() {
        assertEquals(RoundedCornerShape(8.dp), WaiveraShapes.avatar(24.dp))
        assertEquals(RoundedCornerShape(10.dp), WaiveraShapes.avatar(40.dp))
        assertEquals(RoundedCornerShape(23.dp), WaiveraShapes.avatar(96.dp))
        assertNotEquals(CircleShape, WaiveraShapes.avatar(40.dp))
    }

    /**
     * The presence dot sits on the avatar's curve (UI9-09): at the hero size its coloured core
     * meets the corner's 45° point instead of floating in the cut-away corner, and small avatars
     * keep the dot where it was.
     */
    @Test
    fun `presence dot sits on the rounded corner at every size`() {
        val dot = WaiveraSizes.PresenceDot
        listOf(24.dp, 40.dp, 48.dp, 64.dp, 96.dp).forEach { size ->
            val inset = presenceDotInset(size)
            val radius = WaiveraShapes.avatarCornerRadius(size)
            val curve = radius.value * (1f - 1f / sqrt(2f))
            assertTrue(inset >= dot / 2, "the dot at $size pokes out past the avatar's box")
            val coreEdge = inset.value - (dot / 2 - WaiveraSizes.AvatarRing).value
            assertEquals(curve.toDouble(), coreEdge.toDouble(), TOLERANCE, "the dot's core misses the curve at $size")
        }
        assertTrue(presenceDotInset(96.dp) - dot / 2 > 4.dp, "the hero dot still floats in the corner")
        assertTrue(presenceDotInset(40.dp) - dot / 2 < 1.dp, "a list avatar's dot moved noticeably")
    }

    @Test
    fun `tab badge text follows the font scale up to its cap`() {
        assertEquals(1f, navBadgeTextScale(1f))
        assertEquals(1.15f, navBadgeTextScale(1.15f))
        assertEquals(NAV_BADGE_MAX_TEXT_SCALE, navBadgeTextScale(1.3f))
        assertEquals(NAV_BADGE_MAX_TEXT_SCALE, navBadgeTextScale(2f))
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
    fun `banner action stacks when the message would take more than two lines beside it`() {
        // The offline banner's "Showing your saved conversations..." beside "Retry now" (UI4-06).
        assertTrue(stacks(messageLinesBeside = 3))
        assertFalse(stacks(messageLinesBeside = 2))
        assertFalse(stacks(messageLinesBeside = 1))
    }

    private fun stacks(messageLinesBeside: Int) = bannerActionStacks(
        fontScale = 1f,
        actionWidthPx = 200,
        availableWidthPx = 1000,
        messageLinesBeside = messageLinesBeside,
    )

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

    private companion object {
        const val TOLERANCE = 0.01
    }
}
