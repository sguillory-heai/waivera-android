package com.waivera.waivera.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Lines wrap at word boundaries with balanced lengths ([LineBreak.Paragraph]) and are never
 * hyphenated: product copy split as "pass-word" or "notifica-tions" reads like a layout bug
 * (UI3-02). Long single tokens (emails, names) are capped with maxLines and an ellipsis where
 * they appear, instead of being broken.
 */
private val WrapLineBreak = LineBreak.Paragraph
internal val WaiveraHyphens = Hyphens.None

/**
 * Type scale in sp only, so every style follows the system font scale up to 200%.
 * Only real platform weights are used (a non-standard weight crashed the Expo app on Android).
 */
internal val WaiveraMaterialTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        hyphens = WaiveraHyphens,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        hyphens = WaiveraHyphens,
    ),
    // Screen titles ("Title" in the Expo app: 21sp, heaviest weight).
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Black,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Section titles.
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Button labels.
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        hyphens = WaiveraHyphens,
    ),
    // Subtitles and field labels.
    labelMedium = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        hyphens = WaiveraHyphens,
    ),
    labelSmall = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        hyphens = WaiveraHyphens,
    ),
)

/** Styles with no Material 3 slot. */
object WaiveraTextStyles {
    /** Small caps-style section labels above lists. */
    val Eyebrow = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.1.em,
        hyphens = WaiveraHyphens,
    )

    /** Count and status badges. */
    val Badge = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, hyphens = WaiveraHyphens)
}
