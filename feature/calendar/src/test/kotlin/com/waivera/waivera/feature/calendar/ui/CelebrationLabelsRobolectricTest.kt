package com.waivera.waivera.feature.calendar.ui

import android.content.Context
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.Celebration
import com.waivera.waivera.feature.calendar.model.CelebrationType
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The 07 §4 celebration copy, resolved against the module's real string resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CelebrationLabelsRobolectricTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val fallback get() = context.getString(R.string.feature_calendar_celebration_name_fallback)

    @Test
    fun compactLabels() {
        assertEquals(
            "Birthday + 5-yr anniversary",
            CelebrationText.compact(Celebration(CelebrationType.MULTIPLE, 5, "Sam")).resolve(context),
        )
        assertEquals(
            "Birthday today",
            CelebrationText.compact(Celebration(CelebrationType.BIRTHDAY, null, "Sam")).resolve(context),
        )
        assertEquals(
            "3-year anniversary",
            CelebrationText.compact(Celebration(CelebrationType.WORK_ANNIVERSARY, 3, "Sam")).resolve(context),
        )
    }

    @Test
    fun fullLabelsUseTheNameOrTheFallback() {
        assertEquals(
            "It's Priya Raman's birthday today!",
            CelebrationText.full(Celebration(CelebrationType.BIRTHDAY, null, "Priya Raman"), fallback).resolve(context),
        )
        assertEquals(
            "Celebrating This employee's 5-year work anniversary!",
            CelebrationText.full(Celebration(CelebrationType.WORK_ANNIVERSARY, 5, " "), fallback).resolve(context),
        )
        assertEquals(
            "It's Jordan Lee's birthday and 5-year work anniversary!",
            CelebrationText.full(Celebration(CelebrationType.MULTIPLE, 5, "Jordan Lee"), fallback).resolve(context),
        )
    }

    @Test
    fun accessibleLabelIsAFullSentence() {
        assertEquals(
            "Jordan Lee is celebrating a 5-year work anniversary today.",
            CelebrationText.accessible(Celebration(CelebrationType.WORK_ANNIVERSARY, 5, "Jordan Lee"), fallback)
                .resolve(context),
        )
    }
}
