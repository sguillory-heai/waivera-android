package com.waivera.waivera.core.realtime

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReconnectPolicyTest {
    private val policy = ReconnectPolicy(baseDelayMillis = 500, maxDelayMillis = 30_000, random = Random(42))

    @Test
    fun firstRetryIsImmediate() {
        assertEquals(0, policy.delayFor(1))
    }

    @Test
    fun delaysGrowExponentiallyWithinJitterBounds() {
        val expectedCeilings = listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L)
        expectedCeilings.forEachIndexed { index, ceiling ->
            val delay = policy.delayFor(index + 2)
            assertTrue(delay in (ceiling / 2)..ceiling, "attempt ${index + 2}: $delay not in ${ceiling / 2}..$ceiling")
        }
    }

    @Test
    fun delaysNeverExceedTheCapEvenAfterManyAttempts() {
        (2..200).forEach { attempt -> assertTrue(policy.delayFor(attempt) <= 30_000) }
    }

    @Test
    fun rejectsAttemptZero() {
        assertFailsWith<IllegalArgumentException> { policy.delayFor(0) }
    }
}
