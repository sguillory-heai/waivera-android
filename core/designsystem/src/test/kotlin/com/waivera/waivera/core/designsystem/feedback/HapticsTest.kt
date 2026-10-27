package com.waivera.waivera.core.designsystem.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticsTest {
    private val performed = mutableListOf<HapticSignal>()
    private var now = 1_000L
    private var enabled = true
    private val haptics = Haptics(performSignal = { performed += it }, isEnabled = { enabled }, clock = { now })

    @Test
    fun `each moment maps to the signal in the feedback map`() {
        val expected = mapOf(
            HapticMoment.ToggleOn to HapticSignal.ToggleOn,
            HapticMoment.ToggleOff to HapticSignal.ToggleOff,
            HapticMoment.Selection to HapticSignal.SegmentTick,
            HapticMoment.SwipeThreshold to HapticSignal.GestureThresholdActivate,
            HapticMoment.Success to HapticSignal.Confirm,
            HapticMoment.Error to HapticSignal.Reject,
            HapticMoment.Warning to HapticSignal.Reject,
            HapticMoment.LongPress to HapticSignal.LongPress,
        )
        assertEquals(HapticMoment.entries.toSet(), expected.keys)
        expected.forEach { (moment, signal) -> assertEquals(signal, moment.signal(), "$moment") }
    }

    @Test
    fun `settings switch off suppresses every haptic`() {
        enabled = false
        HapticMoment.entries.forEach(haptics::perform)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `rapid repeats of one moment fire once`() {
        repeat(5) {
            haptics.success()
            now += 20
        }
        assertEquals(listOf(HapticSignal.Confirm), performed)
    }

    @Test
    fun `the same moment fires again after the repeat window`() {
        haptics.success()
        now += Haptics.MIN_REPEAT_MILLIS
        haptics.success()
        assertEquals(listOf(HapticSignal.Confirm, HapticSignal.Confirm), performed)
    }

    @Test
    fun `different moments in quick succession all fire`() {
        haptics.toggle(on = true)
        haptics.toggle(on = false)
        haptics.error()
        assertEquals(listOf(HapticSignal.ToggleOn, HapticSignal.ToggleOff, HapticSignal.Reject), performed)
    }

    @Test
    fun `turning the switch back on restores haptics`() {
        enabled = false
        haptics.longPress()
        enabled = true
        haptics.longPress()
        assertEquals(listOf(HapticSignal.LongPress), performed)
    }
}
