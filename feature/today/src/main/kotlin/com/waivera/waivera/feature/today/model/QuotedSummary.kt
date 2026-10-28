package com.waivera.waivera.feature.today.model

/**
 * A chat activity summary split for the Needs you card: the server writes a mention as
 * `{sender} mentioned you: "{snippet}"` (`ChatAttentionService`), and concept A shows the lead
 * as the headline and the snippet as a quote on its own line. [lead] is null when the summary is
 * not in that shape, and then the whole summary is the [quote].
 */
data class QuotedSummary(val lead: String?, val quote: String) {
    companion object {
        private const val SEPARATOR = ": \""
        private const val QUOTE = '"'

        fun of(summary: String): QuotedSummary {
            val text = summary.trim()
            val at = text.indexOf(SEPARATOR)
            val lead = if (at > 0) text.substring(0, at).trim() else ""
            val quote = if (at > 0) text.substring(at + SEPARATOR.length).removeSuffix(QUOTE.toString()).trim() else ""
            return if (lead.isNotEmpty() &&
                quote.isNotEmpty()
            ) {
                QuotedSummary(lead, quote)
            } else {
                QuotedSummary(null, text)
            }
        }
    }
}
