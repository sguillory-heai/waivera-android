package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Material's pull-to-refresh with the app's [WaiveraRefreshIndicator], so the moment a pull is far
 * enough to refresh has a visible answer on every screen (M2-10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveraPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            WaiveraRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * Material's indicator, which pops on the pop token the moment the pull passes the refresh
 * threshold (M2-10). With the threshold vibration gone, the arrow's alpha change was the only cue
 * that letting go would refresh. It pops only on arming, never when a refresh starts on its own
 * or the pull goes back under the threshold, and never with animations off, where the arrow's
 * alpha change remains. The arming is derived, so a pull recomposes this only when it crosses
 * the threshold, and the pop is drawn in a layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveraRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val armed by remember(state) { derivedStateOf { state.distanceFraction >= 1f } }
    val pop = rememberPopScale(key = armed, enabled = armed && !isRefreshing)
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier.armingPop(pop, state),
    )
}

/**
 * Scales the indicator about the middle of the disc where the indicator's own layer has moved it
 * (down the pull, by `distanceFraction` of the threshold, from one disc height above its slot),
 * so the pop grows the disc in place instead of bobbing it toward its slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.armingPop(pop: State<Float>, state: PullToRefreshState): Modifier = graphicsLayer {
    val scale = pop.value
    scaleX = scale
    scaleY = scale
    val height = size.height
    if (height > 0f) {
        val discTop = state.distanceFraction * PullToRefreshDefaults.PositionalThreshold.toPx() - height
        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = (discTop + height / 2f) / height)
    }
}
