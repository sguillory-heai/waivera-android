package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The leading icon tile of a [WaiveraListRow] and [WaiveraSwitchRow] (v7, item 1): on a row made
 * tall by long content (three or more lines) it sits [WaiveraSpacing.LeadingTileTop] below the
 * content's top, level with the headline's first line; on a one-line or a standard two-line row
 * (title and subtitle) it is centred exactly as before, at 100% and 200%.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class LeadingTileAlignmentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(headline: String, supporting: String?, fontScale: Float, detail: String? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column {
                        WaiveraListRow(
                            headline = headline,
                            supporting = supporting,
                            onClick = {},
                            showChevron = true,
                            leading = { WaiveraIconTile(icon = WaiveraIcons.Calendar) },
                            detail = detail,
                            modifier = Modifier.testTag(ROW),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showSwitchRow(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column {
                        WaiveraSwitchRow(
                            title = TWO_LINE_TITLE,
                            subtitle = TWO_LINE_SUBTITLE,
                            checked = true,
                            onCheckedChange = {},
                            leading = {
                                WaiveraIconTile(icon = WaiveraIcons.Calendar, modifier = Modifier.testTag(SWITCH_TILE))
                            },
                            modifier = Modifier.testTag(ROW),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun aTallRowPinsTheTileToTheTop() {
        show(
            headline = LongFixtures.NAME_60,
            supporting = LongFixtures.PARAGRAPHS_3000.take(SUPPORTING),
            fontScale = 2f,
        )
        val row = bounds(ROW)
        val tile = bounds(LIST_ROW_LEADING_TAG)
        assertTrue("the row is not tall: ${row.bottom - row.top}", row.bottom - row.top > (tile.bottom - tile.top) * 2)
        val fromTop = tile.top - row.top
        val limit = WaiveraSpacing.MediumSmall + WaiveraSpacing.LeadingTileTop + PIXEL
        assertTrue("the tile sits $fromTop below the row's top, over $limit", fromTop <= limit)
    }

    @Test
    fun aOneLineRowKeepsTheTileCentred() {
        show(headline = "Notifications", supporting = null, fontScale = 1f)
        val row = bounds(ROW)
        val tile = bounds(LIST_ROW_LEADING_TAG)
        val rowCentre = (row.top + row.bottom) / 2
        val tileCentre = (tile.top + tile.bottom) / 2
        assertEquals(rowCentre.value, tileCentre.value, PIXEL.value)
    }

    @Test
    fun aThreeLineRowPinsTheTileToTheTop() {
        show(
            headline = TWO_LINE_TITLE,
            supporting = TWO_LINE_SUBTITLE,
            detail = "jordan.lee@example.com",
            fontScale = 1f,
        )
        val row = bounds(ROW)
        val tile = bounds(LIST_ROW_LEADING_TAG)
        val rowCentre = (row.top + row.bottom) / 2
        val tileCentre = (tile.top + tile.bottom) / 2
        assertTrue("the tile is still centred", rowCentre - tileCentre > PIXEL)
        assertEquals(
            (WaiveraSpacing.MediumSmall + WaiveraSpacing.LeadingTileTop).value,
            (tile.top - row.top).value,
            PIXEL.value,
        )
    }

    @Test
    fun aStandardTwoLineRowKeepsTheTileCentred() {
        show(headline = TWO_LINE_TITLE, supporting = TWO_LINE_SUBTITLE, fontScale = 1f)
        assertCentred(bounds(ROW), bounds(LIST_ROW_LEADING_TAG))
    }

    @Test
    fun aStandardTwoLineRowKeepsTheTileCentredAtLargeFont() {
        show(headline = TWO_LINE_TITLE, supporting = TWO_LINE_SUBTITLE, fontScale = 2f)
        val row = bounds(ROW)
        val tile = bounds(LIST_ROW_LEADING_TAG)
        // Taller than the tile plus twice the pin, so the old rule would have pinned it.
        assertTrue(
            "the row is not taller than the tile: ${row.bottom - row.top}",
            row.bottom - row.top - WaiveraSpacing.MediumSmall * 2 >
                tile.bottom - tile.top + WaiveraSpacing.LeadingTileTop * 2 + PIXEL,
        )
        assertCentred(row, tile)
    }

    @Test
    fun aStandardTwoLineSwitchRowKeepsTheTileCentred() {
        showSwitchRow(fontScale = 1f)
        assertCentred(bounds(ROW), bounds(SWITCH_TILE))
    }

    @Test
    fun aStandardTwoLineSwitchRowKeepsTheTileCentredAtLargeFont() {
        showSwitchRow(fontScale = 2f)
        assertCentred(bounds(ROW), bounds(SWITCH_TILE))
    }

    @Test
    fun theRuleMatchesCenterVerticallyUpToTheStandardHeight() {
        val alignment = LeadingTileAlignment(topPx = TOP_PX, standardPx = STANDARD_PX)
        for (size in listOf(TILE_PX, TILE_PX + 1)) {
            for (space in size..STANDARD_PX) {
                val centred = Alignment.CenterVertically.align(size, space)
                assertEquals("size $size, space $space", centred, alignment.align(size, space))
            }
            for (space in STANDARD_PX + 1..STANDARD_PX * 2) {
                val pinned = minOf(Alignment.CenterVertically.align(size, space), TOP_PX)
                assertEquals("size $size, space $space", pinned, alignment.align(size, space))
            }
        }
    }

    private fun assertCentred(row: DpRect, tile: DpRect) {
        val rowCentre = (row.top + row.bottom) / 2
        val tileCentre = (tile.top + tile.bottom) / 2
        assertEquals(rowCentre.value, tileCentre.value, PIXEL.value)
    }

    private companion object {
        const val ROW = "row"
        const val SWITCH_TILE = "switch_tile"
        const val TWO_LINE_TITLE = "Alerts"
        const val TWO_LINE_SUBTITLE = "Daily digest"
        const val TOP_PX = 4
        const val TILE_PX = 80
        const val STANDARD_PX = 100
        const val SUPPORTING = 160
        val PIXEL = 1.dp
    }
}
