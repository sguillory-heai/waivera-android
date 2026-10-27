package com.waivera.waivera.core.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals

class NoBreakTest {
    @Test
    fun hyphensInIdsNeverBreak() {
        assertEquals("WAIV‑2527", "WAIV-2527".withNonBreakingHyphens())
        assertEquals("4821", "4821".withNonBreakingHyphens())
    }
}
