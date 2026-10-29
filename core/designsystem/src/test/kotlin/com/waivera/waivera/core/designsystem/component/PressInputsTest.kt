package com.waivera.waivera.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PressInputsTest {
    private var now = 1_000L
    private val inputs = PressInputs(clock = { now })

    @Test
    fun `a touch presses on the way down and lets go on the way up`() {
        assertEquals(true, inputs.pointer(down = true))
        assertEquals(false, inputs.pointer(down = false))
    }

    @Test
    fun `the clickable's own press during a touch is the same gesture`() {
        inputs.pointer(down = true)
        assertNull(inputs.interaction(down = true), "already pressed by the finger")
        assertEquals(false, inputs.pointer(down = false))
        assertNull(inputs.interaction(down = false))
    }

    @Test
    fun `a press the list held back until release does not press a second time`() {
        inputs.pointer(down = true)
        inputs.pointer(down = false)
        now += 16
        assertNull(inputs.interaction(down = true), "the late press is the touch's echo")
        assertNull(inputs.interaction(down = false))
    }

    @Test
    fun `a keyboard press long after any touch presses and releases`() {
        inputs.pointer(down = true)
        inputs.pointer(down = false)
        now += PressTiming.POINTER_ECHO_MILLIS
        assertEquals(true, inputs.interaction(down = true))
        assertEquals(false, inputs.interaction(down = false))
    }

    @Test
    fun `a quick tap is held for the minimum visible press`() {
        assertEquals(PressTiming.MIN_VISIBLE_PRESS_MILLIS, PressTiming.releaseDelayMillis(500, 500))
        assertEquals(PressTiming.MIN_VISIBLE_PRESS_MILLIS - 30, PressTiming.releaseDelayMillis(500, 530))
        assertEquals(0L, PressTiming.releaseDelayMillis(500, 500 + PressTiming.MIN_VISIBLE_PRESS_MILLIS))
        assertEquals(0L, PressTiming.releaseDelayMillis(500, 2_000), "a long press releases at once")
    }
}
