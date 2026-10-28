package com.waivera.waivera.core.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun aFileNameSplitsBeforeTheLastPartOfItsStem() {
        assertEquals("north-roster-" to "wk43.pdf", "north-roster-wk43.pdf".fileNameHeadAndTail())
        assertEquals("Hughes care " to "plan.pdf", "Hughes care plan.pdf".fileNameHeadAndTail())
        assertEquals("site_" to "photo.jpg", "site_photo.jpg".fileNameHeadAndTail())
        assertEquals("roster." to "v2.pdf", "roster.v2.pdf".fileNameHeadAndTail())
    }

    @Test
    fun aFileNameWithoutABreakIsAllTail() {
        assertEquals("" to "Rosterweek43.pdf", "Rosterweek43.pdf".fileNameHeadAndTail())
        assertEquals("" to "README", "README".fileNameHeadAndTail())
        assertEquals("" to "-draft.pdf", "-draft.pdf".fileNameHeadAndTail())
        assertEquals("" to ".env", ".env".fileNameHeadAndTail())
    }

    @Test
    fun anAddressWrapsOnlyAfterItsAtSignByDefault() {
        assertEquals(
            "alex\u2060.\u2060rivera\u2060@\u200Bexample\u2060.\u2060org",
            "alex.rivera@example.org".asAddressDisplay(),
        )
    }

    @Test
    fun aDomainWiderThanTheLineAlsoWrapsAfterItsDots() {
        assertEquals(
            "jo\u2060@\u200Bmail\u2060.\u200Bnorth\u2060-\u2060region\u2060.\u200Bexample",
            "jo@mail.north-region.example".asAddressDisplay(AddressBreaks(domainDots = true)),
        )
    }

    @Test
    fun aLocalPartWiderThanTheLineAlsoWrapsAfterItsSeparators() {
        val shown = "alexandria.rivera-montgomery@example.com"
            .asAddressDisplay(AddressBreaks(localPart = true))
        assertEquals(
            "alexandria\u2060.\u200Brivera\u2060-\u200Bmontgomery\u2060@\u200Bexample\u2060.\u2060com",
            shown,
        )
        // Every break point follows a separator, so no line can start with "." or "-".
        shown.indices.filter { shown[it] == '\u200B' }.forEach { index ->
            assertTrue(shown[index - 1] in ".-@", "break after '${shown[index - 1]}'")
        }
    }

    @Test
    fun theVisibleCharactersAreTheAddress() {
        val address = "a.b-c_d+e@x-y.example.co.uk"
        val shown = address.asAddressDisplay(AddressBreaks(domainDots = true, localPart = true))
        assertEquals(address, shown.filterNot { it == '\u2060' || it == '\u200B' })
    }

    @Test
    fun aHandleWithoutAnAtSignIsHeldTogether() {
        assertEquals("ops\u2060-\u2060team", "ops-team".asAddressDisplay())
        assertEquals("ops\u2060-\u200Bteam", "ops-team".asAddressDisplay(AddressBreaks(localPart = true)))
    }

    @Test
    fun breakPointsAreAddedOnlyForThePartThatDoesNotFit() {
        assertEquals(AddressBreaks(), addressBreaksFor(localWidth = 100, domainWidth = 120, maxWidth = 296))
        assertEquals(
            AddressBreaks(domainDots = true),
            addressBreaksFor(localWidth = 100, domainWidth = 300, maxWidth = 296),
        )
        assertEquals(
            AddressBreaks(localPart = true),
            addressBreaksFor(localWidth = 400, domainWidth = 200, maxWidth = 296),
        )
        assertEquals("alex.rivera@" to "example.org", "alex.rivera@example.org".addressParts())
        assertEquals("ops-team" to "", "ops-team".addressParts())
    }
}
