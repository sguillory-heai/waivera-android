package com.waivera.waivera.core.data.live

/**
 * Something that holds per-user data and must forget it on sign-out (Room cache, image caches,
 * in-memory avatar cache). Contributed with `@IntoSet`; the app may add its own.
 */
fun interface SignOutCleaner {
    suspend fun clear()
}
