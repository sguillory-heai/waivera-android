package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.api.FileApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.apiCallOrNull
import com.waivera.waivera.core.network.error.catchingWaivera
import java.time.Clock
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The staff directory comes from `/api/chat/initiation-directory` (people the user may message),
 * merged with every conversation participant seen. Avatars come from `/api/file/avatar/{id}` as
 * data URIs; they are decoded once and cached in memory, including a short negative cache so a
 * list of avatar-less people does not refetch on every scroll.
 */
@Singleton
class LivePeopleRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val fileApi: FileApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val clock: Clock,
) : PeopleRepository,
    SignOutCleaner {
    private val avatars = LinkedHashMap<String, CachedAvatar>()
    private val avatarLock = Mutex()

    private class CachedAvatar(val bytes: ByteArray?, val expiresAt: Long)

    override fun observeDirectory(): Flow<Synced<List<Person>>> = sync.synced(KEY, cache.observePeople())

    override fun observePerson(userId: String): Flow<Person?> = cache.observePerson(userId)

    override suspend fun refresh(): Result<Unit> = sync.track(KEY) {
        val people = apiCall { chatApi.getInitiationDirectory() }.mapNotNull(Mappers::person)
        cache.putPeople(people)
    }

    @Suppress("ReturnCount")
    override suspend fun avatar(userId: String): ByteArray? {
        val now = clock.millis()
        avatarLock.withLock { avatars[userId]?.takeIf { it.expiresAt > now }?.let { return it.bytes } }
        val result = catchingWaivera { apiCallOrNull { fileApi.getAvatar(userId) } }
        val error = result.exceptionOrNull()
        // Transient failures are not cached, so the next screen tries again.
        if (error is WaiveraError && error.isTransient) return null
        val dto = result.getOrNull()
        val bytes = dto?.base64Image?.let(::decodeDataUri)
        val ttl = if (bytes !=
            null
        ) {
            (dto.expiresInSeconds.takeIf { it > 0 }?.times(MILLIS) ?: DEFAULT_TTL)
        } else {
            NEGATIVE_TTL
        }
        avatarLock.withLock {
            avatars[userId] = CachedAvatar(bytes, now + ttl)
            if (avatars.size > MAX_AVATARS) avatars.remove(avatars.keys.first())
        }
        return bytes
    }

    override suspend fun clear() {
        avatarLock.withLock { avatars.clear() }
    }

    companion object {
        const val KEY = "people"
        private const val MILLIS = 1_000L
        private const val DEFAULT_TTL = 30 * 60 * 1_000L
        private const val NEGATIVE_TTL = 5 * 60 * 1_000L
        private const val MAX_AVATARS = 200

        /** `data:image/png;base64,....` to bytes; null for anything else. */
        @Suppress("ReturnCount")
        fun decodeDataUri(uri: String): ByteArray? {
            if (!uri.startsWith("data:")) return null
            val comma = uri.indexOf(',').takeIf { it > 0 } ?: return null
            if (!uri.substring(0, comma).endsWith(";base64")) return null
            return runCatching { Base64.getDecoder().decode(uri.substring(comma + 1).trim()) }.getOrNull()
        }
    }
}
