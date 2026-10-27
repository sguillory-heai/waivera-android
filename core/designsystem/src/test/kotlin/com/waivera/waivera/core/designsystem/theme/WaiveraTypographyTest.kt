package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

    @Test
    fun theSharedSettingIsNone() {
        assertEquals(Hyphens.None, WaiveraHyphens)
    }
}
