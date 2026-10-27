package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.error.NotSignedInException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Supplies the signed-in user's Firebase ID token. Called on OkHttp's background
 * threads, so implementations may block (for example `Tasks.await` on Firebase).
 */
fun interface AuthTokenProvider {
    /** Returns the current token, or a refreshed one when [forceRefresh] is true; null when signed out. */
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
 */
class AuthTokenInterceptor(
    private val tokens: AuthTokenProvider,
    private val apiBaseUrl: HttpUrl? = null,
) : Interceptor {
    @Suppress("ReturnCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.isForApi()) return chain.proceed(request)

        val token = tokens.idToken(forceRefresh = false) ?: throw NotSignedInException()
        val response = chain.proceed(request.withBearer(token))
        if (response.code != HTTP_UNAUTHORIZED || request.body?.isOneShot() == true) return response

        val refreshed = tokens.idToken(forceRefresh = true) ?: return response
        response.close()
        return chain.proceed(request.withBearer(refreshed))
    }

    private fun Request.isForApi(): Boolean {
        val base = apiBaseUrl ?: return true
        return url.host == base.host && url.port == base.port && url.scheme == base.scheme
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
