package com.waivera.waivera.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkConfigTest {
    @Test
    fun hubUrlAlwaysIncludesTheApiSegment() {
        val config = NetworkConfig("https://api.example.com///", "")
        assertEquals("https://api.example.com/api/chatHub", config.chatHubUrl)
        assertEquals(
            "https://api.example.com/api/chat/message/9320/attachment",
            config.resolveApiUrl("/api/chat/message/9320/attachment").toString(),
        )
    }

    @Test
    fun missingOrCleartextBaseIsUnconfigured() {
        assertFalse(NetworkConfig("", "").isConfigured)
        assertFalse(NetworkConfig("http://api.example.com", "").isConfigured)
        assertEquals(listOf("waivera.apiBaseUrl"), NetworkConfig("", "").missingKeys)
        assertNull(NetworkConfig("", "").chatHubUrl)
    }

    @Test
    fun localCleartextIsAcceptedOnlyWhenTheBuildAllowsIt() {
        assertFalse(NetworkConfig("http://10.0.2.2:5000", "").isConfigured, "release builds never accept http")
        assertFalse(NetworkConfig("https://api.example.com", "http://localhost:3000").webBase != null)
        assertTrue(NetworkConfig("http://10.0.2.2:5000", "", allowLocalCleartext = true).isConfigured)
        assertTrue(NetworkConfig("http://localhost:5000", "", allowLocalCleartext = true).isConfigured)
        assertFalse(NetworkConfig("http://api.example.com", "", allowLocalCleartext = true).isConfigured)
    }
}
