package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.EventTiming
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The agenda event card's leading tile (v7, item 1): a card made tall by a long title, location
 * and organizer keeps its tile at the top, level with the title's first line, instead of
 * centred in the card; a short card keeps it centred. The milestone card's avatar follows the same
 * rule once its sentence wraps the card tall (v7 follow-up, item 2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class AgendaCardTileLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val start = ZonedDateTime.of(LocalDate.of(2026, 11, 2).atTime(9, 0), ZoneId.of("America/Chicago"))

    private fun card(title: String, location: String?, owner: String?) = AgendaItem.EventCard(
        event = ScheduleEvent(id = "evt_1", title = title, location = location, ownerName = owner),
        timing = EventTiming.Timed(start, start.plusHours(1), startsBeforeDay = false, endsAfterDay = false),
    )

    private val anniversary: AgendaItem.MilestoneCard = run {
        val occurrence = MilestoneOccurrence(
            milestone = Milestone(
                type = MilestoneType.WORK_ANNIVERSARY,
                userId = "usr_jordan_lee",
                displayName = "Jordan Lee",
                month = 11,
                day = 1,
                startYear = 2021,
            ),
            date = LocalDate.of(2026, 11, 1),
            years = 5,
        )
        AgendaItem.MilestoneCard(occurrence, requireNotNull(MilestoneIndex.celebrationOf(listOf(occurrence))))
    }

    private fun show(item: AgendaItem, fontScale: Float, wide: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    val width = if (wide) Modifier.requiredWidth(WIDE_CARD) else Modifier.fillMaxWidth()
                    Box(width.testTag(CARD)) {
                        AgendaCard(item = item, currentYear = 2026, onOpenEvent = {}, myUserId = "usr_alex")
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun aTallEventCardPinsItsTileToTheTop() {
        show(
            card(
                title = LongFixtures.PARAGRAPHS_3000.take(LONG_TITLE),
                location = LongFixtures.CHANNEL_LONG,
                owner = LongFixtures.NAME_60,
            ),
            fontScale = 2f,
        )
        val card = bounds(CARD)
        val tile = bounds(AGENDA_CARD_TILE_TAG)
        assertTrue(
            "the card is not tall: ${card.bottom - card.top}",
            card.bottom - card.top > (tile.bottom - tile.top) * 2,
        )
        val fromTop = tile.top - card.top
        val limit = WaiveraSpacing.MediumSmall + WaiveraSpacing.LeadingTileTop + PIXEL
        assertTrue("the tile sits $fromTop below the card's top, over $limit", fromTop <= limit)
    }

    @Test
    fun aShortEventCardKeepsItsTileCentred() {
        show(card(title = "Standup", location = null, owner = null), fontScale = 1f)
        val card = bounds(CARD)
        val tile = bounds(AGENDA_CARD_TILE_TAG)
        assertEquals(((card.top + card.bottom) / 2).value, ((tile.top + tile.bottom) / 2).value, PIXEL.value)
    }

    @Test
    fun aTallMilestoneCardPinsItsAvatarToTheTop() {
        show(anniversary, fontScale = 2f)
        val sentence = bounds(MILESTONE_SENTENCE_TAG)
        val avatar = bounds(MILESTONE_AVATAR_TAG)
        assertTrue(
            "the sentence is not tall: ${sentence.bottom - sentence.top}",
            sentence.bottom - sentence.top > (avatar.bottom - avatar.top) * 2,
        )
        // The sentence is the row's tallest child, so its top is the row's top.
        assertEquals(WaiveraSpacing.LeadingTileTop.value, (avatar.top - sentence.top).value, PIXEL.value)
    }

    @Test
    fun aShortMilestoneCardKeepsItsAvatarCentred() {
        show(anniversary, fontScale = 1f, wide = true)
        val sentence = bounds(MILESTONE_SENTENCE_TAG)
        val avatar = bounds(MILESTONE_AVATAR_TAG)
        assertTrue(
            "the sentence is taller than the avatar",
            sentence.bottom - sentence.top < avatar.bottom - avatar.top,
        )
        assertEquals(
            ((sentence.top + sentence.bottom) / 2).value,
            ((avatar.top + avatar.bottom) / 2).value,
            PIXEL.value,
        )
    }

    private companion object {
        const val CARD = "card"
        val WIDE_CARD = 600.dp
        const val LONG_TITLE = 90
        val PIXEL = 1.dp
    }
}
