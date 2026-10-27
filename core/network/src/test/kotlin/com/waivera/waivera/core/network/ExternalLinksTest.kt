package com.waivera.waivera.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalLinksTest {
    @Test
    fun onlyWebLinksWithAHostCanLeaveTheApp() {
        assertTrue(ExternalLinks.isOpenable("https://app.example.test/lobby/11"))
        assertTrue(ExternalLinks.isOpenable("  HTTP://example.com/path?q=1  "))
        listOf(
            null,
            "",
            "intent://scan/#Intent;scheme=zxing;package=com.example;end",
            "content://com.waivera.waivera.feature.chat.files/share/a.pdf",
            "file:///data/data/com.waivera.waivera/databases/waivera.db",
            "javascript:alert(1)",
            "market://details?id=com.example",
            "waivera://chat/7",
            "mailto:priya.raman@example.com",
            "https:///no-host",
            "https://exa mple.com",
        ).forEach { assertFalse(ExternalLinks.isOpenable(it), "$it must not open") }
    }
}
