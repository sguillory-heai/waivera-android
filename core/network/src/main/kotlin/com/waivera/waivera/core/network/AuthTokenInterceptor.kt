package com.waivera.waivera.core.network

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
 * Adds `Authorization: Bearer <Firebase ID token>` and retries once with a
 * refreshed token on 401, matching the Expo app's API client.
 */
class AuthTokenInterceptor(private val tokens: AuthTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.idToken(forceRefresh = false)
        val response = chain.proceed(chain.request().withBearer(token))
        val refreshed = if (response.code == HTTP_UNAUTHORIZED && token != null) {
            tokens.idToken(forceRefresh = true)
        } else {
            null
        }
        if (refreshed == null) return response

        response.close()
        return chain.proceed(chain.request().withBearer(refreshed))
    }

    private fun Request.withBearer(token: String?): Request =
        if (token == null) this else newBuilder().header("Authorization", "Bearer $token").build()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
