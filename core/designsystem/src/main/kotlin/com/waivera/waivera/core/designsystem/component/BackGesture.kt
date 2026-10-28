package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * Where a predictive back swipe is, as [BackPreviewFrame] draws it. The shell reads it from the
 * navigation event state it hands to Navigation 3, so the preview follows the same input that
 * seeks the screen transition; a test can hold it at any progress.
 */
@Immutable
sealed interface BackGesture {
    /** No swipe has happened yet. */
    data object Idle : BackGesture

    /**
     * The finger is down. [progress] runs from 0 when the swipe starts to 1 at the system's
     * full travel; [swipeFromRight] says which edge it started from.
     */
    data class Dragging(val progress: Float, val swipeFromRight: Boolean) : BackGesture

    /** The finger lifted and the screen is going back. */
    data object Committed : BackGesture

    /** The swipe was abandoned and the screen stays. */
    data object Cancelled : BackGesture
}
