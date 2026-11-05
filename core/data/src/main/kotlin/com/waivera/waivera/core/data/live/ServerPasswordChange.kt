package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.auth.AuthGateway
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.dto.ChangePasswordRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCallUnit
import java.security.MessageDigest

/**
 * The steps of the web's server-owned password change, apart from the session around them:
 * Firebase checks the current password and hands back a fresh ID token, then sprout_api's
 * `change-password` sets the new password and stamps the change date.
 *
 * The server changes Firebase before it stamps the date, so an answer that never arrived
 * (offline, a 5xx) may hide a change that half happened. Whenever the new password already signs
 * in, the change is sent once more with that sign-in's token: the server accepts the same
 * password again and stamps the date, so the gate can never stay stuck.
 */
internal class ServerPasswordChange(private val gateway: AuthGateway, private val authApi: AuthenticationApi) {
    /** The last new password sent, by digest only, for a retry after a lost answer. Memory only. */
    @Volatile
    private var lastAttempt: Attempt? = null

    /** Throws a [WaiveraError]; on return the server has the new password and its change date. */
    suspend fun change(uid: String, currentPassword: String, newPassword: String) {
        val attempt = Attempt(uid, digest(newPassword))
        val idToken = freshToken(currentPassword, newPassword, attempt)
        lastAttempt = attempt
        send(idToken, newPassword)
    }

    /** The change finished or can no longer be resumed. */
    fun forget() {
        lastAttempt = null
    }

    /** Another account signed in: its retries have nothing to do with this one's. */
    fun forgetUnless(uid: String?) {
        if (lastAttempt?.uid != uid) lastAttempt = null
    }

    /**
     * A token from proving the current password. On a retry of the same change whose answer was
     * lost, the current password may already be refused while the new one signs in: that
     * sign-in's token then carries the change instead.
     */
    private suspend fun freshToken(currentPassword: String, newPassword: String, attempt: Attempt): String = try {
        gateway.reauthenticateForIdToken(currentPassword)
    } catch (refused: WaiveraError.InvalidCredentials) {
        if (lastAttempt?.matches(attempt) != true) throw refused
        reauthenticateWithNew(newPassword, orThrow = refused)
    }

    /**
     * Signs in with the new password, or throws [orThrow] when Firebase says it is not the
     * password (yet). Any other failure (offline, too many attempts) is reported as itself.
     */
    private suspend fun reauthenticateWithNew(newPassword: String, orThrow: WaiveraError): String = try {
        gateway.reauthenticateForIdToken(newPassword)
    } catch (_: WaiveraError.InvalidCredentials) {
        throw orThrow
    }

    /** A definitive refusal (4xx) forgets the attempt; a lost or failed answer checks for a half-done change. */
    private suspend fun send(idToken: String, newPassword: String) {
        try {
            apiCallUnit { authApi.changePassword(ChangePasswordRequest(idToken, newPassword)) }
        } catch (failure: WaiveraError) {
            if (!failure.isTransient) {
                forget()
                throw failure
            }
            val retryToken = reauthenticateWithNew(newPassword, orThrow = failure)
            apiCallUnit { authApi.changePassword(ChangePasswordRequest(retryToken, newPassword)) }
        }
    }

    private fun digest(password: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))

    /** A new password already sent to `change-password`; never the password itself. */
    private class Attempt(val uid: String, val digest: ByteArray) {
        fun matches(other: Attempt): Boolean = uid == other.uid && MessageDigest.isEqual(digest, other.digest)
    }
}
