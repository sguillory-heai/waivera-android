package com.waivera.waivera.core.network

import java.net.URI

/**
 * The one rule for handing a link to another app: http or https with a host. Screens check
 * links before they open them (SafeLinks, WebLinks); the app's link handler applies this again,
 * so a link that skips those checks still cannot fire `intent:`, `content:`, `file:` or another
 * app's custom scheme.
 */
object ExternalLinks {
    private val WEB_SCHEMES = setOf("http", "https")

    fun isOpenable(link: String?): Boolean {
        val uri = link?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { URI(it) }.getOrNull() }
        return uri != null && uri.scheme?.lowercase() in WEB_SCHEMES && !uri.host.isNullOrBlank()
    }
}
