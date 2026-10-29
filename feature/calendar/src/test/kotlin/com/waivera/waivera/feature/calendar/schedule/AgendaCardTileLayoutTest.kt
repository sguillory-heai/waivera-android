package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.EventTiming
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
 * centred in the card; a short card keeps it centred.
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

    private fun show(item: AgendaItem, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.fillMaxWidth().testTag(CARD)) {
                        AgendaCard(item = item, currentYear = 2026, onOpenEvent = {})
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

    private companion object {
        const val CARD = "card"
        const val LONG_TITLE = 90
        val PIXEL = 1.dp
    }
}
