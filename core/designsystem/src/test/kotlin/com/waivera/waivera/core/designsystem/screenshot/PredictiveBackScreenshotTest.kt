package com.waivera.waivera.core.designsystem.screenshot

import com.waivera.waivera.core.designsystem.component.BackPreviewFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The predictive back preview halfway through a swipe from the left edge, with animations on
 * (UI3-10, UI7-01). The tour runs with "Remove animations", so this is where the standard
 * preview is seen: the leaving screen shrunk to 0.95 and moved towards the right edge, on top
 * and opaque, with rounded corners and a hairline outline, and the screen underneath a little to
 * the left under a half-strength scrim. [BackPreviewFrame] draws all of it from the gesture's
 * progress, which [BackPreviewSample] holds at 0.5 while seeking a transition through
 * [WaiveraMotionScheme.predictiveBackTransition], as the shell does.
 * [PredictiveBackPreviewTest] asserts what this capture should show. The standard capture keeps
 * the animator scale on (`animations = true`); with it off the theme is the reduced scheme and
 * nothing is framed (UI4-10).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class PredictiveBackScreenshotTest {

    @Test
    fun standardMotionAtHalfway() = captureScreenshots("PredictiveBackStandard_halfway", animations = true) {
        BackPreviewSample(motion = WaiveraMotionScheme.Standard)
    }

    /** "Remove animations": the leaving screen holds, full size, until the swipe commits. */
    @Test
    fun reducedMotionAtHalfway() = captureScreenshots("PredictiveBackReduced_halfway") {
        BackPreviewSample(motion = WaiveraMotionScheme.Reduced)
    }
}
