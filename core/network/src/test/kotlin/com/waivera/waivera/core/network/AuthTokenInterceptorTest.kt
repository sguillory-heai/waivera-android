package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.error.NotSignedInException
import com.waivera.waivera.core.network.error.TokenUnavailableException
import com.waivera.waivera.core.network.error.WaiveraError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
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
    fun aSecond401IsReturnedWithoutAThirdAttempt() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).build())
        val client = client { forceRefresh -> if (forceRefresh) "fresh" else "stale" }

        val response = client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()

        assertEquals(401, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun onlyA401WithTheRefreshedTokenIsReportedAsARejection() {
        var rejected = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor({ if (it) "fresh" else "stale" }, server.url("/")) { rejected++ })
            .build()
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(200).build())
        client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute().close()
        assertEquals(0, rejected, "a refresh that works is not a rejection")

        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).build())
        client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute().close()
        assertEquals(1, rejected)
    }

    @Test
    fun anUnexpectedTokenFailureIsAnIoExceptionSoOkHttpCannotCrashTheApp() {
        // OkHttp rethrows non-IO exceptions on its dispatcher thread, which kills the process.
        val client = client { throw IllegalStateException("Firebase app was deleted") }

        assertFailsWith<TokenUnavailableException> {
            client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun failsLocallyWithoutANetworkRequestWhenSignedOut() {
        val client = client { null }

        assertFailsWith<NotSignedInException> {
            client.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aTokenThatCannotBeFetchedIsANetworkFailureNotASignOut() {
        server.enqueue(MockResponse.Builder().code(401).build())
        val offline = client { throw TokenUnavailableException() }
        val first = assertFailsWith<TokenUnavailableException> {
            offline.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()
        }
        assertIs<WaiveraError.Network>(WaiveraError.from(first), "transient, so nothing signs out (IS-02)")
        assertEquals(0, server.requestCount)

        // The refresh after a 401 failing the same way is not a sign-out either.
        val refreshFails = client { forceRefresh -> if (forceRefresh) throw TokenUnavailableException() else "stale" }
        assertFailsWith<TokenUnavailableException> {
            refreshFails.newCall(Request.Builder().url(server.url("/api/x")).build()).execute()
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun neverSendsTheTokenToAnotherHost() {
        server.enqueue(MockResponse.Builder().code(200).build())
        val otherBase = server.url("/").newBuilder().host("api.example.invalid").build()
        val client = OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor({ "secret" }, otherBase)).build()

        client.newCall(Request.Builder().url(server.url("/cdn/image.png")).build()).execute()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun doesNotReplayOneShotBodies() {
        server.enqueue(MockResponse.Builder().code(401).build())
        val client = client { forceRefresh -> if (forceRefresh) "fresh" else "stale" }
        val oneShot = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("bytes")
            }
        }

        val response = client.newCall(Request.Builder().url(server.url("/api/upload")).post(oneShot).build()).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun keepsAnExplicitAuthorizationHeaderEvenWhenSignedOut() {
        server.enqueue(MockResponse.Builder().code(200).build())
        val client = client { null }

        val request = Request.Builder().url(server.url("/api/mobile-push/unregister"))
            .header("Authorization", "Bearer captured")
            .build()
        client.newCall(request).execute()

        assertEquals("Bearer captured", server.takeRequest().headers["Authorization"])
    }

    private fun client(tokens: AuthTokenProvider) =
        OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor(tokens, server.url("/"))).build()
}
