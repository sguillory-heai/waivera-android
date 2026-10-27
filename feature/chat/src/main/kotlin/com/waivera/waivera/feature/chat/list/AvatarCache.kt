package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.repository.PeopleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Decoded avatars for the rows on screen (spec 02 §4b, spec 10 §5). Each id is requested once
 * per screen, at most [CONCURRENCY] at a time; [PeopleRepository] owns the TTL and the
 * negative cache. Decoded images stay in [images], so a row scrolled back into view renders
 * its photo on the first frame with no placeholder flash.
 */
class AvatarCache<T : Any>(
    private val people: PeopleRepository,
    private val decode: (ByteArray) -> T?,
    private val scope: CoroutineScope,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _images = MutableStateFlow<Map<String, T>>(emptyMap())
    val images: StateFlow<Map<String, T>> = _images.asStateFlow()

    private val requested = mutableSetOf<String>()
    private val permits = Semaphore(CONCURRENCY)

    /** Ids already requested, invalid ids and bot ids are skipped without a request. */
    fun prefetch(userIds: List<String>) {
        val fresh = synchronized(requested) { userIds.distinct().filter { isValid(it) && requested.add(it) } }
        fresh.forEach { id ->
            scope.launch {
                permits.withPermit {
                    val bytes = people.avatar(id) ?: return@withPermit
                    val image = withContext(decodeDispatcher) { decode(bytes) } ?: return@withPermit
                    _images.update { it + (id to image) }
                }
            }
        }
    }

    companion object {
        const val CONCURRENCY = 4

        /** How many rows from the top get their avatars warmed. */
        const val PREFETCH_ROWS = 30

        fun isValid(userId: String): Boolean =
            userId.isNotBlank() && !userId.startsWith("bot:") && userId != "null" && userId != "undefined"
    }
}
