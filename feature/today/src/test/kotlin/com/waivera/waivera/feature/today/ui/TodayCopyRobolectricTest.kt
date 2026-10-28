package com.waivera.waivera.feature.today.ui

import android.content.Context
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Today's copy, resolved against the module's real string resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class TodayCopyRobolectricTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    /** The reward shows its 🎉 once, held to the last word by a no-break space (UI8-02). */
    @Test
    fun allCaughtUpTitleHasOnePartyPopperTiedToTheLastWord() {
        val title = context.getString(R.string.feature_today_all_caught_up_title)
        assertEquals(1, title.split(PARTY_POPPER).size - 1, "\"$title\" should hold exactly one $PARTY_POPPER")
        assertTrue(title.endsWith("$NO_BREAK_SPACE$PARTY_POPPER"), "\"$title\" lets the emoji wrap onto its own line")
    }

    /** Emoji belong to celebrations only; the greeting carries none (owner's note, voice.md). */
    @Test
    fun greetingsCarryNoEmoji() {
        listOf(
            R.string.feature_today_brand_greeting_morning,
            R.string.feature_today_brand_greeting_afternoon,
            R.string.feature_today_brand_greeting_evening,
        ).forEach { id ->
            val greeting = context.getString(id)
            assertTrue(greeting.all { it.isLetter() || it == ' ' }, "\"$greeting\" holds more than words")
        }
        listOf(
            R.string.feature_today_brand_greeting_morning_named,
            R.string.feature_today_brand_greeting_afternoon_named,
            R.string.feature_today_brand_greeting_evening_named,
        ).forEach { id ->
            val greeting = context.getString(id, "Alex")
            assertTrue(greeting.all { it.isLetter() || it == ' ' || it == ',' }, "\"$greeting\" holds more than words")
        }
    }

    private companion object {
        const val PARTY_POPPER = "🎉"
        const val NO_BREAK_SPACE = ' '
    }
}
