package com.waivera.waivera.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Waits in real time (up to [timeoutMillis]) for [condition], for work that crosses a real
 * socket such as MockWebServer calls launched in a background scope. The test scheduler keeps
 * running background tasks while this suspends.
 */
suspend fun eventually(timeoutMillis: Long = 5_000, condition: suspend () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "condition not met within $timeoutMillis ms" }
        withContext(Dispatchers.Default) { delay(POLL_MILLIS) }
    }
}

private const val POLL_MILLIS = 10L
