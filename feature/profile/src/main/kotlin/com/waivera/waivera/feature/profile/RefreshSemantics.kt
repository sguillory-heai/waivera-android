package com.waivera.waivera.feature.profile

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/**
 * Refresh is pull-to-refresh, not a header icon (UI2-07). Pull gestures are hard to make with
 * TalkBack, so the same refresh is offered as an accessibility action on the pulled content.
 */
internal fun Modifier.refreshAction(label: String, onRefresh: () -> Unit): Modifier = semantics {
    customActions = listOf(
        CustomAccessibilityAction(label) {
            onRefresh()
            true
        },
    )
}
