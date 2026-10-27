package com.waivera.waivera.feature.chat.message

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.feature.chat.R

/**
 * Chat-only sizes from the reference screens (03 §6, §8, §13). Kept in one place so composables
 * never carry literal dp values; anything shared with other features belongs in the design system.
 */
internal object ChatDimens {
    val BubbleRadius: Dp = 15.dp
    val BubbleGroupedRadius: Dp = 6.dp
    val BubbleMinWidth: Dp = 54.dp
    const val BUBBLE_MAX_WIDTH_FRACTION = 0.82f
    val AvatarTopOffset: Dp = 18.dp
    val ImageWidth: Dp = 250.dp
    val ImageHeight: Dp = 190.dp
    val ImageLoadingHeight: Dp = 150.dp
    val ImageRadius: Dp = 13.dp
    val ImageBadge: Dp = 28.dp
    val FileCardWidth: Dp = 250.dp
    val ReactionPillHeight: Dp = 30.dp
    val TypingSlot: Dp = 22.dp
    val ComposerMinHeight: Dp = 48.dp
    val ComposerMaxHeight: Dp = 120.dp
    val QuoteBar: Dp = 3.dp
    val ListMarkerGutter: Dp = 24.dp
    val TableCellWidth: Dp = 150.dp
    val TableCellMinHeight: Dp = 38.dp
    val TableMinWidth: Dp = 260.dp
    val StagedThumbnail: Dp = 50.dp
    val ReadOnlyTile: Dp = 34.dp
    val LiveDot: Dp = 7.dp
    val CallBannerMinHeight: Dp = 48.dp
    val SkeletonBubbleHeight: Dp = 54.dp
    val JumpPillBottomGap: Dp = 12.dp

    /** Timeline bottom inset while the pill shows: its gap, its 48dp height and a small margin. */
    val JumpPillInset: Dp = JumpPillBottomGap + WaiveraSpacing.MinTouchTarget + WaiveraSpacing.Small
    val ArrivalOffset: Dp = 18.dp
    val PreviewMaxHeight: Dp = 220.dp
    val EmojiTarget: Dp = 48.dp
    val ReactionTabMinWidth: Dp = 62.dp

    /** Farther than this from the newest message shows "Jump to latest" (03 §15). */
    val JumpToLatestThreshold: Dp = 180.dp

    /** Within this of the newest message, new rows keep the list pinned to the bottom. */
    val StickToBottomThreshold: Dp = 48.dp

    /** Rows from the oldest loaded one at which the next older page is requested. */
    const val OLDER_PREFETCH_ROWS = 5

    /** How often relative times ("2m ago") refresh while the list is on screen (03 §4). */
    const val TIME_TICK_MILLIS = 30_000L

    const val MAX_ZOOM = 5f
    const val DOUBLE_TAP_ZOOM = 2.5f
    const val SKELETON_FRACTIONS_COUNT = 5
    val SkeletonWidths: List<Float> = listOf(0.66f, 0.51f, 0.78f, 0.62f, 0.47f)

    /** Bubble corners tighten on the author's side when a neighbour is grouped with it. */
    fun bubbleShape(isMine: Boolean, groupedWithPrevious: Boolean, groupedWithNext: Boolean): Shape {
        val top = if (groupedWithPrevious) BubbleGroupedRadius else BubbleRadius
        val bottom = if (groupedWithNext) BubbleGroupedRadius else BubbleRadius
        return if (isMine) {
            RoundedCornerShape(topStart = BubbleRadius, topEnd = top, bottomEnd = bottom, bottomStart = BubbleRadius)
        } else {
            RoundedCornerShape(topStart = top, topEnd = BubbleRadius, bottomEnd = BubbleRadius, bottomStart = bottom)
        }
    }

    val SkeletonOtherShape: Shape = RoundedCornerShape(
        topStart = BubbleRadius,
        topEnd = BubbleRadius,
        bottomEnd = BubbleRadius,
        bottomStart = BubbleGroupedRadius,
    )
    val SkeletonMineShape: Shape = RoundedCornerShape(
        topStart = BubbleRadius,
        topEnd = BubbleRadius,
        bottomEnd = BubbleGroupedRadius,
        bottomStart = BubbleRadius,
    )
    val ImageShape: Shape = RoundedCornerShape(ImageRadius)
}

/** Chat icons not in the shared set; same 24dp vector style as `WaiveraIcons`. */
internal object ChatIcons {
    @DrawableRes val Send: Int = R.drawable.feature_chat_ic_send

    @DrawableRes val AttachImage: Int = R.drawable.feature_chat_ic_image_add

    @DrawableRes val Download: Int = R.drawable.feature_chat_ic_download

    @DrawableRes val File: Int = R.drawable.feature_chat_ic_file

    @DrawableRes val FileText: Int = R.drawable.feature_chat_ic_file_text

    @DrawableRes val FileSheet: Int = R.drawable.feature_chat_ic_file_sheet

    @DrawableRes val ChevronDown: Int = R.drawable.feature_chat_ic_chevron_down

    @DrawableRes val Copy: Int = R.drawable.feature_chat_ic_copy

    @DrawableRes val Delete: Int = R.drawable.feature_chat_ic_delete

    @DrawableRes val Edit: Int = R.drawable.feature_chat_ic_edit

    @DrawableRes val PhoneMissed: Int = R.drawable.feature_chat_ic_phone_missed

    @DrawableRes val Maximize: Int = R.drawable.feature_chat_ic_maximize

    @DrawableRes val Image: Int = R.drawable.feature_chat_ic_image

    @DrawableRes val BookmarkFilled: Int = R.drawable.feature_chat_ic_bookmark_filled
}

/** Light content on the dark scrim (image badge, image preview) in both themes. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun onScrimColor(): androidx.compose.ui.graphics.Color {
    val colors = com.waivera.waivera.core.designsystem.theme.WaiveraTheme.colors
    return if (colors.isDark) colors.text else colors.background
}
