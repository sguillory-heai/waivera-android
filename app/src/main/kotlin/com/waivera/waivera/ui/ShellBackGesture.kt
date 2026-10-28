package com.waivera.waivera.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventState
import com.waivera.waivera.core.designsystem.component.BackGesture

/**
 * The shell's predictive back swipe as [com.waivera.waivera.core.designsystem.component.BackPreviewFrame]
 * draws it. Progress comes from the same [NavigationEventState] the shell hands to NavDisplay,
 * so the preview and the seeked screen transition follow one input. How the swipe ended comes
 * from the back handler's callbacks, which fire before the state goes idle; it stays until the
 * next swipe, and frames that took no part in a swipe ignore it.
 */
@Stable
internal class ShellBackGesture {
    private var events by mutableStateOf<NavigationEventState<*>?>(null)
    private var released by mutableStateOf<BackGesture>(BackGesture.Idle)

    /** Called from a side effect once NavDisplay's navigation event state exists. */
    fun attach(state: NavigationEventState<*>) {
        events = state
    }

    fun completed() {
        released = BackGesture.Committed
    }

    fun cancelled() {
        released = BackGesture.Cancelled
    }

    /** The swipe now; read it in effects or at draw time, not in composition. */
    fun current(): BackGesture = when (val transition = events?.transitionState) {
        is NavigationEventTransitionState.InProgress -> BackGesture.Dragging(
            progress = transition.latestEvent.progress,
            swipeFromRight = transition.latestEvent.swipeEdge == NavigationEvent.EDGE_RIGHT,
        )
        else -> released
    }
}
