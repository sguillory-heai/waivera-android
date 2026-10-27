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
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The predictive back preview halfway through a swipe from the left edge, with animations on
 * (UI3-10). The tour runs with "Remove animations", so this is where the standard preview is
 * seen: the leaving screen shrinks and follows the finger, on top and opaque, and the screen
 * underneath shows around it with a slight parallax. It drives [WaiveraMotionScheme.Standard]'s
 * transform through a seekable transition, the mechanism Navigation 3 uses for the gesture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class PredictiveBackScreenshotTest {

    @Test
    fun standardMotionAtHalfway() = captureScreenshots("PredictiveBackStandard_halfway") {
        BackPreview(motion = WaiveraMotionScheme.Standard)
    }

    /** "Remove animations": the leaving screen holds, full size, until the swipe commits. */
    @Test
    fun reducedMotionAtHalfway() = captureScreenshots("PredictiveBackReduced_halfway") {
        BackPreview(motion = WaiveraMotionScheme.Reduced)
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    private fun BackPreview(motion: WaiveraMotionScheme) {
        val state = remember { SeekableTransitionState(Screen.Detail) }
        LaunchedEffect(state) { state.seekTo(fraction = HALFWAY, targetState = Screen.List) }
        val transition = rememberTransition(state, label = "predictiveBack")
        transition.AnimatedContent(
            transitionSpec = { motion.predictiveBackTransition(swipeFromRight = false) },
            modifier = Modifier.fillMaxSize(),
        ) { screen ->
            SampleScreen(screen)
        }
    }

    @Composable
    private fun SampleScreen(screen: Screen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WaiveraTheme.colors.background),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        ) {
            when (screen) {
                Screen.List -> {
                    WaiveraTopBar(title = "Chat")
                    repeat(LIST_ROWS) { index ->
                        WaiveraListRow(headline = "Conversation ${index + 1}", supporting = "Last message", onClick = {
                        })
                    }
                }
                Screen.Detail -> {
                    WaiveraTopBar(title = "Priya Raman", onBack = {})
                    WaiveraSectionHeader(text = "Today")
                    repeat(LIST_ROWS) { index ->
                        WaiveraListRow(
                            headline = "Message ${index + 1}",
                            supporting = "Can you cover Friday?",
                            modifier = Modifier.padding(horizontal = WaiveraSpacing.ExtraSmall),
                        )
                    }
                }
            }
        }
    }

    private enum class Screen { List, Detail }

    private companion object {
        const val HALFWAY = 0.5f
        const val LIST_ROWS = 8
    }
}
