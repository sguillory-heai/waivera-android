package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertTrue
import org.junit.Test

/**
 * My own message bubble (v5 decision 3, option B): the soft violet tint in light, the lifted plum
 * in dark. Every ink inside it keeps 4.5:1 on the tint and on each piece nested in it (the file
 * card and someone else's reaction on the surface, my reaction on the tonal fill), and the marks
 * that carry a state (my reaction's outline) keep 3:1.
 */
class WaiveraMyBubbleContrastTest {
    private val themes = listOf(false, true)

    @Test
    fun textInTheBubbleAndItsNestedPieces_keepsFourAndAHalf() {
        for (dark in themes) {
            val colors = waiveraColors(dark)
            val fill = colors.myBubble
            val on = colors.onMyBubble()
            val backgrounds = listOf(
                "bubble" to fill,
                "file card" to on.surface.compositeOver(fill),
                "their reaction" to on.background.compositeOver(fill),
                "mention arrival wash" to on.accentWash.compositeOver(fill),
            )
            val inks = listOf(
                "text" to on.text,
                "secondary" to on.textSecondary,
                "link and mention" to on.link,
                "accent" to on.accent,
                "danger (upload failed)" to on.dangerText,
                "success" to on.successText,
                "warning" to on.warningText,
            )
            for ((name, background) in backgrounds) {
                inks.forEach { (ink, color) ->
                    val ratio = contrast(color.compositeOver(background), background)
                    assertTrue(ratio >= TEXT_MIN, "dark=$dark $ink on $name is ${"%.2f".format(ratio)}:1")
                }
            }
            val mine = on.accentSoft.compositeOver(fill)
            val mineRatio = contrast(on.onAccentSoft, mine)
            assertTrue(mineRatio >= TEXT_MIN, "dark=$dark my reaction's count is ${"%.2f".format(mineRatio)}:1")
            val outline = contrast(on.accent, fill)
            assertTrue(
                outline >= UI_MIN,
                "dark=$dark my reaction's outline on the bubble is ${"%.2f".format(outline)}:1",
            )
        }
    }

    /**
     * The owner chose the tint knowing it sits under 3:1 against the page (the board measured
     * 1.2 / 1.5:1): ownership is carried by the right alignment and the tint, not by an edge.
     * This holds the tint to the deeper step picked over the board, so it never drifts back toward
     * the page, and keeps it a visible step off their bubble (the surface).
     */
    @Test
    fun bubbleTint_standsApartFromThePageAndTheirBubble() {
        for (dark in themes) {
            val colors = waiveraColors(dark)
            val page = contrast(colors.myBubble, colors.background)
            val theirs = contrast(colors.myBubble, colors.surface)
            val boardStep = if (dark) BOARD_STEP_DARK else BOARD_STEP_LIGHT
            assertTrue(
                page > boardStep,
                "dark=$dark bubble on page is ${"%.2f".format(page)}:1, the board's was $boardStep",
            )
            assertTrue(theirs >= APART_MIN, "dark=$dark mine and theirs are ${"%.2f".format(theirs)}:1 apart")
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + LUMINANCE_OFFSET) / (minOf(la, lb) + LUMINANCE_OFFSET)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val UI_MIN = 3.0

        /** The board's tint against the page (#E9DFFB on #F5F2F9, #3A2D52 on #120F17). */
        const val BOARD_STEP_LIGHT = 1.15
        const val BOARD_STEP_DARK = 1.52

        /** Mine and theirs: a visible fill step, as for avatar fills and skeletons. */
        const val APART_MIN = 1.3
        const val LUMINANCE_OFFSET = 0.05
    }
}
