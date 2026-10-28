package com.waivera.waivera.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Body copy wraps at word boundaries with balanced lengths ([LineBreak.Paragraph]). Titles,
 * headlines and labels, where names and channel names show, use the greedy simple breaker
 * ([TitleLineBreak]): the balancing breaker would rather split a long hyphenated name mid-word
 * ("coordination-a / nd-scheduling") than leave a short line (UI4-02). Nothing is hyphenated:
 * product copy split as "pass-word" or "notifica-tions" reads like a layout bug (UI3-02). Names
 * also get break points after their own hyphens (`withBreaksAfterHyphens`) where they show.
 */
private val WrapLineBreak = LineBreak.Paragraph
internal val TitleLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Simple,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Default,
)
internal val WaiveraHyphens = Hyphens.None

/** Every style is set in [WaiveraFonts.PlusJakartaSans], the concept's typeface. */
private val Jakarta = WaiveraFonts.PlusJakartaSans

/**
 * Type scale in sp only, so every style follows the system font scale up to 200%. Sizes and
 * weights come from the approved concept (`concepts/src/A.html`, with B's section titles); the
 * variable font tops out at ExtraBold (800), so nothing asks for Black.
 */
internal val WaiveraMaterialTypography = Typography(
    // "Welcome back" on sign-in: 30 / 800, tracked in.
    headlineLarge = TextStyle(
        fontFamily = Jakarta,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.025).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    headlineMedium = TextStyle(
        fontFamily = Jakarta,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.025).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Screen titles (gate screens, sheets).
    headlineSmall = TextStyle(
        fontFamily = Jakarta,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.02).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    titleLarge = TextStyle(
        fontFamily = Jakarta,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.015).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Section titles in B's scale: mixed case, 17 / 600 ("Needs you", "Your day").
    titleMedium = TextStyle(
        fontFamily = Jakarta,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.012).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Card and row titles: 15 / 700.
    titleSmall = TextStyle(
        fontFamily = Jakarta,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.005).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Field input and long copy.
    bodyLarge = TextStyle(
        fontFamily = Jakarta,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    bodyMedium = TextStyle(
        fontFamily = Jakarta,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Card metadata: 13 / 400.
    bodySmall = TextStyle(
        fontFamily = Jakarta,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Button labels: 16 / 700, the concept's CTA.
    labelLarge = TextStyle(
        fontFamily = Jakarta,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.005.em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    // Field labels and kicker lines: 13 / 600.
    labelMedium = TextStyle(
        fontFamily = Jakarta,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
    labelSmall = TextStyle(
        fontFamily = Jakarta,
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    ),
)

/**
 * Styles with no Material 3 slot: the hero's numbers and headings, kickers, chips and badges.
 * Hero styles carry no colour; the hero components set the on-hero ink.
 */
object WaiveraTextStyles {
    /** Small caps-style section labels above lists. */
    val Eyebrow = TextStyle(
        fontFamily = Jakarta,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.1.em,
        hyphens = WaiveraHyphens,
    )

    /** Count and status badges. */
    val Badge = TextStyle(
        fontFamily = Jakarta,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        hyphens = WaiveraHyphens,
    )

    /** The hero's date line ("WEDNESDAY, OCTOBER 28"), capitalised in code: 12 / 700, tracked out. */
    val HeroEyebrow = TextStyle(
        fontFamily = Jakarta,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.09.em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The hero greeting ("Good morning, Alex"): 30 / 800. */
    val HeroGreeting = TextStyle(
        fontFamily = Jakarta,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.028).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The greeting on a short window, so the sheet's first rows show above the fold: 24 / 800. */
    val HeroGreetingCompact = HeroGreeting.copy(fontSize = 24.sp, lineHeight = 28.sp)

    /** The hero bar's screen title next to the mark ("Today"): 17 / 800. */
    val HeroBarTitle = TextStyle(
        fontFamily = Jakarta,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.01).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The summary card's headline ("4 things need you"): 18 / 800. */
    val HeroTitle = TextStyle(
        fontFamily = Jakarta,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.015).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The all-caught-up headline under the big ring: 26 / 800. */
    val HeroReward = TextStyle(
        fontFamily = Jakarta,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.025).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** Supporting lines on the hero ("5 handled since this morning."): 13 / 400. */
    val HeroBody = TextStyle(
        fontFamily = Jakarta,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The ring's count ("5"): 26 / 800. */
    val RingNumeral = TextStyle(
        fontFamily = Jakarta,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.03).em,
        hyphens = WaiveraHyphens,
    )

    /** The ring's total ("/9"), set smaller beside the count: 15 / 700. */
    val RingDenominator = TextStyle(
        fontFamily = Jakarta,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        hyphens = WaiveraHyphens,
    )

    /** The word under the ring's count ("handled"): 11 / 600. */
    val RingCaption = TextStyle(
        fontFamily = Jakarta,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        hyphens = WaiveraHyphens,
    )

    /**
     * The caption in the compact ring on a short window: 10 / 600 with no tracking, so "handled"
     * clears the arc inside a 72dp ring (UI10-06).
     */
    val RingCaptionCompact = TextStyle(
        fontFamily = Jakarta,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        hyphens = WaiveraHyphens,
    )

    /** A stat tile's number ("14"): 20 / 800. */
    val StatNumeral = TextStyle(
        fontFamily = Jakarta,
        fontSize = 20.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.02).em,
        hyphens = WaiveraHyphens,
    )

    /** A stat tile's label ("events this week"): 12 / 500. */
    val StatLabel = TextStyle(
        fontFamily = Jakarta,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** A card's kicker above its title ("Not sent", "Mention · 9:02 AM"): 12 / 700. */
    val Kicker = TextStyle(
        fontFamily = Jakarta,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.01.em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** A section's trailing link ("Show all", "See schedule"): 14 / 700. */
    val SectionAction = TextStyle(
        fontFamily = Jakarta,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The count badge beside a section title ("Needs you 4"): 13 / 800. */
    val SectionCount = TextStyle(
        fontFamily = Jakarta,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        hyphens = WaiveraHyphens,
    )

    /** The big clock time on the Up next card ("11:00"): 26 / 800. */
    val DisplayTime = TextStyle(
        fontFamily = Jakarta,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.03).em,
        hyphens = WaiveraHyphens,
    )

    /** The timeline row's clock ("11:00"): 16 / 600. */
    val ClockTime = TextStyle(
        fontFamily = Jakarta,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
        hyphens = WaiveraHyphens,
    )

    /** A timeline entry's start time beside its card ("11:00 AM"): 13 / 700. */
    val TimelineTime = TextStyle(
        fontFamily = Jakarta,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.005).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** Small chips and the timeline's hour labels: 12 / 700 for chips. */
    val Chip = TextStyle(
        fontFamily = Jakarta,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** A panel's own title inside a card ("Your day", concept B): 14 / 600. */
    val PanelTitle = TextStyle(
        fontFamily = Jakarta,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** A detail field's label on Profile and Person ("Email", "Time zone"): 13 / 500, muted (v5 decision 7). */
    val FieldLabel = TextStyle(
        fontFamily = Jakarta,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        lineBreak = TitleLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** A detail field's value under its label: 17 / 700, the row's weight (v5 decision 7). */
    val FieldValue = TextStyle(
        fontFamily = Jakarta,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.005).em,
        lineBreak = WrapLineBreak,
        hyphens = WaiveraHyphens,
    )

    /** The timeline's hour labels ("8 AM", "10"): 11 / 500. */
    val AxisLabel = TextStyle(
        fontFamily = Jakarta,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        hyphens = WaiveraHyphens,
    )
}
