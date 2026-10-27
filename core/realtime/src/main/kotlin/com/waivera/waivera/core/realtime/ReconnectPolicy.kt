package com.waivera.waivera.core.realtime

import kotlin.math.min
import kotlin.random.Random

/**
 * Exponential backoff with full jitter for hub reconnects. Retries never stop
 * while the app is in the foreground; the delay is capped so a worker coming
 * back into signal reconnects within [maxDelayMillis].
 */
class ReconnectPolicy(
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    private val random: Random = Random.Default,
) {
    init {
        require(baseDelayMillis > 0) { "baseDelayMillis must be positive" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must be at least baseDelayMillis" }
    }

    /** Delay before reconnect [attempt], counted from 1. The first retry is immediate. */
    fun delayFor(attempt: Int): Long {
        require(attempt >= 1) { "attempt starts at 1" }
        if (attempt == 1) return 0
        val shift = min(attempt - 2, MAX_SHIFT)
        val ceiling = min(maxDelayMillis, baseDelayMillis shl shift)
        return random.nextLong(ceiling / 2, ceiling + 1)
    }

    companion object {
        const val DEFAULT_BASE_DELAY_MILLIS = 500L
        const val DEFAULT_MAX_DELAY_MILLIS = 30_000L

        // Keeps the shift well inside Long range; the cap applies long before this.
        private const val MAX_SHIFT = 20
    }
}
