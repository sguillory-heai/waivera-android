package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The hero's text pairs hold 4.5:1 at every point of their gradient, sampled along it, in both
 * themes: the header ink (with the glow composited at full strength) and the page text on the
 * sheet. The sheet's action is the solid violet primary (v5), checked on the sheet here.
 */
class WaiveraHeroContrastTest {
    private val themes = listOf(
        Triple("light", LightWaiveraHeroColors, LightWaiveraColors),
        Triple("dark", DarkWaiveraHeroColors, DarkWaiveraColors),
    )

    /** Sign in is the solid violet primary (v5 decision 2): its white label reads on the fill. */
    @Test
    fun buttonLabel_readsOnTheSolidFill() = themes.forEach { (name, _, colors) ->
        assertAtLeast("$name button label", colors.onAccent, colors.accentFill, TEXT_MIN)
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

    /**
     * The secondary ink, the card and tile content and the countdown chip, sampled over the hero
     * as it is drawn: the 165° gradient with each glow fading out along its radius. Composited at
     * full strength over every gradient point (as the header ink is above), the pink glow would
     * fail them where it never reaches; sampled where it really is, every point keeps 4.5:1.
     */
    @Test
    fun heroContent_readsWhereTheGlowsReallyAre() = themes.forEach { (name, hero, _) ->
        heroPoints(hero).forEach { point ->
            assertAtLeast("$name secondary on hero", hero.onHeaderSecondary.compositeOver(point), point, TEXT_MIN)
            for ((layerName, layer) in listOf("card" to hero.card, "tile" to hero.tile)) {
                val fill = layer.compositeOver(point)
                assertAtLeast("$name ink on $layerName", hero.onHeader, fill, TEXT_MIN)
                val secondary = hero.onHeaderSecondary.compositeOver(fill)
                assertAtLeast("$name secondary on $layerName", secondary, fill, TEXT_MIN)
                assertAtLeast("$name chip on $layerName", hero.onChip, hero.chip.compositeOver(fill), TEXT_MIN)
                assertAtLeast("$name next glyph on $layerName", hero.nextIcon, fill, UI_MIN)
            }
        }
    }

    /** The ring's track stands apart from the card it sits on. */
    @Test
    fun ringTrack_isVisibleOnTheCard() = themes.forEach { (name, hero, _) ->
        heroPoints(hero).forEach { point ->
            val card = hero.card.compositeOver(point)
            val track = hero.ringTrack.compositeOver(card)
            assertTrue(contrast(track, card) >= RING_TRACK_MIN, "$name ring track vanishes on the card")
        }
    }

    /**
     * v5 rule A: the progress and reward rings are one solid arc, no gradient. It carries the
     * progress, so it keeps the 3:1 non-text minimum against the track it fills and the glass card
     * beside it, at every hero point where the glows really are.
     */
    @Test
    fun ringArc_isSolidAndStandsOffTheTrackAndTheCard() = themes.forEach { (name, hero, _) ->
        assertTrue(hero.ringArc.alpha == 1f, "$name ring arc should be one opaque colour")
        heroPoints(hero).forEach { point ->
            val card = hero.card.compositeOver(point)
            val track = hero.ringTrack.compositeOver(card)
            assertAtLeast("$name ring arc on the track", hero.ringArc, track, UI_MIN)
            assertAtLeast("$name ring arc on the card", hero.ringArc, card, UI_MIN)
        }
    }

    @Test
    fun sheet_keepsPageTextAndLinksReadable() = themes.forEach { (name, hero, colors) ->
        assertAtLeast("$name text on sheet", colors.text, hero.sheet, TEXT_MIN)
        assertAtLeast("$name secondary text on sheet", colors.textSecondary, hero.sheet, TEXT_MIN)
        assertAtLeast("$name link on sheet", colors.link, hero.sheet, TEXT_MIN)
        assertAtLeast("$name field edge on sheet", colors.fieldBorder, hero.sheet, UI_MIN)
    }

    @Test
    fun button_edgeStandsOffTheSheet() = themes.forEach { (name, hero, colors) ->
        assertAtLeast("$name button on sheet", colors.accentFill, hero.sheet, UI_MIN)
    }

    /**
     * The hero's colour on a grid over a tall Today hero (390 × 424 on the concept's frame): the
     * gradient along the 165° line, then each glow at its strength, falling off linearly to
     * nothing at [WaiveraHeroGeometry.GLOW_FADE] of its radius.
     */
    private fun heroPoints(hero: WaiveraHeroColors): List<Color> {
        val radians = Math.toRadians(WaiveraHeroGeometry.HEADER_ANGLE_DEGREES.toDouble())
        val dx = sin(radians)
        val dy = -cos(radians)
        val half = (abs(FRAME_WIDTH * dx) + abs(FRAME_HEIGHT * dy)) / 2
        val pink = Triple(
            FRAME_WIDTH - WaiveraHeroGeometry.PINK_END_INSET,
            WaiveraHeroGeometry.PINK_TOP.toDouble(),
            hero.glowPink,
        )
        val violet = Triple(
            WaiveraHeroGeometry.VIOLET_START.toDouble(),
            FRAME_HEIGHT + WaiveraHeroGeometry.VIOLET_BELOW,
            hero.glowViolet,
        )
        val radii = mapOf(pink to WaiveraHeroGeometry.PINK_RADIUS, violet to WaiveraHeroGeometry.VIOLET_RADIUS)
        return (0..GRID).flatMap { i ->
            (0..GRID).map { j ->
                val x = FRAME_WIDTH * i / GRID
                val y = FRAME_HEIGHT * j / GRID
                val along = ((x - FRAME_WIDTH / 2) * dx + (y - FRAME_HEIGHT / 2) * dy) / (2 * half) + 0.5
                var color = srgbLerp(hero.headerTop, hero.headerBottom, along.coerceIn(0.0, 1.0).toFloat())
                for ((glow, radius) in radii) {
                    val (gx, gy, tint) = glow
                    val reach = hypot(x - gx, y - gy) / radius / WaiveraHeroGeometry.GLOW_FADE
                    val strength = (1 - reach).coerceAtLeast(0.0).toFloat()
                    color = tint.copy(alpha = tint.alpha * strength).compositeOver(color)
                }
                color
            }
        }
    }

    private fun srgbLerp(start: Color, end: Color, t: Float) = Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
    )

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
        const val GRID = 40
        const val FRAME_WIDTH = 390.0
        const val FRAME_HEIGHT = 424.0
        const val RING_TRACK_MIN = 1.2
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
