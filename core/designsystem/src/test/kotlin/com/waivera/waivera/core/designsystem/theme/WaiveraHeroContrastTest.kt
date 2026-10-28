package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The hero's text pairs hold 4.5:1 at every point of their gradient, sampled along it, in both
 * themes: the header ink (with the glow composited at full strength), the button label, and the
 * page text on the sheet.
 */
class WaiveraHeroContrastTest {
    private val themes = listOf(
        Triple("light", LightWaiveraHeroColors, LightWaiveraColors),
        Triple("dark", DarkWaiveraHeroColors, DarkWaiveraColors),
    )

    @Test
    fun buttonLabel_readsAcrossTheWholeGradient() = themes.forEach { (name, hero, _) ->
        samples(hero.buttonStart, hero.buttonEnd).forEach { fill ->
            assertAtLeast("$name button label", hero.onButton, fill, TEXT_MIN)
        }
    }

    @Test
    fun headerInk_readsOnTheHeaderAndUnderTheGlow() = themes.forEach { (name, hero, _) ->
        samples(hero.headerTop, hero.headerBottom).forEach { fill ->
            assertAtLeast("$name header", hero.onHeader, fill, TEXT_MIN)
            assertAtLeast("$name header under pink glow", hero.onHeader, hero.glowPink.compositeOver(fill), TEXT_MIN)
            assertAtLeast(
                "$name header under violet glow",
                hero.onHeader,
                hero.glowViolet.compositeOver(fill),
                TEXT_MIN,
            )
        }
    }

    @Test
    fun sheet_keepsPageTextAndLinksReadable() = themes.forEach { (name, hero, colors) ->
        assertAtLeast("$name text on sheet", colors.text, hero.sheet, TEXT_MIN)
        assertAtLeast("$name secondary text on sheet", colors.textSecondary, hero.sheet, TEXT_MIN)
        assertAtLeast("$name link on sheet", colors.accent, hero.sheet, TEXT_MIN)
        assertAtLeast("$name field edge on sheet", colors.borderStrong, hero.sheet, UI_MIN)
    }

    @Test
    fun button_edgeStandsOffTheSheet() = themes.forEach { (name, hero, _) ->
        assertAtLeast("$name button start on sheet", hero.buttonStart, hero.sheet, UI_MIN)
        assertAtLeast("$name button end on sheet", hero.buttonEnd, hero.sheet, UI_MIN)
    }

    private fun samples(start: Color, end: Color): List<Color> = (0..STEPS).map {
        lerp(start, end, it / STEPS.toFloat())
    }

    private fun assertAtLeast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$name is ${"%.2f".format(ratio)}:1, needs $minimum:1")
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + OFFSET) / (minOf(la, lb) + OFFSET)
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double =
            if (c <= LINEAR_LIMIT) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
        return RED * channel(color.red) + GREEN * channel(color.green) + BLUE * channel(color.blue)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val UI_MIN = 3.0
        const val STEPS = 20
        const val OFFSET = 0.05
        const val LINEAR_LIMIT = 0.03928f
        const val LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_DIVISOR = 1.055
        const val GAMMA = 2.4
        const val RED = 0.2126
        const val GREEN = 0.7152
        const val BLUE = 0.0722
    }
}
