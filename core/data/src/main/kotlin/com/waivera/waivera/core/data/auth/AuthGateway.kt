package com.waivera.waivera.core.data.auth

import com.waivera.waivera.core.network.AuthTokenProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The Firebase user as far as the data layer cares. [uid] is auth-only, never an "is this me" key. */
data class AuthUser(val uid: String, val email: String?)

/**
 * Firebase Auth behind an interface so the session logic runs on the JVM in tests and in demo
 * mode. Every failure is a [com.waivera.waivera.core.network.error.WaiveraError].
 */
interface AuthGateway {
    /** False when Firebase values are missing from the build. */
    val isConfigured: Boolean

    val missingKeys: List<String>

    /** The persisted Firebase user; null when signed out. Emits once Firebase has restored it. */
    val user: StateFlow<AuthUser?>

    suspend fun signIn(email: String, password: String)

    fun signOut()

    suspend fun sendPasswordReset(email: String)

    suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String)

    /**
     * Blocking; called from OkHttp and SignalR threads. Null when signed out; throws
     * [com.waivera.waivera.core.network.error.TokenUnavailableException] when the token cannot
     * be fetched for a reason that can pass.
     */
    fun idTokenBlocking(forceRefresh: Boolean): String?
}

/** Supplies OkHttp and the hub with the Firebase ID token. */
class GatewayTokenProvider @Inject constructor(private val gateway: AuthGateway) : AuthTokenProvider {
    override fun idToken(forceRefresh: Boolean): String? = gateway.idTokenBlocking(forceRefresh)
}
