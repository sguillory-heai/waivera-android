package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.PersonDetails
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.FileApi
import com.waivera.waivera.core.network.dto.UserInfoResponse
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.apiCallOrNull
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import java.time.Clock
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer

/**
 * The staff directory comes from `GET /api/users?onlyActive=true`, the list the web chat uses:
 * staff in the caller's own organization. Caseload participants in that list are left out, as
 * the staff app has always done. `get-username-list` is never used because it lists every
 * organization's users, and `initiation-directory` is SUPER ADMIN only (AF-01). The directory
 * replaces earlier entries, keeping only people who are also in a cached conversation. Avatars
 * come from `/api/file/avatar/{id}` as data URIs; they are decoded once and cached in memory,
 * including a short negative cache so a list of avatar-less people does not refetch on every
 * scroll.
 */
@Singleton
class LivePeopleRepository @Inject constructor(
    private val fileApi: FileApi,
    private val authApi: AuthenticationApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val clock: Clock,
) : PeopleRepository,
    SignOutCleaner {
    private val avatars = LinkedHashMap<String, CachedAvatar>()
    private val avatarLock = Mutex()
    private val scopeLock = Mutex()

    /** Set once the old cache is known to be gone; sign-out clears it with the cache. */
    @Volatile private var scopeChecked = false

    private class CachedAvatar(val bytes: ByteArray?, val expiresAt: Long)

    override fun observeDirectory(): Flow<Synced<List<Person>>> = sync.synced(
        KEY,
        flow {
            dropLegacyDirectory()
            emitAll(cache.observePeople())
        },
    )

    override fun observePerson(userId: String): Flow<Person?> = cache.observePerson(userId)

    override suspend fun refresh(): Result<Unit> = sync.track(KEY) {
        val entries = apiCallOrNull { authApi.getUsers(onlyActive = true) }.orEmpty()
        dropLegacyDirectory()
        val known = cache.observePeople().first().associateBy { it.id }
        val directory = entries.mapNotNull { directoryPerson(it, known) }
        // The list always holds at least the caller, so an empty one is a server fault, not a
        // reason to clear everyone already shown.
        if (directory.isNotEmpty()) cache.replaceDirectory(directory)
    }

    /**
     * Builds before 2026-11-03 cached every organization's staff from `get-username-list`. Once
     * per sign-in, before the cache is first shown or refreshed, everyone not in a cached
     * conversation is dropped, so that list never shows again, offline included.
     */
    private suspend fun dropLegacyDirectory() {
        if (scopeChecked) return
        scopeLock.withLock {
            if (scopeChecked) return
            if (cache.document(SCOPE_KEY, String.serializer()) != SCOPE) {
                cache.replaceDirectory(emptyList())
                // The old list's sync time would make the emptied cache look fresh.
                sync.forget(KEY)
                cache.putDocument(SCOPE_KEY, String.serializer(), SCOPE)
            }
            scopeChecked = true
        }
    }

    /** A staff member from the directory, keeping cached fields the directory leaves empty. */
    private fun directoryPerson(entry: UserInfoResponse, known: Map<String, Person>): Person? {
        val id = entry.id?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (entry.isParticipant || entry.deactivated || entry.employeeRole.equals(PARTICIPANT, ignoreCase = true)) {
            return null
        }
        val cached = known[id]
        return Person(
            id = id,
            firstName = entry.firstName.clean() ?: cached?.firstName,
            lastName = entry.lastName.clean() ?: cached?.lastName,
            displayName = entry.displayName.clean() ?: cached?.displayName,
            email = entry.email.clean() ?: cached?.email,
            avatarUrl = cached?.avatarUrl,
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    override suspend fun details(userId: String): Result<PersonDetails> = catchingWaivera {
        val dto = apiCall { authApi.getUserDetails(userId) }.requireData()
        PersonDetails(
            userId = dto.id?.takeIf(String::isNotBlank) ?: userId,
            firstName = dto.firstName,
            lastName = dto.lastName,
            displayName = dto.displayName,
            email = dto.email,
            phoneNumber = dto.phoneNumber,
            roles = dto.roles.map(String::trim).filter(String::isNotEmpty),
            organizationName = dto.organizationName?.takeIf(String::isNotBlank),
            scId = dto.scId?.takeIf(String::isNotBlank),
        )
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
        scopeChecked = false
        avatarLock.withLock { avatars.clear() }
    }

    companion object {
        const val KEY = "people"
        private const val PARTICIPANT = "Participant"
        private const val SCOPE_KEY = "people.directoryScope"
        private const val SCOPE = "organization"
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
