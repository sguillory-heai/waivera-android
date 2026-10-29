package com.waivera.waivera.core.designsystem.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticsTest {
    private val performed = mutableListOf<HapticSignal>()
    private var now = 1_000L
    private var enabled = true
    private val haptics = Haptics(performSignal = { performed += it }, isEnabled = { enabled }, clock = { now })

    /** Policy 2026-10-29: only a failed send and a rejected sign-in vibrate, each with Reject. */
    @Test
    fun `only a failed send and a rejected sign-in vibrate, each with the reject signal`() {
        val expected = mapOf(
            HapticMoment.SendFailed to HapticSignal.Reject,
            HapticMoment.SignInRejected to HapticSignal.Reject,
        )
        assertEquals(expected.keys, HapticMoment.entries.toSet(), "no other moment may vibrate")
        expected.forEach { (moment, signal) -> assertEquals(signal, moment.signal(), "$moment") }
        assertEquals(listOf(HapticSignal.Reject), HapticSignal.entries.toList(), "no other signal exists")
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
            haptics.perform(HapticMoment.SendFailed)
            now += 20
        }
        assertEquals(listOf(HapticSignal.Reject), performed)
    }

    @Test
    fun `the same moment fires again after the repeat window`() {
        haptics.perform(HapticMoment.SendFailed)
        now += Haptics.MIN_REPEAT_MILLIS
        haptics.perform(HapticMoment.SendFailed)
        assertEquals(listOf(HapticSignal.Reject, HapticSignal.Reject), performed)
    }

    @Test
    fun `different moments in quick succession all fire`() {
        haptics.perform(HapticMoment.SignInRejected)
        haptics.perform(HapticMoment.SendFailed)
        assertEquals(listOf(HapticSignal.Reject, HapticSignal.Reject), performed)
    }

    @Test
    fun `turning the switch back on restores haptics`() {
        enabled = false
        haptics.perform(HapticMoment.SignInRejected)
        enabled = true
        haptics.perform(HapticMoment.SignInRejected)
        assertEquals(listOf(HapticSignal.Reject), performed)
    }
}
