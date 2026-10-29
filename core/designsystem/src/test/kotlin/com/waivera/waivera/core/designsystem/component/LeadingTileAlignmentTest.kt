package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
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
 * The leading icon tile of a [WaiveraListRow] (v7, item 1): on a row made tall by long content it
 * sits [WaiveraSpacing.LeadingTileTop] below the content's top, level with the headline's first
 * line; on a one-line row it is centred exactly as before.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class LeadingTileAlignmentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(headline: String, supporting: String?, fontScale: Float) {
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

    private companion object {
        const val ROW = "row"
        const val SUPPORTING = 160
        val PIXEL = 1.dp
    }
}
