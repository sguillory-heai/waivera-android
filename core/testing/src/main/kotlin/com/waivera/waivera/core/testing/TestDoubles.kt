package com.waivera.waivera.core.testing

import com.waivera.waivera.core.data.auth.AuthGateway
import com.waivera.waivera.core.data.auth.AuthUser
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.outbox.OutboxScheduler
import com.waivera.waivera.core.data.realtime.AppForegroundMonitor
import com.waivera.waivera.core.data.realtime.ConnectivityMonitor
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.data.store.SettingsStore
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A clock tests can move. */
class TestClock(var now: Instant = Instant.parse("2026-11-01T16:30:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advanceMillis(millis: Long) {
        now = now.plusMillis(millis)
    }
}

class InMemorySecureStore : SecureStore {
    val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override suspend fun clear(keep: Set<String>) {
        values.keys.retainAll(keep)
    }
}

class InMemorySettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setTheme(theme: ThemePreference) = state.update { it.copy(theme = theme) }

    override suspend fun setHapticsEnabled(enabled: Boolean) = state.update { it.copy(hapticsEnabled = enabled) }

    override suspend fun updateNotifications(transform: (NotificationPreferences) -> NotificationPreferences) =
        state.update { it.copy(notifications = transform(it.notifications)) }

    override suspend fun clear() {
        state.value = AppSettings()
    }
}

class FakeConnectivityMonitor(online: Boolean = true) : ConnectivityMonitor {
    val online = MutableStateFlow(online)
    override val isOnline: StateFlow<Boolean> = this.online
}

class FakeForegroundMonitor(foreground: Boolean = true) : AppForegroundMonitor {
    val foreground = MutableStateFlow(foreground)
    override val isForeground: StateFlow<Boolean> = this.foreground
}

class RecordingOutboxScheduler : OutboxScheduler {
    var scheduled = 0
        private set

    override fun schedule() {
        scheduled++
    }
}

/** Firebase stand-in: [accounts] maps email to password; [signInError] forces a failure. */
class FakeAuthGateway(override val missingKeys: List<String> = emptyList(), initialUser: AuthUser? = null) :
    AuthGateway {
    val accounts = mutableMapOf("alex.rivera@example.com" to "correct-horse")
    var signInError: WaiveraError? = null
    var token: String? = "test-token"
    val tokenRequests = mutableListOf<Boolean>()
    var signOuts = 0
        private set

    override val isConfigured: Boolean get() = missingKeys.isEmpty()
    private val _user = MutableStateFlow(initialUser)
    override val user: StateFlow<AuthUser?> = _user

    override suspend fun signIn(email: String, password: String) {
        signInError?.let { throw it }
        if (accounts[email] != password) throw WaiveraError.InvalidCredentials()
        _user.value = AuthUser("firebase-uid-${email.substringBefore('@')}", email)
    }

    override fun signOut() {
        signOuts++
        _user.value = null
    }

    override suspend fun sendPasswordReset(email: String) = Unit

    override suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String) {
        val email = _user.value?.email ?: throw WaiveraError.NotSignedIn()
        if (accounts[email] != currentPassword) throw WaiveraError.InvalidCredentials()
        accounts[email] = newPassword
    }

    override fun idTokenBlocking(forceRefresh: Boolean): String? {
        tokenRequests += forceRefresh
        return token.takeIf { _user.value != null }
    }
}

/** A session repository whose state tests set directly. */
class TestSessionRepository(
    initial: SessionState = SessionState.SignedIn(
        CurrentUser(userId = "usr_alex_rivera", timeZoneId = "America/Chicago"),
    ),
) : SessionRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutableState
    var refreshes = 0
        private set

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut(): Result<Unit> {
        mutableState.value = SessionState.SignedOut()
        return Result.success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun refresh(): Result<Unit> {
        refreshes++
        return Result.success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        Result.success(Unit)
}
