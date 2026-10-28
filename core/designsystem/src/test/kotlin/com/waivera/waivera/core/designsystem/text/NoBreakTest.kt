package com.waivera.waivera.core.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NoBreakTest {
    @Test
    fun hyphensInIdsNeverBreak() {
        assertEquals("WAIV‑2527", "WAIV-2527".withNonBreakingHyphens())
        assertEquals("4821", "4821".withNonBreakingHyphens())
    }

    @Test
    fun hyphenatedNamesGetABreakPointAfterEachInnerHyphen() {
        assertEquals(
            "Maria-\u200BFernanda Castellanos-\u200BWhitaker",
            "Maria-Fernanda Castellanos-Whitaker".withBreaksAfterHyphens(),
        )
        assertEquals("old-\u200Bpilot-\u200Bprogram", "old-pilot-program".withBreaksAfterHyphens())
    }

    @Test
    fun hyphensThatDoNotJoinTwoWordPartsAreLeftAlone() {
        assertEquals("- note", "- note".withBreaksAfterHyphens())
        assertEquals("pre- and post-shift", "pre- and post-shift".withBreaksAfterHyphens().replace("\u200B", ""))
        assertEquals("pre- and post-\u200Bshift", "pre- and post-shift".withBreaksAfterHyphens())
        assertEquals("ends-", "ends-".withBreaksAfterHyphens())
    }

    @Test
    fun textWithoutHyphensIsReturnedAsIs() {
        val name = "Priya Raman"
        assertSame(name, name.withBreaksAfterHyphens())
    }

    @Test
    fun fileExtensionStaysWithTheName() {
        assertEquals("site-photo\u2060.\u2060jpg", "site-photo.jpg".withUnbreakableExtension())
        assertEquals("roster.v2\u2060.\u2060pdf", "roster.v2.pdf".withUnbreakableExtension())
        assertEquals("README", "README".withUnbreakableExtension())
        assertEquals(".env", ".env".withUnbreakableExtension())
        assertEquals("ends.", "ends.".withUnbreakableExtension())
    }
}
