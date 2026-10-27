package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.auth.AuthGateway
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.NetworkConfig
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
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Session lifecycle (01-auth.md): Firebase restores or signs in a user, then the backend
 * bootstrap (`GET /api/Authentication/login`, enveloped per G-02) yields the backend user id,
 * which is the only identity the rest of the app uses. The bootstrap runs once per Firebase uid
 * change, never per token refresh.
 */
@Singleton
@Suppress("LongParameterList")
class LiveSessionRepository @Inject constructor(
    private val gateway: AuthGateway,
    private val authApi: AuthenticationApi,
    private val secureStore: SecureStore,
    private val realtime: RealtimeClient,
    private val push: Provider<PushRegistrationRepository>,
    private val cleaners: Set<@JvmSuppressWildcards SignOutCleaner>,
    private val networkConfig: NetworkConfig,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionRepository {
    private val _state = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Why the next SignedOut happens, set before a forced Firebase sign-out. */
    private var pendingSignOutReason: WaiveraError? = null

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
                        _state.value = SessionState.SignedOut(pendingSignOutReason)
                        pendingSignOutReason = null
                    } else {
                        bootstrap(uid)
                    }
                }
            }
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
            secureStore.put(
                SecureStore.CACHED_SESSION,
                WaiveraJson.encodeToString(CachedSession.serializer(), CachedSession(uid, user)),
            )
            publish(user, login.requiresPasswordChange, offline = false)
        }.onFailure { failure ->
            val error = WaiveraError.from(failure)
            if (error.isAuthRejection()) {
                WaiveraLog.w(TAG, "Bootstrap rejected (${error.httpStatus}); signing out")
                pendingSignOutReason = error
                gateway.signOut()
                return
            }
            val cached = cachedSession()?.takeIf { it.uid == uid }
            if (cached != null) {
                publish(cached.user, requiresPasswordChange = false, offline = true)
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

    override suspend fun signOut(): Result<Unit> = catchingWaivera {
        realtime.stop()
        push.get().unregister().onFailure { WaiveraLog.w(TAG, "Push unregister failed during sign-out", it) }
        cleaners.forEach { cleaner -> runCatching { cleaner.clear() } }
        secureStore.clear(keep = setOf(SecureStore.DEVICE_ID, SecureStore.FCM_TOKEN))
        gateway.signOut()
        _state.value = SessionState.SignedOut()
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
    private data class CachedSession(val uid: String, val user: CurrentUser)

    companion object {
        private const val TAG = "Session"

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
