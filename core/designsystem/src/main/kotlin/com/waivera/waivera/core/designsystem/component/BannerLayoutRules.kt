package com.waivera.waivera.core.designsystem.component

import com.waivera.waivera.core.designsystem.theme.LARGE_FONT_SCALE

/**
 * True when a banner's text action should go under the text instead of beside it: above
 * [BANNER_STACK_FONT_SCALE], when the action would take more than
 * [BANNER_INLINE_ACTION_MAX_FRACTION] of the banner width, or when the message would need more
 * than [BANNER_INLINE_MESSAGE_MAX_LINES] lines in the width left beside the action (UI4-06).
 */
internal fun bannerActionStacks(
    fontScale: Float,
    actionWidthPx: Int,
    availableWidthPx: Int,
    messageLinesBeside: Int = 1,
): Boolean = fontScale > BANNER_STACK_FONT_SCALE ||
    actionWidthPx > availableWidthPx * BANNER_INLINE_ACTION_MAX_FRACTION ||
    messageLinesBeside > BANNER_INLINE_MESSAGE_MAX_LINES

internal const val BANNER_STACK_FONT_SCALE = LARGE_FONT_SCALE
internal const val BANNER_INLINE_ACTION_MAX_FRACTION = 0.35f
internal const val BANNER_INLINE_MESSAGE_MAX_LINES = 2
