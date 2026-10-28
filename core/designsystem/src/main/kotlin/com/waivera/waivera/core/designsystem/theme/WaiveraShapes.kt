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
    val Field: Shape = RoundedCornerShape(12.dp)
    val ListCard: Shape = RoundedCornerShape(14.dp)
    val Banner: Shape = RoundedCornerShape(14.dp)
    val Composer: Shape = RoundedCornerShape(18.dp)
    val Pill: Shape = RoundedCornerShape(18.dp)
    val Bubble: Shape = RoundedCornerShape(20.dp)

    /** Brand moments: the sign-in hero band and celebration cards. */
    val Hero: Shape = RoundedCornerShape(24.dp)
    val Sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Circle: Shape = CircleShape

    /** Square avatars use `max(8, round(size * 0.24))`, matching the Expo avatar. */
    fun avatar(size: Dp, rounded: Boolean): Shape = if (rounded) {
        CircleShape
    } else {
        RoundedCornerShape(maxOf(MIN_AVATAR_RADIUS, (size.value * AVATAR_RADIUS_RATIO).roundToInt()).dp)
    }

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
