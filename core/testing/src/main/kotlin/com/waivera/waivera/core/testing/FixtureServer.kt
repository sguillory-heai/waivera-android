package com.waivera.waivera.core.testing

import com.waivera.waivera.core.network.AuthTokenInterceptor
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.WaiveraConverterFactory
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * A fake sprout_api over MockWebServer. Routes match `"METHOD /path"` exactly (query ignored)
 * or by prefix when the key ends with `*`. Unrouted requests get 404, so a test notices a call
 * it did not expect. Every request is recorded in [requests].
 */
class FixtureServer : AutoCloseable {
    private val server = MockWebServer()
    private val routes = linkedMapOf<String, (RecordedRequest) -> MockResponse>()
    val requests = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val key = "${request.method} ${request.url.encodedPath}"
                val handler = synchronized(routes) {
                    routes[key]
                        ?: routes.entries.firstOrNull { (route, _) ->
                            route.endsWith("*") &&
                                key.startsWith(route.dropLast(1))
                        }?.value
                }
                return handler?.invoke(request) ?: MockResponse.Builder().code(NOT_FOUND).build()
            }
        }
        server.start()
    }

    val baseUrl: String get() = server.url("/").toString()

    val config: NetworkConfig get() = NetworkConfig(
        apiBaseUrl = baseUrl.trimEnd('/'),
        webBaseUrl = "https://app.example.test",
    )

    fun route(key: String, handler: (RecordedRequest) -> MockResponse) {
        synchronized(routes) { routes[key] = handler }
    }

    fun json(key: String, body: String, code: Int = OK) = route(key) { json(body, code) }

    fun fixture(key: String, name: String) = json(key, Fixtures.text(name))

    fun status(key: String, code: Int, body: String = "") = route(key) { json(body, code) }

    fun requestsTo(path: String): List<RecordedRequest> = requests.filter { it.url.encodedPath == path }

    fun retrofit(token: () -> String? = { "test-token" }): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor({ token() }, server.url("/"))).build())
        .addConverterFactory(WaiveraConverterFactory())
        .build()

    inline fun <reified T> service(): T = retrofit().create(T::class.java)

    override fun close() = server.close()

    companion object {
        const val OK = 200
        const val NOT_FOUND = 404

        fun json(body: String, code: Int = OK): MockResponse =
            MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()
    }
}
