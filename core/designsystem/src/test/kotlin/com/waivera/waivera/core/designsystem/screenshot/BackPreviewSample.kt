package com.waivera.waivera.core.designsystem.screenshot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.waivera.waivera.core.designsystem.component.BackPreviewFrame
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** The two screens of the sample back swipe: Detail is the top of the stack, List is under it. */
internal enum class BackPreviewScreen { List, Detail }

/**
 * A back swipe from the left edge held at [BACK_PREVIEW_HALFWAY], driven like Navigation 3
 * drives the gesture: a seekable transition from Detail to List, through
 * [WaiveraMotionScheme.predictiveBackTransition] and [BackPreviewFrame].
 *
 * The z-index follows NavDisplay, which replaces every transform's `targetContentZIndex` with its
 * own: the screen on show when nothing is moving keeps 0, and the screen a pop reveals goes one
 * below it. Taking the transform's z-index as is gave the settled Detail screen the same -1 as
 * the revealed List, so the List, placed last, was drawn over the shrinking Detail card and the
 * capture showed only the List with a sliver of Detail at its edge (UI5-02).
 *
 * Each screen carries its name as a test tag, and every row reports a tap with its screen through
 * [onTap], so a test can tell which screen is on top.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun BackPreviewSample(motion: WaiveraMotionScheme, onTap: (BackPreviewScreen) -> Unit = {}) {
    val state = remember { SeekableTransitionState(BackPreviewScreen.Detail) }
    LaunchedEffect(state) { state.seekTo(fraction = BACK_PREVIEW_HALFWAY, targetState = BackPreviewScreen.List) }
    val transition = rememberTransition(state, label = "predictiveBack")
    transition.AnimatedContent(
        transitionSpec = {
            motion.predictiveBackTransition(swipeFromRight = false).apply {
                targetContentZIndex = if (initialState == targetState) SETTLED_Z_INDEX else REVEALED_Z_INDEX
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        BackPreviewFrame(
            scope = this,
            isTop = screen == BackPreviewScreen.Detail,
            modifier = Modifier
                .fillMaxSize()
                .testTag(screen.name),
        ) {
            SampleScreen(screen = screen, onTap = { onTap(screen) })
        }
    }
}

@Composable
private fun SampleScreen(screen: BackPreviewScreen, onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
    ) {
        when (screen) {
            BackPreviewScreen.List -> {
                WaiveraTopBar(title = "Chat")
                repeat(LIST_ROWS) { index ->
                    WaiveraListRow(headline = "Conversation ${index + 1}", supporting = "Last message", onClick = onTap)
                }
            }
            BackPreviewScreen.Detail -> {
                WaiveraTopBar(title = "Priya Raman", onBack = {})
                WaiveraSectionHeader(text = "Today")
                repeat(LIST_ROWS) { index ->
                    WaiveraListRow(
                        headline = "Message ${index + 1}",
                        supporting = "Can you cover Friday?",
                        onClick = onTap,
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.ExtraSmall),
                    )
                }
            }
        }
    }
}

internal const val BACK_PREVIEW_HALFWAY = 0.5f
private const val LIST_ROWS = 8
private const val SETTLED_Z_INDEX = 0f
private const val REVEALED_Z_INDEX = -1f
