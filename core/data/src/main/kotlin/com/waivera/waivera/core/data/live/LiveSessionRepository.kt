package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.auth.AuthGateway
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.SessionRejections
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.dto.UserLoginResponseDto
import com.waivera.waivera.core.network.dto.UserWithRoleDto
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.apiCallUnit
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.network.log.WaiveraLog
import com.waivera.waivera.core.realtime.RealtimeClient
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * Session lifecycle (01-auth.md): Firebase restores or signs in a user, then the backend
 * bootstrap (`GET /api/Authentication/login`, enveloped per G-02) yields the backend user id,
 * which is the only identity the rest of the app uses. The bootstrap runs once per Firebase uid
 * change, never per token refresh.
 *
 * Every way a session ends (sign-out, a rejected bootstrap, Firebase dropping the user, another
 * account signing in) goes through [endSession], which runs [SessionEnder] once (AF-04, AS-01).
 * [SecureStore.CACHED_SESSION] doubles as the marker that per-user data may exist, so a session
 * that ended while the process was dead is cleaned up on the next start.
 */
@Singleton
@Suppress("LongParameterList")
class LiveSessionRepository @Inject constructor(
    private val gateway: AuthGateway,
    private val authApi: AuthenticationApi,
    private val secureStore: SecureStore,
    private val realtime: RealtimeClient,
    private val ender: SessionEnder,
    private val networkConfig: NetworkConfig,
    private val rejections: SessionRejections,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionRepository {
    private val _state = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val endLock = Mutex()

    @Volatile
    private var pendingConfirm: PendingConfirm? = null

    init {
        val missing = gateway.missingKeys + networkConfig.missingKeys
        if (missing.isNotEmpty()) {
            _state.value = SessionState.ConfigurationMissing(missing)
        } else {
            scope.launch {
                gateway.user.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                    if (pendingConfirm?.uid != uid) pendingConfirm = null
                    if (uid == null) {
                        endSession(reason = null)
                    } else {
                        endPreviousAccount(uid)
                        bootstrap(uid)
                    }
                }
            }
            scope.launch {
                rejections.events.collect {
                    recheckAfterRejection()
                    // Further rejections meanwhile collapse into one check after the pause.
                    delay(RECHECK_PAUSE_MILLIS)
                }
            }
        }
    }

    /**
     * A 401 survived a refreshed token (IF-05). The bootstrap is asked again; only its own
     * rejection ends the session. Anything else (offline, 5xx, one endpoint's 401) leaves it.
     */
    private suspend fun recheckAfterRejection() {
        if (_state.value !is SessionState.SignedIn) return
        val error = catchingWaivera { fetchUser() }.exceptionOrNull()?.let(WaiveraError::from) ?: return
        if (error.isAuthRejection() && _state.value is SessionState.SignedIn) {
            WaiveraLog.w(TAG, "Session rejected (${error.httpStatus}); signing out")
            endSession(reason = error, force = true)
            gateway.signOut()
        }
    }

    /**
     * [announce] publishes [SessionState.Initializing] first, which puts the shell on its loader;
     * a refresh from inside the app (a voluntary password change) keeps the current state up
     * until the result is in.
     */
    private suspend fun bootstrap(uid: String, announce: Boolean = true) {
        if (announce) _state.value = SessionState.Initializing
        val result = catchingWaivera { fetchUser() }
        result.onSuccess { (login, user) ->
            val cached = CachedSession(uid, user, login.requiresPasswordChange)
            // The cached copy only serves offline starts; a keystore or disk failure writing it
            // must not stop the person from getting in.
            val encoded = WaiveraJson.encodeToString(CachedSession.serializer(), cached)
            catchingWaivera { secureStore.put(SecureStore.CACHED_SESSION, encoded) }
                .onFailure { WaiveraLog.w(TAG, "Could not cache the session (${(it.cause ?: it).javaClass.name})") }
            publish(user, login.requiresPasswordChange, offline = false)
        }.onFailure { failure ->
            val error = WaiveraError.from(failure)
            if (error.isAuthRejection()) {
                WaiveraLog.w(TAG, "Bootstrap rejected (${error.httpStatus}); signing out")
                // Clean up while the Firebase token still works, then let Firebase forget the user.
                endSession(reason = error, force = true)
                gateway.signOut()
                return
            }
            val cached = cachedSession()?.takeIf { it.uid == uid }
            if (cached != null) {
                // The password-change gate holds offline too (AF-06).
                publish(cached.user, cached.requiresPasswordChange, offline = true)
            } else {
                _state.value = SessionState.BootstrapFailed(error)
            }
        }
    }

    private suspend fun fetchUser(): Pair<UserLoginResponseDto, CurrentUser> {
        val login = apiCall { authApi.login() }.requireData()
        val userId = login.userId?.takeIf(String::isNotBlank) ?: throw WaiveraError.AccountNotProvisioned()
        // Profile details are optional: a failure here should not block chat.
        val details = catchingWaivera { apiCall { authApi.getUserDetails(userId) }.requireData() }.getOrNull()
        val firebaseEmail = gateway.user.value?.email
        return login to currentUser(userId, details, firebaseEmail, login.requiresOnboarding)
    }

    private fun publish(user: CurrentUser, requiresPasswordChange: Boolean, offline: Boolean) {
        _state.value = if (requiresPasswordChange) {
            SessionState.PasswordChangeRequired(user)
        } else {
            SessionState.SignedIn(user, isOffline = offline)
        }
        realtime.start(user.userId)
    }

    private suspend fun cachedSession(): CachedSession? = secureStore.get(SecureStore.CACHED_SESSION)?.let {
        runCatching { WaiveraJson.decodeFromString(CachedSession.serializer(), it) }.getOrNull()
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = catchingWaivera {
        if (_state.value is SessionState.ConfigurationMissing) throw WaiveraError.Configuration(gateway.missingKeys)
        _state.value = SessionState.Initializing
        try {
            gateway.signIn(email, password)
        } catch (e: WaiveraError) {
            _state.value = SessionState.SignedOut()
            throw e
        }
        when (val settled = state.first { it !is SessionState.Initializing }) {
            is SessionState.SignedIn, is SessionState.PasswordChangeRequired -> Unit
            is SessionState.SignedOut -> throw settled.reason ?: WaiveraError.Unexpected()
            is SessionState.BootstrapFailed -> throw settled.error
            is SessionState.ConfigurationMissing -> throw WaiveraError.Configuration(settled.missingKeys)
            SessionState.Initializing -> Unit
        }
    }

    /** Unregisters while the Firebase token is still valid, then signs Firebase out. */
    override suspend fun signOut(): Result<Unit> = catchingWaivera {
        endSession(reason = null, force = true, byUser = true)
        gateway.signOut()
    }

    /**
     * Ends the session once: [SessionEnder] runs when a session was live or left data behind
     * (or always, with [force]), then the state becomes [SessionState.SignedOut]. A reason
     * already published is kept when a later call has none (the Firebase sign-out that follows).
     */
    private suspend fun endSession(reason: WaiveraError?, force: Boolean = false, byUser: Boolean = false) =
        endLock.withLock {
            val current = _state.value
            if (force || current.currentUserId != null || secureStore.get(SecureStore.CACHED_SESSION) != null) {
                ender.end()
            }
            val keepReason = current is SessionState.SignedOut && reason == null
            _state.value = if (keepReason) current else SessionState.SignedOut(reason, byUser)
        }

    /** Firebase switched straight to another user: the previous account's data goes first. */
    private suspend fun endPreviousAccount(uid: String) = endLock.withLock {
        val cached = cachedSession() ?: return@withLock
        if (cached.uid != uid) ender.end()
    }

    /**
     * Waits for the bootstrap to settle; if it is still running after [timeoutMillis], answers
     * with the cached session of the same Firebase user, so an inline reply can be queued (AF-07).
     */
    override suspend fun settledUserId(timeoutMillis: Long): String? {
        val settled = withTimeoutOrNull(timeoutMillis) { state.first { it !is SessionState.Initializing } }
        if (settled != null) return settled.currentUserId
        val uid = gateway.user.value?.uid
        return cachedSession()?.takeIf { uid != null && it.uid == uid }?.user?.userId
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = catchingWaivera {
        gateway.sendPasswordReset(email)
    }

    override suspend fun refresh(): Result<Unit> = catchingWaivera {
        val uid = gateway.user.value?.uid ?: throw WaiveraError.NotSignedIn()
        // Signed in already (a profile edit, coming back online): keep the screens up meanwhile.
        bootstrap(uid, announce = state.value.currentUserId == null)
        (state.value as? SessionState.BootstrapFailed)?.let { throw it.error }
    }

    /**
     * Firebase first, then `confirm-password-change`. If Firebase took the new password but the
     * confirm call failed (offline), a retry with the same new password skips Firebase, which
     * would now reject the old one as the current password, and only confirms. The session is
     * reloaded without [SessionState.Initializing], so the screen stays up while it runs.
     */
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = catchingWaivera {
        val uid = gateway.user.value?.uid ?: throw WaiveraError.NotSignedIn()
        val digest = digest(newPassword)
        val pending = pendingConfirm
        if (pending == null || pending.uid != uid || !MessageDigest.isEqual(pending.digest, digest)) {
            gateway.reauthenticateAndUpdatePassword(currentPassword, newPassword)
            pendingConfirm = PendingConfirm(uid, digest)
        }
        apiCallUnit { authApi.confirmPasswordChange() }
        pendingConfirm = null
        bootstrap(uid, announce = false)
        (state.value as? SessionState.BootstrapFailed)?.let { throw it.error }
    }

    private fun digest(password: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))

    /** A Firebase password change still waiting for the backend confirm; memory only, never the password. */
    private class PendingConfirm(val uid: String, val digest: ByteArray)

    private fun WaiveraError.isAuthRejection(): Boolean = this is WaiveraError.AccountNotProvisioned ||
        this is WaiveraError.SessionExpired ||
        this is WaiveraError.Forbidden ||
        this is WaiveraError.NotSignedIn

    @Serializable
    private data class CachedSession(
        val uid: String,
        val user: CurrentUser,
        /** Kept so an offline start still holds the password-change gate (AF-06). */
        val requiresPasswordChange: Boolean = false,
    )

    companion object {
        private const val TAG = "Session"

        /** At most one revocation check a minute, whatever the number of rejected calls. */
        private const val RECHECK_PAUSE_MILLIS = 60_000L

        fun currentUser(
            userId: String,
            details: UserWithRoleDto?,
            fallbackEmail: String?,
            requiresOnboarding: Boolean,
        ) = CurrentUser(
            userId = userId,
            email = details?.email ?: fallbackEmail,
            firstName = details?.firstName,
            lastName = details?.lastName,
            displayName = details?.displayName,
            avatarUrl = details?.avatarURL,
            timeZoneId = details?.timezone,
            organizationId = details?.organizationId ?: 0,
            organizationName = details?.organizationName,
            roles = details?.roles.orEmpty(),
            phoneNumber = details?.phoneNumber,
            requiresOnboarding = requiresOnboarding,
            scId = details?.scId?.takeIf(String::isNotBlank),
        )
    }
}
