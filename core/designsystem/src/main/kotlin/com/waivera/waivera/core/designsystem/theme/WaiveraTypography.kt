package com.waivera.waivera.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Long words (names like "Castellanos-Whitaker", emails) wrap at word or hyphen boundaries and
 * then hyphenate, instead of breaking at an arbitrary letter when the font scale is large.
 */
private val WrapLineBreak = LineBreak.Paragraph
private val WrapHyphens = Hyphens.Auto

/**
 * Type scale in sp only, so every style follows the system font scale up to 200%.
 * Only real platform weights are used (a non-standard weight crashed the Expo app on Android).
 */
internal val WaiveraMaterialTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    // Screen titles ("Title" in the Expo app: 21sp, heaviest weight).
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Black,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    // Section titles.
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WrapHyphens,
    ),
    // Button labels.
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
    // Subtitles and field labels.
    labelMedium = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
)

/** Styles with no Material 3 slot. */
object WaiveraTextStyles {
    /** Small caps-style section labels above lists. */
    val Eyebrow = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.1.em,
    )

    /** Count and status badges. */
    val Badge = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
}
