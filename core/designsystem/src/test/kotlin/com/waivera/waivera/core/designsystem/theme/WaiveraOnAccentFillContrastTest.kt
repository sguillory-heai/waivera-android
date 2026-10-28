package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Text on the solid brand violet (my own message bubbles) in both themes: the white inks directly
 * on it and on every tint nested in it (file card, reaction, mention) keep 4.5:1.
 */
class WaiveraOnAccentFillContrastTest {
    @Test
    fun textOnTheFillAndItsTints_keepsFourAndAHalf() {
        for (dark in listOf(false, true)) {
            val colors = waiveraColors(dark)
            val fill = colors.accentFill
            val on = colors.onAccentFill()
            val backgrounds = listOf(
                "fill" to fill,
                "reaction" to on.background.compositeOver(fill),
                "inset" to on.surface.compositeOver(fill),
                "strong inset" to on.surfaceMuted.compositeOver(fill),
                "mention" to on.accentWash.compositeOver(fill),
            )
            val inks = listOf("text" to on.text, "secondary" to on.textSecondary, "link" to on.link)
            for ((name, background) in backgrounds) {
                inks.forEach { (ink, color) ->
                    val ratio = contrast(color.compositeOver(background), background)
                    assertTrue(ratio >= TEXT_MIN, "dark=$dark $ink on $name ($fill) is $ratio")
                }
            }
        }
    }

    /** The bubble stands apart from the page it sits on (v5: 6.2 / 3.6:1). */
    @Test
    fun bubbleFill_standsOffThePage() {
        for (dark in listOf(false, true)) {
            val colors = waiveraColors(dark)
            val ratio = contrast(colors.accentFill, colors.background)
            assertTrue(ratio >= UI_MIN, "dark=$dark bubble on page is $ratio")
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
        const val LUMINANCE_OFFSET = 0.05
    }
}
