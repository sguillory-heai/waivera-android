package com.waivera.waivera.core.designsystem.text

import android.content.res.Resources
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The one self-celebration rule Profile, Today and Schedule share (UI9-05), against the real strings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class CelebrationCopyTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    private fun self(birthday: Boolean, years: Int?, firstName: String?): String? =
        CelebrationCopy.self(birthday = birthday, anniversaryYears = years, firstName = firstName)?.resolve(resources)

    @Test
    fun speaksToYouByFirstName() {
        assertEquals("Happy birthday, Alex!", self(birthday = true, years = null, firstName = "Alex"))
        assertEquals("Happy 3-year work anniversary, Alex!", self(birthday = false, years = 3, firstName = "Alex"))
        assertEquals("Happy 1-year work anniversary, Alex!", self(birthday = false, years = 1, firstName = "Alex"))
        assertEquals(
            "Happy birthday and work anniversary, Alex!",
            self(birthday = true, years = 3, firstName = "Alex"),
        )
    }

    @Test
    fun withNoUsableNameItStillCelebrates() {
        assertEquals("Happy birthday!", self(birthday = true, years = null, firstName = " "))
        assertEquals("Happy 4-year work anniversary!", self(birthday = false, years = 4, firstName = null))
        assertEquals("Happy birthday and work anniversary!", self(birthday = true, years = 4, firstName = ""))
    }

    @Test
    fun nothingToCelebrateIsNull() {
        assertNull(CelebrationCopy.self(birthday = false, anniversaryYears = null, firstName = "Alex"))
    }

    @Test
    fun firstNameIsTheFirstWordOfTheDisplayName() {
        assertEquals("Alex", CelebrationCopy.firstName("Alex Rivera"))
        assertEquals("Priya", CelebrationCopy.firstName("  Priya   Raman "))
        assertEquals("Sam", CelebrationCopy.firstName("Sam"))
        assertNull(CelebrationCopy.firstName("   "))
        assertNull(CelebrationCopy.firstName(null))
    }

    /** No emoji in the sentence: the card shows it once, in its tile. */
    @Test
    fun theSentenceCarriesNoEmoji() {
        listOf(
            self(birthday = true, years = null, firstName = "Alex"),
            self(birthday = false, years = 2, firstName = "Alex"),
            self(birthday = true, years = 2, firstName = "Alex"),
        ).forEach { sentence ->
            val text = checkNotNull(sentence)
            assertEquals(text, text.filter { it.code < EMOJI_FLOOR }, "\"$text\" carries an emoji")
        }
    }

    private companion object {
        const val EMOJI_FLOOR = 0x2000
    }
}
