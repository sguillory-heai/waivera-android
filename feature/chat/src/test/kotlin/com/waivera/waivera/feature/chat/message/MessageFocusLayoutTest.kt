package com.waivera.waivera.feature.chat.message

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focus overlay's placement and entrance as plain functions, in dp-sized pixels (density 1):
 * a 360x640 phone upright and on its side, with the menu sizes the 100% and 200% fonts give.
 */
class MessageFocusLayoutTest {
    private val gap = 8
    private val pill = IntSize(316, 56)
    private val menu100 = IntSize(232, 257)
    private val menu200 = IntSize(320, 300)

    private fun geometry(
        window: IntSize,
        menu: IntSize,
        anchor: IntRect?,
        mine: Boolean = true,
        pill: IntSize? = this.pill,
    ): FocusGeometry {
        val safe = IntRect(16, 24 + gap, window.width - 16, window.height - 48 - gap)
        return FocusGeometry(
            safe = safe,
            anchor = anchor,
            mine = mine,
            pill = pill,
            menu = menu,
            gap = gap,
            minBubbleHeight = 56,
            maxBubbleHeight = (safe.height * ChatDimens.FOCUS_BUBBLE_MAX_FRACTION).toInt(),
            minBubbleWidth = 160,
            fallbackBubbleWidth = 270,
        )
    }

    /** Lays out a bubble as tall as it can be and checks nothing leaves the safe area or overlaps. */
    private fun assertFits(g: FocusGeometry, naturalHeight: Int, label: String): FocusPlacement {
        val frame = g.frame()
        val bubble = IntSize(frame.bubbleMaxWidth, minOf(naturalHeight, frame.bubbleMaxHeight))
        val at = g.place(frame, bubble)
        val bubbleRect = IntRect(at.bubble.x, at.bubble.y, at.bubble.x + bubble.width, at.bubble.y + bubble.height)
        val menuRect = IntRect(at.menu.x, at.menu.y, at.menu.x + g.menu.width, at.menu.y + g.menu.height)
        val rects = listOfNotNull(
            "bubble" to bubbleRect,
            "menu" to menuRect,
            at.pill?.let { p -> g.pill?.let { "pill" to IntRect(p.x, p.y, p.x + it.width, p.y + it.height) } },
        )
        rects.forEach { (name, r) ->
            assertTrue("$label: the $name ($r) leaves the safe area ${g.safe}", g.safe.contains(r))
        }
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertFalse(
                    "$label: ${rects[i].first} overlaps ${rects[j].first}",
                    rects[i].second.overlaps(rects[j].second),
                )
            }
        }
        return at
    }

    private fun IntRect.contains(other: IntRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    @Test
    fun anUprightPhoneStacksTheMenuUnderTheBubble() {
        val near = IntRect(120, 500, 344, 540)
        val g = geometry(IntSize(360, 640), menu100, near)
        assertEquals(FocusArrangement.Stacked, g.frame().arrangement)
        val at = assertFits(g, naturalHeight = 40, label = "short, 100%")
        assertTrue("a bubble by the composer moves up so the menu fits", at.bubble.y < near.top)
        assertEquals("the menu lines up with my bubble's end", near.right - menu100.width, at.menu.x)
    }

    @Test
    fun aLongMessageIsClampedSoTheMenuNeverLeavesTheScreen() {
        val tall = IntRect(48, -640, 344, 550)
        for ((menu, font) in listOf(menu100 to "100%", menu200 to "200%")) {
            val g = geometry(IntSize(360, 640), menu, tall)
            val frame = g.frame()
            assertEquals(FocusArrangement.Stacked, frame.arrangement)
            assertTrue("$font: the bubble is clamped", frame.bubbleMaxHeight < 640)
            val at = assertFits(g, naturalHeight = 6_000, label = "3,000 characters, $font")
            assertTrue("$font: the bubble opens at its start, at the top of the safe area", at.pill?.y == g.safe.top)
        }
    }

    @Test
    fun landscapePutsTheMenuBesideTheBubble() {
        for ((menu, font) in listOf(IntSize(232, 257) to "100%", IntSize(280, 265) to "200%")) {
            val g = geometry(IntSize(640, 360), menu, IntRect(300, 200, 608, 260))
            assertEquals("$font: the menu does not fit under", FocusArrangement.Beside, g.frame().arrangement)
            assertFits(g, naturalHeight = 6_000, label = "landscape long, $font")
            assertFits(g, naturalHeight = 40, label = "landscape short, $font")
            val theirs = geometry(IntSize(640, 360), menu, IntRect(56, 200, 300, 260), mine = false)
            assertFits(theirs, naturalHeight = 400, label = "landscape theirs, $font")
        }
    }

    @Test
    fun theMenuIsHeldBesideThePillWhenTheWindowIsWide() {
        assertEquals(232, focusMenuWidth(safeWidth = 608, pillWidth = 316, gap = gap, target = 232, minBeside = 200))
        assertEquals(284, focusMenuWidth(safeWidth = 608, pillWidth = 316, gap = gap, target = 320, minBeside = 200))
        assertEquals(320, focusMenuWidth(safeWidth = 328, pillWidth = 316, gap = gap, target = 320, minBeside = 200))
        assertEquals(232, focusMenuWidth(safeWidth = 328, pillWidth = 316, gap = gap, target = 232, minBeside = 200))
    }

    @Test
    fun withoutReactionsThereIsNoPill() {
        val g = geometry(IntSize(360, 640), menu100, IntRect(16, 300, 260, 360), mine = false, pill = null)
        val at = assertFits(g, naturalHeight = 60, label = "read-only")
        assertEquals(null, at.pill)
        assertEquals("someone else's bubble keeps its start", 16, at.bubble.x)
    }

    @Test
    fun reducedMotionOnlyFades() {
        val reduced = focusEntrance(WaiveraMotionScheme.Reduced)
        assertEquals(1f, reduced.bubbleFrom)
        assertEquals(1f, reduced.piecesFrom)
        assertFalse("nothing travels", reduced.travels)
        assertTrue("the bubble fades in with the rest", reduced.bubbleFades)
        assertTrue("nothing springs", reduced.lift is SnapSpec)
        assertTrue("it fades", reduced.fade is TweenSpec)
    }

    @Test
    fun withMotionTheBubbleLiftsFromItsSinkOnASpring() {
        val standard = focusEntrance(WaiveraMotionScheme.Standard)
        assertEquals("it starts where the long press left it", PressDepth.Button.longPressScale, standard.bubbleFrom)
        assertTrue(standard.travels)
        assertFalse("the real bubble does not fade", standard.bubbleFades)
        assertTrue("the pill and menu grow out of the bubble", standard.piecesFrom < 1f)
        assertTrue(standard.lift is SpringSpec)
    }

    /**
     * Wave 30 O1: at every 16 ms frame of the pop spring, upright and beside, rising from the
     * composer and dropping from under the top bar, short and clamped tall: the pill's bottom stays
     * at least the gap above the bubble's drawn top, and stacked, the menu's top at least the gap
     * below its drawn bottom. At rest nothing is shifted.
     */
    @Test
    fun theSpringNeverCarriesTheBubbleUnderThePillOrTheMenu() {
        val spring = focusEntrance(WaiveraMotionScheme.Standard)
        val animation = TargetBasedAnimation(spring.lift, Float.VectorConverter, 0f, 1f)
        val frames = (0..animation.durationNanos / FRAME_NANOS).map { animation.getValueFromNanos(it * FRAME_NANOS) }
        assertTrue("the pop spring does not overshoot, so this checks nothing", frames.any { it > 1f })
        val cases = listOf(260f, 40f, -640f, 0f).flatMap { travel ->
            listOf(40, 268).flatMap { height -> listOf(true, false).map { stacked -> Triple(travel, height, stacked) } }
        }
        for ((travel, height, stacked) in cases) {
            val label = "travel $travel, height $height, ${if (stacked) "stacked" else "beside"}"
            frames.forEachIndexed { frame, progress ->
                assertClearOfTheBubble(
                    liftFrame(progress, spring.bubbleFrom, travel, height, menuFollows = stacked),
                    height,
                    stacked,
                    "$label, frame $frame",
                )
            }
            assertAtRest(liftFrame(1f, spring.bubbleFrom, travel, height, menuFollows = stacked), label)
        }
    }

    /** With the bubble at rest at y 300: the pill and (stacked) the menu keep the gap from its drawn edges. */
    private fun assertClearOfTheBubble(f: LiftFrame, height: Int, stacked: Boolean, label: String) {
        val restTop = 300
        val inset = height * (1f - f.bubbleScale) / 2f
        val drawnTop = restTop + f.bubbleShift + inset
        val drawnBottom = restTop + height + f.bubbleShift - inset
        val pillBottom = restTop - gap + f.pillShift
        assertTrue(
            "$label: the pill ($pillBottom) is under ${gap}px from the bubble ($drawnTop)",
            drawnTop - pillBottom >= gap - EPSILON,
        )
        if (stacked) {
            val menuTop = restTop + height + gap + f.menuShift
            assertTrue(
                "$label: the menu ($menuTop) is under ${gap}px from the bubble ($drawnBottom)",
                menuTop - drawnBottom >= gap - EPSILON,
            )
        } else {
            assertEquals("$label: the menu beside moves", 0f, f.menuShift, EPSILON)
        }
    }

    @Test
    fun withReducedMotionNothingIsShifted() {
        val reduced = focusEntrance(WaiveraMotionScheme.Reduced)
        assertAtRest(liftFrame(1f, reduced.bubbleFrom, travel = 0f, bubbleHeight = 268, menuFollows = true), "reduced")
    }

    private fun assertAtRest(f: LiftFrame, label: String) {
        assertEquals("$label: the bubble's scale at rest", 1f, f.bubbleScale, EPSILON)
        assertEquals("$label: the bubble's shift at rest", 0f, f.bubbleShift, EPSILON)
        assertEquals("$label: the pill's shift at rest", 0f, f.pillShift, EPSILON)
        assertEquals("$label: the menu's shift at rest", 0f, f.menuShift, EPSILON)
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
        const val EPSILON = 0.01f
    }
}
