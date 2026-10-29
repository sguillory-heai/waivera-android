package com.waivera.waivera.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.designsystem.component.HERO_CELEBRATION_TEXT_TAG
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The person page's hero with the long-name fixtures on the small phone (wave 29 N4): the name
 * keeps to two lines, three at 200%, never breaks mid-word, and TalkBack reads it whole; the
 * celebration note says only the first name; and at 200% Message is on the first screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class PersonHeroNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val message = context.getString(R.string.feature_profile_person_message)

    private fun show(name: String, fontScale: Float, celebration: Celebration? = ANNIVERSARY) {
        val details = PersonDetails(
            displayName = name,
            email = "maria-fernanda@example.com",
            phoneNumber = "(512) 555-0142",
            roles = listOf("Care team lead"),
            organization = "North Region",
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    PersonDetailContent(
                        state = PersonDetailUiState(
                            userId = "usr_long",
                            name = name,
                            email = details.email,
                            details = Section.Ready(details),
                            celebration = celebration,
                        ),
                        snackbar = remember { SnackbarHostState() },
                        onBack = {},
                        onMessage = {},
                        onRetry = {},
                        onRetryConnection = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private fun assertHero(name: String, fontScale: Float, maxLines: Int) {
        show(name, fontScale)
        val layout = layoutOf(PERSON_HERO_NAME_TAG)
        val text = layout.layoutInput.text.text
        assertTrue("\"$name\" takes ${layout.lineCount} lines at ${fontScale}x", layout.lineCount <= maxLines)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertFalse(
                "\"$name\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
            )
        }
        val spoken = compose.onNodeWithTag(PERSON_HERO_NAME_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
            .filterNot { it == ZERO_WIDTH_SPACE }
        assertEquals("TalkBack's name", name, spoken)

        val note = layoutOf(HERO_CELEBRATION_TEXT_TAG).layoutInput.text.text.filterNot { it == ZERO_WIDTH_SPACE }
        val firstName = name.trim().substringBefore(' ')
        assertTrue("the note \"$note\" does not name $firstName", firstName in note)
        if (' ' in name.trim()) assertFalse("the note repeats the full name: \"$note\"", name in note)
    }

    @Test
    fun sixtyCharacterNameAtDefaultSize() = assertHero(LongFixtures.NAME_60, 1f, maxLines = 2)

    @Test
    fun sixtyCharacterNameAtLargeFont() = assertHero(LongFixtures.NAME_60, LARGE_FONT, maxLines = 3)

    @Test
    fun unbrokenNameAtLargeFont() = assertHero(LongFixtures.NAME_UNBROKEN_40, LARGE_FONT, maxLines = 3)

    @Test
    fun twoLongWordsAtLargeFont() = assertHero(LongFixtures.NAME_TWO_LONG_WORDS, LARGE_FONT, maxLines = 3)

    @Test
    fun oneCharacterNameAtLargeFont() = assertHero(LongFixtures.NAME_ONE_CHAR, LARGE_FONT, maxLines = 3)

    private fun assertMessageOnFirstScreen(name: String, celebration: Celebration?) {
        show(name, LARGE_FONT, celebration)
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val button = compose.onNode(hasText(message) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Message ends at ${button.bottom}px, below the ${screen.bottom}px screen, for \"$name\" at 200%",
            button.bottom <= screen.bottom,
        )
        assertTrue("Message starts above the screen for \"$name\"", button.top >= screen.top)
    }

    @Test
    fun messageIsOnTheFirstScreenForTheSixtyCharacterName() =
        assertMessageOnFirstScreen(LongFixtures.NAME_60, ANNIVERSARY)

    @Test
    fun messageIsOnTheFirstScreenForTheUnbrokenName() =
        assertMessageOnFirstScreen(LongFixtures.NAME_UNBROKEN_40, ANNIVERSARY)

    @Test
    fun messageIsOnTheFirstScreenForTwoLongWords() =
        assertMessageOnFirstScreen(LongFixtures.NAME_TWO_LONG_WORDS, BIRTHDAY_AND_ANNIVERSARY)

    @Test
    fun messageIsOnTheFirstScreenWithNoCelebration() =
        assertMessageOnFirstScreen(LongFixtures.NAME_60, celebration = null)

    /** Your own profile's hero keeps the same limits, and its note says only your first name. */
    private fun assertProfileHero(name: String, fontScale: Float, maxLines: Int) {
        val user = CurrentUser(userId = "usr_me", email = "me@example.com", displayName = name)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    IdentityCard(user = user, profile = null, celebration = BIRTHDAY_AND_ANNIVERSARY)
                }
            }
        }
        compose.waitForIdle()
        val layout = layoutOf(PROFILE_HERO_NAME_TAG)
        val text = layout.layoutInput.text.text
        assertTrue("\"$name\" takes ${layout.lineCount} lines at ${fontScale}x", layout.lineCount <= maxLines)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertFalse(
                "\"$name\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
            )
        }
        val note = layoutOf(HERO_CELEBRATION_TEXT_TAG)
        val noteText = note.layoutInput.text.text
        for (line in 0 until note.lineCount - 1) {
            val end = note.getLineEnd(line)
            assertFalse(
                "the note breaks mid-word after \"${noteText.substring(0, end)}\"",
                noteText[end - 1].isLetterOrDigit() && noteText[end].isLetterOrDigit(),
            )
        }
    }

    @Test
    fun profileHeroSixtyCharacterNameAtLargeFont() = assertProfileHero(LongFixtures.NAME_60, LARGE_FONT, maxLines = 3)

    @Test
    fun profileHeroUnbrokenNameAtLargeFont() =
        assertProfileHero(LongFixtures.NAME_UNBROKEN_40, LARGE_FONT, maxLines = 3)

    @Test
    fun profileHeroSixtyCharacterNameAtDefaultSize() = assertProfileHero(LongFixtures.NAME_60, 1f, maxLines = 2)

    private companion object {
        const val LARGE_FONT = 2f
        const val ZERO_WIDTH_SPACE = '​'
        val ANNIVERSARY = Celebration(birthday = false, anniversaryYears = 7)
        val BIRTHDAY_AND_ANNIVERSARY = Celebration(birthday = true, anniversaryYears = 12)
    }
}
