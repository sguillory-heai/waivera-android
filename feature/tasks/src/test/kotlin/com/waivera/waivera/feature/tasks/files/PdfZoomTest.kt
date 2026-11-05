package com.waivera.waivera.feature.tasks.files

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfZoomTest {
    private val width = 400f

    @Test
    fun `pinching keeps the point under the fingers still`() {
        val zoom = PdfZoom()
        val scrolls = mutableListOf<Float>()
        zoom.change(2f, Offset(100f, 300f), Offset.Zero, width) { scrolls += it }
        assertEquals(2f, zoom.scale)
        // 100px across stays under the fingers: (100 - offset) / 2 = 100 at 1x.
        assertEquals(-100f, zoom.offsetX)
        // 300px down at 1x is 150px down in the zoomed window, so the list moves 150 (unzoomed) px.
        assertEquals(listOf(150f), scrolls)
    }

    @Test
    fun `zoom stays between fit and the maximum and never shows past an edge`() {
        val zoom = PdfZoom()
        zoom.change(10f, Offset(width, 0f), Offset.Zero, width) {}
        assertEquals(5f, zoom.scale)
        zoom.change(1f, Offset.Zero, Offset(-5_000f, 0f), width) {}
        assertEquals(-width * 4, zoom.offsetX)
        zoom.change(1f, Offset.Zero, Offset(5_000f, 0f), width) {}
        assertEquals(0f, zoom.offsetX)
        zoom.change(0.01f, Offset(200f, 0f), Offset.Zero, width) {}
        assertEquals(1f, zoom.scale)
        assertEquals(0f, zoom.offsetX)
    }

    @Test
    fun `panning down while zoomed scrolls the pages by the zoomed distance`() {
        val zoom = PdfZoom()
        zoom.change(2f, Offset.Zero, Offset.Zero, width) {}
        val scrolls = mutableListOf<Float>()
        zoom.change(1f, Offset(0f, 200f), Offset(0f, -100f), width) { scrolls += it }
        assertEquals(listOf(50f), scrolls)
        zoom.reset()
        assertEquals(1f, zoom.scale)
    }
}
