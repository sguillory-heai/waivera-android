package com.waivera.waivera.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Corner radii taken from the recurring values in the Expo app (spec 10 §2). */
object WaiveraShapes {
    val Chip: Shape = RoundedCornerShape(8.dp)

    /** The soft rounded square behind every leading icon (`WaiveraIconTile`). */
    val Tile: Shape = RoundedCornerShape(14.dp)
    val Button: Shape = RoundedCornerShape(12.dp)
    val Card: Shape = RoundedCornerShape(12.dp)

    /** Text fields and the primary gradient CTA (concept A). */
    val Field: Shape = RoundedCornerShape(16.dp)
    val Cta: Shape = RoundedCornerShape(16.dp)

    /** A content card on a tab screen's sheet (Needs you, Up next, Celebrations, Activity). */
    val SheetCard: Shape = RoundedCornerShape(20.dp)

    /** [SheetCard]'s radius, for a card split across list items (its top and bottom pieces). */
    val SheetCardRadius: Dp = 20.dp

    /** Today's large Up next card (concept A, 22). */
    val UpNextCard: Shape = RoundedCornerShape(22.dp)

    /** A stat tile on the hero. */
    val StatTile: Shape = RoundedCornerShape(16.dp)

    /** The glass summary card on the hero. */
    val HeroCard: Shape = RoundedCornerShape(24.dp)

    /** The "Your day" timeline: its panel, its track and one event block on it. */
    val TimelinePanel: Shape = RoundedCornerShape(18.dp)
    val TimelineTrack: Shape = RoundedCornerShape(10.dp)
    val TimelineBlock: Shape = RoundedCornerShape(6.dp)

    /** A filter chip, and a segmented control's track and its chosen segment. */
    val FilterChip: Shape = RoundedCornerShape(12.dp)
    val SegmentTrack: Shape = RoundedCornerShape(16.dp)
    val Segment: Shape = RoundedCornerShape(12.dp)

    /** A modal sheet's top corners: the tab sheet's 28, so a sheet rises the way the screens do. */
    val ModalSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** A snackbar: the field and CTA radius. */
    val Snackbar: Shape = RoundedCornerShape(16.dp)

    /** A section's gradient count ("Needs you 4"). */
    val CountBadge: Shape = RoundedCornerShape(8.dp)
    val ListCard: Shape = RoundedCornerShape(14.dp)
    val Banner: Shape = RoundedCornerShape(16.dp)
    val Composer: Shape = RoundedCornerShape(18.dp)
    val Pill: Shape = RoundedCornerShape(18.dp)
    val Bubble: Shape = RoundedCornerShape(20.dp)

    /** Brand moments: the sign-in hero band and celebration cards. */
    val Hero: Shape = RoundedCornerShape(24.dp)
    val Sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Circle: Shape = CircleShape

    /**
     * The one avatar shape: a soft rounded square, never a circle, at every size and call site
     * (the owner's rule). Corners are `max(8, round(size * 0.24))`, matching the Expo avatar.
     */
    fun avatar(size: Dp): Shape = RoundedCornerShape(avatarCornerRadius(size))

    /** The corner radius of [avatar] at [size], for anything placed on the avatar's curve. */
    fun avatarCornerRadius(size: Dp): Dp = maxOf(MIN_AVATAR_RADIUS, (size.value * AVATAR_RADIUS_RATIO).roundToInt()).dp

    private const val MIN_AVATAR_RADIUS = 8
    private const val AVATAR_RADIUS_RATIO = 0.24f
}

internal val WaiveraMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
