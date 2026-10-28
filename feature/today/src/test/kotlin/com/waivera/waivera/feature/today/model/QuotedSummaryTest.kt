package com.waivera.waivera.feature.today.model

import kotlin.test.Test
import kotlin.test.assertEquals

class QuotedSummaryTest {
    @Test
    fun aMentionSplitsIntoItsLeadAndQuote() {
        assertEquals(
            QuotedSummary(lead = "Priya Raman mentioned you", quote = "the Hughes visit moved to 10"),
            QuotedSummary.of("Priya Raman mentioned you: \"the Hughes visit moved to 10\""),
        )
    }

    @Test
    fun quotesInsideTheSnippetStayWhole() {
        assertEquals(
            QuotedSummary(lead = "Sam mentioned you", quote = "see \"Plan B\": the 2nd draft"),
            QuotedSummary.of("Sam mentioned you: \"see \"Plan B\": the 2nd draft\""),
        )
    }

    @Test
    fun anyOtherSummaryIsAllQuote() {
        assertEquals(
            QuotedSummary(null, "Sam mentioned you in Care team"),
            QuotedSummary.of("Sam mentioned you in Care team"),
        )
        assertEquals(QuotedSummary(null, "Sam mentioned you: \"\""), QuotedSummary.of("Sam mentioned you: \"\""))
        assertEquals(QuotedSummary(null, ": \"hello\""), QuotedSummary.of(": \"hello\""))
    }
}
