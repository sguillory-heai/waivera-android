package com.waivera.waivera.feature.auth

import com.waivera.waivera.core.network.NetworkConfig
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** The legal pages hang off the configured web base, whatever form it was written in. */
class LegalLinksTest {
    private fun links(webBase: String) =
        LegalLinks.from(NetworkConfig(apiBaseUrl = "https://api.example.test", webBaseUrl = webBase).webBase)

    @Test
    fun `routes sit at the web root`() {
        val links = links("https://app.example.test")
        assertEquals("https://app.example.test/terms-of-service", links?.termsOfService)
        assertEquals("https://app.example.test/privacy-policy", links?.privacyPolicy)
    }

    @Test
    fun `a trailing slash makes no double slash`() {
        assertEquals("https://app.example.test/privacy-policy", links("https://app.example.test/")?.privacyPolicy)
    }

    @Test
    fun `a base with a path keeps it`() {
        assertEquals("https://example.test/web/terms-of-service", links("https://example.test/web")?.termsOfService)
    }

    @Test
    fun `no web base, or one that is not https, gives no links`() {
        assertNull(links(""))
        assertNull(links("http://app.example.test"))
        assertNull(LegalLinks.from(null))
    }
}
