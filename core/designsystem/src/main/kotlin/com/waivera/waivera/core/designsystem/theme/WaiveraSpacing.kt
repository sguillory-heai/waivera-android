package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale. Layouts use these instead of literal dp values. */
object WaiveraSpacing {
    val None: Dp = 0.dp
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

    /** A quiet inline tile, such as a section's "didn't load" line, and the glyph inside it. */
    val IconTileSmall: Dp = 32.dp
    val IconTileSmallGlyph: Dp = 20.dp

    /** An emoji inside an icon tile: icon size, fixed so the tile never overflows at 200%. */
    val EmojiInTile: Dp = 22.dp

    /** The icon tile of an empty or error state, and its glyph. */
    val StateIconTile: Dp = 56.dp
    val StateIconGlyph: Dp = 28.dp
    val AvatarSmall: Dp = 32.dp
    val AvatarMedium: Dp = 44.dp
    val AvatarLarge: Dp = 64.dp
    val AvatarHero: Dp = 96.dp

    /** The brand mark next to a heading. */
    val BrandMark: Dp = 48.dp

    /** The logo with its wordmark, in a header or a compact hero. */
    val BrandLockup: Dp = 32.dp

    /** The logo with its wordmark on the sign-in hero band. */
    val BrandLockupHero: Dp = 44.dp

    /** Height of the gradient-washed hero band behind the sign-in logo. */
    val HeroBand: Dp = 132.dp

    /** The hero band on a short window or at large text. */
    val HeroBandCompact: Dp = 80.dp

    /** Empty-state illustrations. */
    val Illustration: Dp = 120.dp

    /** The all-caught-up reward art: a little larger than an empty state's. */
    val IllustrationReward: Dp = 144.dp

    /** Size of one confetti piece in the celebrate burst. */
    val ConfettiPiece: Dp = 7.dp
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

    /** The unread count on a bottom tab (concept A, 18): smaller than a row badge. */
    val NavBadgeMin: Dp = 18.dp

    /** The ring in the bar's own colour around the tab's unread count. */
    val NavBadgeRing: Dp = 2.dp

    /**
     * How far the tab's unread count overlaps the icon's top-end corner, on each axis. The
     * badge grows outward from there.
     */
    val NavBadgeOverlap: Dp = 8.dp

    /**
     * The selected tab's one pill, drawn by the nav bar itself (Material's indicator is hidden).
     * 56dp is Material's own indicator and press-ripple width in this BOM, so a press lines up.
     */
    val NavIndicatorWidth: Dp = 56.dp
    val NavIndicatorHeight: Dp = 32.dp
    val ProgressSmall: Dp = 20.dp
    val ProgressStroke: Dp = 2.dp

    /** Busy indicator for a whole block or screen (the Material default size and stroke). */
    val ProgressLarge: Dp = 40.dp
    val ProgressStrokeLarge: Dp = 4.dp
    val TopBarMinHeight: Dp = 64.dp
    val ListRowMinHeight: Dp = 56.dp
    val ButtonMinHeight: Dp = 48.dp

    /** The primary gradient CTA and a text field (concept A). */
    val CtaHeight: Dp = 56.dp
    val FieldHeight: Dp = 56.dp

    /** The focus halo around a text field, outside its border. */
    val FieldHalo: Dp = 4.dp
    val FieldBorder: Dp = 1.5.dp

    /** The hero's progress ring and its stroke. */
    val Ring: Dp = 96.dp
    val RingStroke: Dp = 10.dp

    /** The all-caught-up ring, its stroke, its halo's reach beyond it and its check. */
    val RewardRing: Dp = 132.dp
    val RewardRingStroke: Dp = 12.dp
    val RewardHalo: Dp = 26.dp
    val RewardCheck: Dp = 52.dp

    /** A content card's leading tile and the glyph in it. */
    val CardTile: Dp = 44.dp
    val CardTileGlyph: Dp = 22.dp

    /** A section's gradient count badge. */
    val CountBadgeHeight: Dp = 24.dp

    /** The "Your day" track, the inset of its blocks and its now-line. */
    val TimelineTrack: Dp = 30.dp
    val TimelineBlockInset: Dp = 5.dp
    val TimelineNowLine: Dp = 2.dp
    val TimelineNowDot: Dp = 8.dp
    val TimelineTick: Dp = 1.dp

    /**
     * A day read as a timeline in a list (the Schedule's Day view): the time column, the width of
     * the rail's gradient block, and the block's height beside a card and on its own line.
     */
    val TimelineTimeColumn: Dp = 68.dp
    val TimelineRailBlock: Dp = 6.dp
    val TimelineRailBlockTall: Dp = 28.dp
    val TimelineRailBlockShort: Dp = 16.dp

    /** The mention quote's rule on a Needs you card. */
    val QuoteRule: Dp = 2.dp
    val SegmentMinHeight: Dp = 48.dp

    /** A filter chip as drawn (its target stays 48dp) and the glyph in it. */
    val FilterChipHeight: Dp = 36.dp
    val FilterChipIcon: Dp = 18.dp
    val SkeletonLine: Dp = 14.dp
    val SheetMaxWidth: Dp = 640.dp

    /** Height of the fade that shows more content scrolls below a viewport edge. */
    val FadeEdge: Dp = 24.dp

    /** Windows shorter than this (a small phone, landscape) drop optional copy so actions stay close. */
    val CompactWindowHeight: Dp = 700.dp

    /** Corner radius of the screen being dismissed in the predictive back preview (M3 back preview). */
    val BackPreviewCorner: Dp = 24.dp
}
