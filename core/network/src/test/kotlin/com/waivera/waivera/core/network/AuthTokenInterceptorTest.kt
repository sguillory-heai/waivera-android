package com.waivera.waivera.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before

class AuthTokenInterceptorTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun retriesOnceWithARefreshedTokenAfterA401() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(200).build())
        val client = client { forceRefresh -> if (forceRefresh) "fresh" else "stale" }

        val response = client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()

        assertEquals(200, response.code)
        assertEquals("Bearer stale", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer fresh", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun sendsNoHeaderAndDoesNotRetryWhenSignedOut() {
        server.enqueue(MockResponse.Builder().code(401).build())
        val client = client { null }

        val response = client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()

        assertEquals(401, response.code)
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals(1, server.requestCount)
    }

    private fun client(tokens: AuthTokenProvider) =
        OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor(tokens)).build()
}
