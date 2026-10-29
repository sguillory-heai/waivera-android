package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The long-press focus overlay's scrim (wave 30 O3). The sheets' scrim left the chat's text
 * readable beside the lifted bubble; the focus scrim's token takes the text behind it under the
 * 3:1 a person needs to make anything out, in both themes, while the page still shows through as
 * dimmed, not blacked out. The sheets keep their own, lighter scrim.
 */
class WaiveraFocusScrimTest {
    private val light = waiveraColors(darkTheme = false)
    private val dark = waiveraColors(darkTheme = true)

    @Test
    fun theTokenIsStrongerThanTheSheetScrimAndStrongerStillInPlumDark() {
        assertEquals(WaiveraPalette.FOCUS_SCRIM_ALPHA_LIGHT, light.focusScrim.alpha, TOLERANCE)
        assertEquals(WaiveraPalette.FOCUS_SCRIM_ALPHA_DARK, dark.focusScrim.alpha, TOLERANCE)
        assertTrue(light.focusScrim.alpha in LIGHT_RANGE, "light focus scrim ${light.focusScrim.alpha}")
        assertTrue(dark.focusScrim.alpha > light.focusScrim.alpha, "plum dark is not a little stronger")
        assertTrue(dark.focusScrim.alpha - light.focusScrim.alpha <= MAX_DARK_LEAD, "plum dark is much stronger")
        listOf(light, dark).forEach { colors ->
            assertTrue(colors.focusScrim.alpha > colors.scrim.alpha, "the focus scrim is no stronger than a sheet's")
            assertEquals(WaiveraPalette.SCRIM_ALPHA, colors.scrim.alpha, TOLERANCE, "the sheets' scrim changed")
            assertEquals(Color.Black.copy(alpha = colors.focusScrim.alpha), colors.focusScrim, "it is not a plain dim")
        }
    }

    @Test
    fun theTextBehindStopsReadingButThePageStillShows() {
        for ((theme, colors) in listOf("light" to light, "dark" to dark)) {
            for ((name, page) in listOf("background" to colors.background, "surface" to colors.surface)) {
                val ink = colors.focusScrim.compositeOver(colors.text)
                val under = colors.focusScrim.compositeOver(page)
                val ratio = contrast(ink, under)
                assertTrue(
                    ratio < HIDDEN_MAX,
                    "$theme text on $name reads under the scrim at ${"%.2f".format(ratio)}:1",
                )
                assertTrue(ratio > 1.0, "$theme $name is one flat colour under the scrim")
            }
        }
        // In light the page stays a mid grey, so it reads as dimmed, not as a black sheet.
        assertTrue(luminance(light.focusScrim.compositeOver(light.background)) >= LIGHT_DIMMED_MIN)
    }

    private companion object {
        const val TOLERANCE = 0.001f
        val LIGHT_RANGE = 0.6f..0.7f
        const val MAX_DARK_LEAD = 0.1f

        /** Under 3:1 (WCAG's non-text minimum) less a margin: text that no longer reads. */
        const val HIDDEN_MAX = 2.5

        /** The dimmed light page's least luminance; a black sheet is 0. */
        const val LIGHT_DIMMED_MIN = 0.04

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        fun luminance(color: Color): Double {
            fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
    }
}
