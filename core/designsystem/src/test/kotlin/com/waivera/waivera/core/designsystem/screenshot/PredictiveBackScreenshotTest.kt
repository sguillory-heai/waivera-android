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
 * (UI3-10). The tour runs with "Remove animations", so this is where the standard preview is
 * seen: the leaving screen shrinks and follows the finger, on top and opaque, with rounded
 * corners, and the screen underneath shows around it with a slight parallax under a scrim. It
 * drives [WaiveraMotionScheme.Standard]'s transform and [BackPreviewFrame] through a seekable
 * transition, the mechanism Navigation 3 uses for the gesture ([BackPreviewSample]).
 * [PredictiveBackPreviewTest] asserts what this capture should show. The standard capture keeps the
 * animator scale on (`animations = true`); with it off the seek snaps to the end and the two
 * captures looked the same (UI4-10).
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
