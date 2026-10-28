package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Every text and control colour pair in both themes, against WCAG 2.2: 4.5:1 for text and
 * glyphs, 3:1 for control edges and outlines. Text on the brand wash is checked at each of the
 * gradient's three stops, the wash's lightest and darkest points.
 */
class WaiveraColorsContrastTest {
    private val themes = listOf("light" to waiveraColors(darkTheme = false), "dark" to waiveraColors(darkTheme = true))

    @Test
    fun text_onEveryPageSurface() = forEachTheme { colors ->
        for ((surfaceName, surface) in pageSurfaces(colors)) {
            text("text on $surfaceName", colors.text, surface)
            text("textSecondary on $surfaceName", colors.textSecondary, surface)
            text("accent on $surfaceName", colors.accent, surface)
            text("dangerText on $surfaceName", colors.dangerText, surface)
            text("successText on $surfaceName", colors.successText, surface)
            text("warningText on $surfaceName", colors.warningText, surface)
        }
    }

    @Test
    fun text_onFillsAndContainers() = forEachTheme { colors ->
        text("onAccent on accentFill (primary button)", colors.onAccent, colors.accentFill)
        text("onAccent on accent (badges)", colors.onAccent, colors.accent)
        text("onAccentSoft on accentSoft", colors.onAccentSoft, colors.accentSoft)
        text("text on secondaryContainer", colors.text, colors.secondaryContainer)
        text("onDangerFill on dangerFill", colors.onDangerFill, colors.dangerFill)
        text("onDangerContainer on dangerContainer", colors.onDangerContainer, colors.dangerContainer)
        text("onWarningContainer on warningContainer", colors.onWarningContainer, colors.warningContainer)
        text("onBirthdayContainer on birthdayContainer", colors.onBirthdayContainer, colors.birthdayContainer)
        text(
            "onAnniversaryContainer on anniversaryContainer",
            colors.onAnniversaryContainer,
            colors.anniversaryContainer,
        )
        text("onNewHireContainer on newHireContainer", colors.onNewHireContainer, colors.newHireContainer)
        text("onMultipleContainer on multipleContainer", colors.onMultipleContainer, colors.multipleContainer)
    }

    @Test
    fun iconTiles_glyphOnContainer() = forEachTheme { colors ->
        val tiles = colors.tiles
        listOf(
            "brand" to tiles.brand,
            "rose" to tiles.rose,
            "amber" to tiles.amber,
            "teal" to tiles.teal,
            "neutral" to tiles.neutral,
            "danger" to tiles.danger,
        ).forEach { (name, tint) -> text("tile $name glyph", tint.content, tint.container) }
    }

    @Test
    fun brandWash_keepsTextReadable() = forEachTheme { colors ->
        for (stop in WaiveraBrand.Colors) {
            val wash = stop.copy(alpha = colors.brandWashAlpha).compositeOver(colors.surface)
            text("text on wash $stop", colors.text, wash)
            text("textSecondary on wash $stop", colors.textSecondary, wash)
        }
    }

    @Test
    fun controls_meetTheNonTextMinimum() = forEachTheme { colors ->
        for ((surfaceName, surface) in pageSurfaces(colors)) {
            ui("borderStrong on $surfaceName", colors.borderStrong, surface)
        }
        ui("accentFill on background (primary button edge)", colors.accentFill, colors.background)
        ui("accent on accentSoft (selected outline)", colors.accent, colors.accentSoft)
    }

    @Test
    fun skeleton_readsAsLoadingNotBlank() = forEachTheme { colors ->
        check("skeletonBase on background", colors.skeletonBase, colors.background, SKELETON_MIN)
    }

    @Test
    fun dark_isOnePlumFamilyWithNoSlateBlue() {
        val dark = waiveraColors(darkTheme = true)
        // Plum: red at least as strong as green, and blue only a little above red. Slate-blue
        // (#1F2937, #111827) has blue far above red.
        listOf(dark.background, dark.surface, dark.surfaceMuted, dark.border).forEach { color ->
            assertTrue(color.red >= color.green, "$color leans green")
            assertTrue(color.blue - color.red <= PLUM_MAX_BLUE_LEAD, "$color leans slate-blue")
        }
        assertTrue(luminance(dark.background) < luminance(dark.surface), "surface steps up from the page")
        assertTrue(luminance(dark.surface) < luminance(dark.surfaceMuted), "raised steps up from the surface")
    }

    private fun forEachTheme(block: ContrastScope.(WaiveraColors) -> Unit) {
        themes.forEach { (name, colors) -> ContrastScope(name).block(colors) }
    }

    private fun pageSurfaces(colors: WaiveraColors) = listOf(
        "background" to colors.background,
        "surface" to colors.surface,
        "surfaceMuted" to colors.surfaceMuted,
    )

    private inner class ContrastScope(private val theme: String) {
        fun text(name: String, foreground: Color, background: Color) = check(name, foreground, background, TEXT_MIN)

        fun ui(name: String, foreground: Color, background: Color) = check(name, foreground, background, UI_MIN)

        fun check(name: String, foreground: Color, background: Color, minimum: Double) {
            val ratio = contrast(foreground, background)
            assertTrue(ratio >= minimum, "$theme $name is ${"%.2f".format(ratio)}:1, needs $minimum:1")
        }
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val UI_MIN = 3.0
        const val SKELETON_MIN = 1.3
        const val PLUM_MAX_BLUE_LEAD = 0.07f

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** WCAG relative luminance of an opaque sRGB colour. */
        fun luminance(color: Color): Double {
            fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
    }
}
