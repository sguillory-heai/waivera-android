package com.waivera.waivera.feature.profile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Your own celebration speaks to you by first name, in the second person (UI8-09), and never
 * names you in full as a system message would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class CelebrationCopyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun selfLabel(celebration: Celebration, firstName: String?): String {
        var label = ""
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                label = celebration.selfLabel(firstName)
            }
        }
        compose.waitForIdle()
        return label
    }

    @Test
    fun birthdayGreetsYouByFirstName() =
        assertEquals("Happy birthday, Alex!", selfLabel(Celebration(birthday = true, anniversaryYears = null), "Alex"))

    @Test
    fun bothDropTheYearsToStayShort() = assertEquals(
        "Happy birthday and work anniversary, Alex!",
        selfLabel(Celebration(birthday = true, anniversaryYears = 4), "Alex"),
    )

    @Test
    fun anniversaryKeepsTheYears() = assertEquals(
        "Happy 4-year work anniversary, Alex!",
        selfLabel(Celebration(birthday = false, anniversaryYears = 4), "Alex"),
    )

    @Test
    fun noFirstNameStillCelebrates() =
        assertEquals("Happy birthday!", selfLabel(Celebration(birthday = true, anniversaryYears = null), " "))

    @Test
    fun theProfileHeaderUsesTheSecondPerson() {
        val user = CurrentUser(userId = "user-1", displayName = "Alex Rivera", firstName = "Alex")
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                IdentityCard(
                    user = user,
                    profile = null,
                    celebration = Celebration(birthday = true, anniversaryYears = 4),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Happy birthday and work anniversary, Alex!", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Alex Rivera!", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
