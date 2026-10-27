package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.apiCallOrNull
import com.waivera.waivera.core.network.error.apiCallUnit
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.testing.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit

class ApiCallTest {
    private val server = MockWebServer()
    private lateinit var chat: ChatApi
    private lateinit var auth: AuthenticationApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor({ "token" }, server.url("/"))).build())
            .addConverterFactory(WaiveraConverterFactory())
            .build()
        chat = retrofit.create(ChatApi::class.java)
        auth = retrofit.create(AuthenticationApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun decodesBareArraysAndSendsQueryParameters() = runTest {
        server.enqueue(json(Fixtures.text("rest-chat-get-messages-pagination.json")))

        val page = apiCall { chat.getMessagesPage(7, 50, beforeMessageId = 9400) }

        assertEquals(listOf(9310L, 9311L, 9312L), page.map { it.id })
        assertEquals("/api/chat/7/messages/pagination?pageSize=50&beforeMessageId=9400", server.takeRequest().target)
    }

    @Test
    fun omitsNullQueryParameters() = runTest {
        server.enqueue(json("[]"))

        apiCall { chat.getMessagesPage(7, 50, beforeMessageId = null) }

        assertEquals("/api/chat/7/messages/pagination?pageSize=50", server.takeRequest().target)
    }

    @Test
    fun noContentDirectoryIsNull() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertNull(apiCallOrNull { auth.getUserNameList() })
    }

    @Test
    fun okWithoutDataIsAFailureForPrivateConversations() = runTest {
        server.enqueue(json("""{"statusCode":200,"message":"OK","success":true}"""))

        val response = apiCall { chat.getPrivateConversation("usr_priya_raman") }

        assertFailsWith<WaiveraError.Unexpected> { response.requireData() }
    }

    @Test
    fun emptyOkBodiesWorkForUnitEndpoints() = runTest {
        server.enqueue(MockResponse.Builder().code(200).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        apiCallUnit { chat.markAsRead(7) }
        apiCallUnit { chat.markAllActivityRead() }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun httpFailuresBecomeTypedErrors() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("\"This channel is archived.\"").build())

        assertFailsWith<WaiveraError.ChannelArchived> {
            apiCall { chat.getConversation(41) }
        }
    }

    @Test
    fun networkFailuresBecomeNetworkErrors() = runTest {
        server.close()

        assertFailsWith<WaiveraError.Network> { apiCall { chat.getConversation(41) } }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()
}
