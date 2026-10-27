package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.error.NotSignedInException
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Supplies the signed-in user's Firebase ID token. Called on OkHttp's background
 * threads, so implementations may block (for example `Tasks.await` on Firebase).
 */
fun interface AuthTokenProvider {
    /**
     * Returns the current token, or a refreshed one when [forceRefresh] is true. Null means
     * signed out (no user, or one Firebase no longer accepts); a failure that can pass throws
     * [com.waivera.waivera.core.network.error.TokenUnavailableException] instead.
     */
    fun idToken(forceRefresh: Boolean): String?
}

/**
 * Adds `Authorization: Bearer <Firebase ID token>` to requests for the sprout_api host only (the
 * token never leaves for another host, such as an image CDN) and retries once with a
 * force-refreshed token on 401, matching the Expo client. With no signed-in user the request
 * fails locally with [NotSignedInException] and never reaches the network.
 *
 * One-shot bodies (streamed uploads) cannot be replayed, so their 401 is returned to the caller,
 * which re-creates the body and retries once (see the attachments repository).
 *
 * A request that already carries `Authorization` is sent as is: the push unregister retry after
 * sign-out uses the token captured while the person was still signed in.
 *
 * A 401 even with the refreshed token goes to [onRejected], so the session can check whether the
 * account was revoked server-side (IF-05).
 */
class AuthTokenInterceptor(
    private val tokens: AuthTokenProvider,
    private val apiBaseUrl: HttpUrl? = null,
    private val onRejected: () -> Unit = {},
) : Interceptor {
    @Suppress("ReturnCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.isForApi() || request.header(AUTHORIZATION) != null) return chain.proceed(request)

        val token = tokens.idToken(forceRefresh = false) ?: throw NotSignedInException()
        val response = chain.proceed(request.withBearer(token))
        if (response.code != HTTP_UNAUTHORIZED || request.body?.isOneShot() == true) return response

        val refreshed = try {
            tokens.idToken(forceRefresh = true)
        } catch (unavailable: IOException) {
            response.close()
            throw unavailable
        } ?: return response
        response.close()
        return chain.proceed(request.withBearer(refreshed)).also { retried ->
            if (retried.code == HTTP_UNAUTHORIZED) onRejected()
        }
    }

    private fun Request.isForApi(): Boolean {
        val base = apiBaseUrl ?: return true
        return url.host == base.host && url.port == base.port && url.scheme == base.scheme
    }

    private fun Request.withBearer(token: String): Request = newBuilder().header(AUTHORIZATION, "Bearer $token").build()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val AUTHORIZATION = "Authorization"
    }
}
