package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Text on the CTA gradient (my own message bubbles) at both ends of both themes' gradients:
 * the white inks directly on it and on every tint nested in it keep 4.5:1.
 */
class WaiveraOnGradientContrastTest {
    @Test
    fun textOnTheGradientAndItsTints_keepsFourAndAHalf() {
        val cases = listOf(false, true).flatMap { dark ->
            val hero = WaiveraHero.colorsFor(dark)
            listOf(hero.buttonStart, hero.buttonEnd).map { end -> Triple(dark, end, waiveraColors(dark).onGradient()) }
        }
        for ((dark, end, on) in cases) {
            val backgrounds = listOf(
                "gradient" to end,
                "inset" to on.surface.compositeOver(end),
                "strong inset" to on.surfaceMuted.compositeOver(end),
                "mention" to on.accentWash.compositeOver(end),
            )
            val inks = listOf("text" to on.text, "secondary" to on.textSecondary, "link" to on.link)
            for ((name, background) in backgrounds) {
                inks.forEach { (ink, color) ->
                    val ratio = contrast(color.compositeOver(background), background)
                    assertTrue(ratio >= TEXT_MIN, "dark=$dark $ink on $name at $end is $ratio")
                }
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + LUMINANCE_OFFSET) / (minOf(la, lb) + LUMINANCE_OFFSET)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val LUMINANCE_OFFSET = 0.05
    }
}
