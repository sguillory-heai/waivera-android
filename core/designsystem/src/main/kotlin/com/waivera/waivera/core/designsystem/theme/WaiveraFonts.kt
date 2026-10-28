package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.waivera.waivera.core.designsystem.R

/**
 * Plus Jakarta Sans, the typeface of the approved concept (concept A "Confident"), bundled as
 * one variable font (weights 200 to 800) under the SIL Open Font License; the licence ships in
 * `assets/licenses`. Each weight the type scale uses is one instance of the variable font, so
 * the app carries a single 176 KB file. Glyphs the font lacks (the no-break hyphen U+2011, emoji)
 * fall back to the system font.
 */
@OptIn(ExperimentalTextApi::class)
object WaiveraFonts {
    private fun instance(weight: FontWeight) = Font(
        resId = R.font.core_designsystem_plus_jakarta_sans,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    val PlusJakartaSans: FontFamily = FontFamily(
        instance(FontWeight.Normal),
        instance(FontWeight.Medium),
        instance(FontWeight.SemiBold),
        instance(FontWeight.Bold),
        instance(FontWeight.ExtraBold),
    )
}
