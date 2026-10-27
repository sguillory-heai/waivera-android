package com.waivera.waivera.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Environment settings from Gradle properties (`waivera.apiBaseUrl`, `waivera.webBaseUrl`)
 * compiled into BuildConfig. Nothing here is ever hard-coded per environment.
 */
data class NetworkConfig(val apiBaseUrl: String, val webBaseUrl: String) {
    /** The API base with exactly one trailing slash, or null when unset or invalid. */
    val apiBase: HttpUrl? = normalize(apiBaseUrl)

    /** The web app base (for the video lobby handoff), or null when unset or invalid. */
    val webBase: HttpUrl? = normalize(webBaseUrl)

    val isConfigured: Boolean get() = apiBase != null

    /** `{apiBase}/api/chatHub`. The `api/` segment is mandatory (hub.md §1). */
    val chatHubUrl: String? get() = apiBase?.resolve(CHAT_HUB_PATH)?.toString()

    /** Retrofit needs a base URL even when unconfigured; requests to it fail fast as offline. */
    val retrofitBaseUrl: HttpUrl get() = apiBase ?: UNCONFIGURED_BASE

    /** Missing settings, by Gradle property name, for the configuration error screen. */
    val missingKeys: List<String> get() = if (apiBase == null) listOf("waivera.apiBaseUrl") else emptyList()

    /** Resolves a relative server path such as `/api/chat/message/9320/attachment`. */
    fun resolveApiUrl(path: String): HttpUrl? = apiBase?.resolve(path.trimStart('/'))

    companion object {
        const val CHAT_HUB_PATH = "api/chatHub"
        private val UNCONFIGURED_BASE: HttpUrl = HttpUrl.Builder().scheme("https").host("unconfigured.invalid").build()

        private fun normalize(raw: String): HttpUrl? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            return "$trimmed/".toHttpUrlOrNull()?.takeIf {
                it.scheme == "https" ||
                    it.host == "10.0.2.2" ||
                    it.host == "localhost"
            }
        }
    }
}
