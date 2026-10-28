package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** UI copy is never hyphenated ("pass-word", "notifica-tions"), at any font scale (UI3-02). */
class WaiveraTypographyTest {
    private val typography = WaiveraMaterialTypography

    private val materialStyles: Map<String, TextStyle> = mapOf(
        "displayLarge" to typography.displayLarge,
        "displayMedium" to typography.displayMedium,
        "displaySmall" to typography.displaySmall,
        "headlineLarge" to typography.headlineLarge,
        "headlineMedium" to typography.headlineMedium,
        "headlineSmall" to typography.headlineSmall,
        "titleLarge" to typography.titleLarge,
        "titleMedium" to typography.titleMedium,
        "titleSmall" to typography.titleSmall,
        "bodyLarge" to typography.bodyLarge,
        "bodyMedium" to typography.bodyMedium,
        "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge,
        "labelMedium" to typography.labelMedium,
        "labelSmall" to typography.labelSmall,
    )

    private val waiveraStyles: Map<String, TextStyle> = mapOf(
        "Eyebrow" to WaiveraTextStyles.Eyebrow,
        "Badge" to WaiveraTextStyles.Badge,
        "HeroEyebrow" to WaiveraTextStyles.HeroEyebrow,
        "HeroGreeting" to WaiveraTextStyles.HeroGreeting,
        "HeroBarTitle" to WaiveraTextStyles.HeroBarTitle,
        "HeroTitle" to WaiveraTextStyles.HeroTitle,
        "HeroReward" to WaiveraTextStyles.HeroReward,
        "HeroBody" to WaiveraTextStyles.HeroBody,
        "RingNumeral" to WaiveraTextStyles.RingNumeral,
        "RingDenominator" to WaiveraTextStyles.RingDenominator,
        "RingCaption" to WaiveraTextStyles.RingCaption,
        "StatNumeral" to WaiveraTextStyles.StatNumeral,
        "StatLabel" to WaiveraTextStyles.StatLabel,
        "Kicker" to WaiveraTextStyles.Kicker,
        "SectionAction" to WaiveraTextStyles.SectionAction,
        "SectionCount" to WaiveraTextStyles.SectionCount,
        "DisplayTime" to WaiveraTextStyles.DisplayTime,
        "ClockTime" to WaiveraTextStyles.ClockTime,
        "Chip" to WaiveraTextStyles.Chip,
        "PanelTitle" to WaiveraTextStyles.PanelTitle,
        "AxisLabel" to WaiveraTextStyles.AxisLabel,
    )

    @Test
    fun noStyleHyphenates() {
        (materialStyles + waiveraStyles).forEach { (name, style) ->
            assertNotEquals(Hyphens.Auto, style.hyphens, "$name hyphenates")
        }
    }

    @Test
    fun everyStyleTheAppDefinesTurnsHyphenationOffExplicitly() {
        val defined = materialStyles.filterKeys { !it.startsWith("display") } + waiveraStyles
        defined.forEach { (name, style) -> assertEquals(Hyphens.None, style.hyphens, name) }
    }

    /** Every style is set in the concept's typeface, so no screen falls back to Roboto by accident. */
    @Test
    fun everyStyleTheAppDefinesUsesPlusJakartaSans() {
        val defined = materialStyles.filterKeys { !it.startsWith("display") } + waiveraStyles
        defined.forEach { (name, style) -> assertEquals(WaiveraFonts.PlusJakartaSans, style.fontFamily, name) }
    }

    /** The variable font stops at ExtraBold; a heavier weight would be synthesised and smear. */
    @Test
    fun noStyleAsksForMoreThanTheFontHas() {
        (materialStyles + waiveraStyles).forEach { (name, style) ->
            val weight = style.fontWeight?.weight ?: return@forEach
            assertTrue(weight <= FontWeight.ExtraBold.weight, "$name asks for weight $weight")
        }
    }

    @Test
    fun theSharedSettingIsNone() {
        assertEquals(Hyphens.None, WaiveraHyphens)
    }
}
