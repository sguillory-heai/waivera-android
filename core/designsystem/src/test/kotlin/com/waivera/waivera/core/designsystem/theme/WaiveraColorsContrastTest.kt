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
            text("link on $surfaceName", colors.link, surface)
            text("dangerText on $surfaceName", colors.dangerText, surface)
            text("successText on $surfaceName", colors.successText, surface)
            text("warningText on $surfaceName", colors.warningText, surface)
        }
    }

    /**
     * Text and icons that set no colour of their own take the theme's default content ink. It was
     * Compose's black, unreadable on the dark sheet card (SheetPieces), so it is checked on every
     * page surface in both themes.
     */
    @Test
    fun defaultContentColor_onEveryPageSurface() = forEachTheme { colors ->
        for ((surfaceName, surface) in pageSurfaces(colors)) {
            text("default content colour on $surfaceName", colors.defaultContentColor(), surface)
        }
    }

    /**
     * Tertiary text (the other month's dates) is quieter than secondary but still meets the 3:1
     * non-text minimum on every page surface (UI10-16).
     */
    @Test
    fun textTertiary_recedesButStaysVisible() = forEachTheme { colors ->
        for ((surfaceName, surface) in pageSurfaces(colors)) {
            ui("textTertiary on $surfaceName", colors.textTertiary, surface)
            val tertiary = contrast(colors.textTertiary, surface)
            val secondary = contrast(colors.textSecondary, surface)
            assertTrue(
                secondary - tertiary >= TERTIARY_STEP,
                "textTertiary on $surfaceName (${"%.2f".format(tertiary)}:1) is too close to textSecondary " +
                    "(${"%.2f".format(secondary)}:1)",
            )
        }
    }

    @Test
    fun text_onFillsAndContainers() = forEachTheme { colors ->
        text("onAccent on accentFill (primary button)", colors.onAccent, colors.accentFill)
        // White counts sit on the saturated unread fill; the pastel dark accent never carries white.
        text("onAccent on unread (badges)", colors.onAccent, colors.unread)
        text("onAccentSoft on accentSoft", colors.onAccentSoft, colors.accentSoft)
        text("text on secondaryContainer", colors.text, colors.secondaryContainer)
        text("onAccentSoft on segmentSelected (chosen segment)", colors.onAccentSoft, colors.segmentSelected)
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
            "celebration" to tiles.celebration,
        ).forEach { (name, tint) ->
            text("tile $name glyph", tint.content, tint.container)
            // Today's card kickers ("Mention · 5m", "Invitation") and chips use the tile ink as text on the card.
            text("tile $name ink on a card", tint.content, colors.surface)
        }
    }

    @Test
    fun brandWash_keepsTextReadable() = forEachTheme { colors ->
        for (stop in WaiveraBrand.washColors(colors)) {
            val wash = stop.compositeOver(colors.surface)
            text("text on wash $stop", colors.text, wash)
            text("textSecondary on wash $stop", colors.textSecondary, wash)
        }
    }

    /** Dark gold over plum composited to khaki (UI8-04): every wash point keeps blue at least level with green. */
    @Test
    fun darkBrandWash_staysInThePlumFamily() {
        val dark = waiveraColors(darkTheme = true)
        for (stop in WaiveraBrand.washColors(dark)) {
            val wash = stop.compositeOver(dark.surface)
            assertTrue(wash.blue >= wash.green, "dark wash point $wash leans olive")
            assertTrue(wash.red >= wash.green, "dark wash point $wash leans green")
        }
    }

    /**
     * Every tile container that sits on a brand surface in dark (the celebration emoji tile, the
     * amber bell on the primer and settings, the rose and brand tiles, the birthday badge) stays in
     * the plum family: the amber tile was an olive-brown #3A2C17 on the plum wash (UI9-06).
     */
    @Test
    fun darkTilesOnBrandSurfaces_stayInThePlumFamily() {
        val dark = waiveraColors(darkTheme = true)
        listOf(
            "celebration" to dark.tiles.celebration.container,
            "amber" to dark.tiles.amber.container,
            "rose" to dark.tiles.rose.container,
            "brand" to dark.tiles.brand.container,
            "neutral" to dark.tiles.neutral.container,
            "birthday badge" to dark.birthdayContainer,
        ).forEach { (name, container) ->
            assertTrue(container.blue >= container.green, "dark $name tile $container leans olive")
            assertTrue(container.red >= container.green, "dark $name tile $container leans green")
        }
    }

    /** The celebration tile keeps the light gold, which reads well on the light wash. */
    @Test
    fun lightCelebrationTile_isTheSoftGold() {
        val light = waiveraColors(darkTheme = false)
        assertTrue(light.tiles.celebration == light.tiles.amber, "the light celebration tile should stay the soft gold")
    }

    /**
     * A tonal button on the wash ("Send wishes") stands apart from every wash point by its fill
     * (accent-soft was about 1.05:1 there, UI9-08), and its label stays readable.
     */
    @Test
    fun tonalOnWash_standsApartFromTheWash() = forEachTheme { colors ->
        for (stop in WaiveraBrand.washColors(colors)) {
            check("tonalOnWash on wash $stop", colors.tonalOnWash, stop.compositeOver(colors.surface), AVATAR_FILL_MIN)
        }
        text("onAccentSoft on tonalOnWash", colors.onAccentSoft, colors.tonalOnWash)
    }

    /**
     * The selected pill (concept A): its ink at 4.5:1 at both ends, the pastel wash with violet ink
     * in light and the CTA gradient with white in dark; the tab's rose count with white; and the
     * snackbar's text and action on its plum.
     */
    @Test
    fun selectedPill_badgeAndSnackbar() = forEachTheme { colors ->
        text("onNavPill on navPillStart", colors.onNavPill, colors.navPillStart)
        text("onNavPill on navPillEnd", colors.onNavPill, colors.navPillEnd)
        text("onAccent on navBadge (tab count)", colors.onAccent, colors.navBadge)
        text("textSecondary on navBar (tab labels)", colors.textSecondary, colors.navBar)
        text("onSnackbar on snackbar", colors.onSnackbar, colors.snackbar)
        text("snackbarAction on snackbar", colors.snackbarAction, colors.snackbar)
    }

    /** The light tab pill ends on pink, never gold: gold on a 32dp pill read as a smear (UI8-01). */
    @Test
    fun selectedTab_hasNoGoldEnd() {
        val end = waiveraColors(darkTheme = false).navPillEnd
        assertTrue(end.blue >= end.green, "the tab pill should end on pink, not gold")
    }

    /**
     * Avatars carry no border or ring, so a fallback avatar's fill alone sets it apart from every
     * page surface and every point of the brand wash (UI8-05), and its initials stay readable.
     */
    @Test
    fun avatarFill_standsApartFromSurfacesAndTheWash() = forEachTheme { colors ->
        val backdrops = pageSurfaces(colors) +
            WaiveraBrand.washColors(colors).map { "wash $it" to it.compositeOver(colors.surface) }
        for ((name, backdrop) in backdrops) {
            check("avatar fill on $name", colors.avatarFill, backdrop, AVATAR_FILL_MIN)
        }
        text("initials on avatarFill", colors.onAccentSoft, colors.avatarFill)
    }

    @Test
    fun controls_meetTheNonTextMinimum() = forEachTheme { colors ->
        for ((surfaceName, surface) in pageSurfaces(colors)) {
            ui("borderStrong on $surfaceName", colors.borderStrong, surface)
        }
        ui("accentFill on background (primary button edge)", colors.accentFill, colors.background)
        ui("accent on accentSoft (selected outline)", colors.accent, colors.accentSoft)
    }

    /**
     * Concept A's field: the soft fill at rest and the raised step on focus keep body text and
     * the placeholder readable, and the tinted resting edge keeps 3:1 against the fill and every
     * sheet it sits on (the concept's hairline was about 1.3:1).
     */
    @Test
    fun textField_fillAndEdge() = forEachTheme { colors ->
        for ((name, fill) in listOf("field" to colors.field, "raised" to colors.raised)) {
            text("text on $name", colors.text, fill)
            text("placeholder on $name", colors.textSecondary, fill)
            text("dangerText on $name", colors.dangerText, fill)
        }
        for ((name, backdrop) in pageSurfaces(colors) + ("field" to colors.field)) {
            ui("fieldBorder on $name", colors.fieldBorder, backdrop)
        }
        ui("accent (focused edge) on raised", colors.accent, colors.raised)
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
        const val AVATAR_FILL_MIN = 1.3
        const val PLUM_MAX_BLUE_LEAD = 0.07f

        /** How much less contrast tertiary text has than secondary, so the two read as two steps. */
        const val TERTIARY_STEP = 2.0

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
