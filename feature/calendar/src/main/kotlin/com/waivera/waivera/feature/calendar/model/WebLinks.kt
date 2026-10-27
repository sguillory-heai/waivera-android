package com.waivera.waivera.feature.calendar.model

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Payload-derived links are untrusted (11-pitfalls §15): only absolute http(s) URLs with a host open. */
object WebLinks {
    fun isWebUrl(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return false
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
    }
}
