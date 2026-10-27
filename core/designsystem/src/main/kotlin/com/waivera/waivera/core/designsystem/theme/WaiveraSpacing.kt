package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale. Layouts use these instead of literal dp values. */
object WaiveraSpacing {
    val Hairline: Dp = 1.dp
    val XxSmall: Dp = 2.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val MediumSmall: Dp = 12.dp
    val Medium: Dp = 16.dp
    val MediumLarge: Dp = 20.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp
    val XxLarge: Dp = 48.dp

    /** Horizontal screen gutter. */
    val Gutter: Dp = 16.dp

    /** Minimum touch target on Android. Every control is at least this big in both axes. */
    val MinTouchTarget: Dp = 48.dp
}

/** Fixed component sizes. Text inside them still scales; containers grow where text can wrap. */
object WaiveraSizes {
    val IconSmall: Dp = 16.dp
    val Icon: Dp = 24.dp
    val IconButton: Dp = 48.dp
    val IconTile: Dp = 40.dp
    val StateIconTile: Dp = 48.dp
    val AvatarSmall: Dp = 32.dp
    val AvatarMedium: Dp = 44.dp
    val AvatarLarge: Dp = 64.dp
    val AvatarHero: Dp = 96.dp

    /** The brand mark above the sign-in title. */
    val BrandMark: Dp = 48.dp
    val AvatarRing: Dp = 2.dp
    val PresenceDot: Dp = 12.dp

    /** Ring width of the "away" presence dot; "offline" uses the thinner hairline ring. */
    val PresenceRing: Dp = 2.5.dp
    val PresenceHairline: Dp = 1.5.dp

    /** Outline of a selected or "mine" pill (reactions): heavier than the hairline so it reads at a glance. */
    val SelectedOutline: Dp = 1.5.dp

    /** Milestone corner badge (07 §4): at least this big, scaled with the avatar above it. */
    val MilestoneBadgeMin: Dp = 17.dp
    val MilestoneBadgeBorder: Dp = 2.dp

    /** Anniversary badges show the year count only on avatars at least this big. */
    val MilestoneYearsMinAvatar: Dp = 40.dp
    val BadgeMin: Dp = 20.dp
    val ProgressSmall: Dp = 20.dp
    val ProgressStroke: Dp = 2.dp
    val TopBarMinHeight: Dp = 64.dp
    val ListRowMinHeight: Dp = 56.dp
    val ButtonMinHeight: Dp = 48.dp
    val SegmentMinHeight: Dp = 48.dp
    val SkeletonLine: Dp = 14.dp
    val SheetMaxWidth: Dp = 640.dp

    /** Height of the fade that shows more content scrolls below a viewport edge. */
    val FadeEdge: Dp = 24.dp

    /** Windows shorter than this (a small phone, landscape) drop optional copy so actions stay close. */
    val CompactWindowHeight: Dp = 700.dp
}
