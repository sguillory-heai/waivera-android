package com.waivera.waivera.feature.calendar.model

import com.waivera.waivera.core.network.ExternalLinks

/** Payload-derived links are untrusted (11-pitfalls §15): only absolute http(s) URLs with a host open. */
object WebLinks {
    fun isWebUrl(raw: String?): Boolean = ExternalLinks.isOpenable(raw)
}
