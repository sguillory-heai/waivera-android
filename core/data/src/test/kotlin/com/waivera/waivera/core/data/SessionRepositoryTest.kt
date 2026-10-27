package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.auth.AuthUser
import com.waivera.waivera.core.data.live.LiveSessionRepository
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.FakeAuthGateway
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.core.testing.eventually
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SessionRepositoryTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val calls = mutableListOf<String>()

    @AfterTest
    fun tearDown() = graph.close()

    private fun serveLogin(login: String = "rest-auth-login.json") {
        graph.server.fixture("GET /api/Authentication/login", login)
        graph.server.fixture(
            "GET /api/Authentication/GetUserDetailsByUserID/usr_alex_rivera",
            "rest-auth-get-user-details.json",
        )
    }

    private fun repository(
        gateway: FakeAuthGateway,
        config: NetworkConfig = graph.server.config,
    ): LiveSessionRepository {
        val push = object : PushRegistrationRepository {
            override suspend fun register(
                token: String,
                preferences: com.waivera.waivera.core.data.model.NotificationPreferences,
            ) = Result.success(Unit)

            override suspend fun unregister(): Result<Unit> {
                calls += "unregister"
                return Result.success(Unit)
            }

            override suspend fun onNewToken(token: String) = Unit
        }
        val cleaner = SignOutCleaner { calls += "clear" }
        val realtime = object : com.waivera.waivera.core.realtime.RealtimeClient by graph.realtime {
            override suspend fun stop() {
                calls += "realtime-stop"
                graph.realtime.stop()
            }
        }
        return LiveSessionRepository(gateway, graph.authApi, graph.secureStore, realtime, {
            push
        }, setOf(cleaner), config, testScope.backgroundScope)
    }

    @Test
    fun missingConfigurationIsReportedNotThrown() = testScope.runTest {
        val repo = repository(FakeAuthGateway(missingKeys = listOf("waivera.firebase.apiKey")), NetworkConfig("", ""))
        val state = assertIs<SessionState.ConfigurationMissing>(repo.state.value)
        assertTrue("waivera.firebase.apiKey" in state.missingKeys && "waivera.apiBaseUrl" in state.missingKeys)
        assertIs<WaiveraError.Configuration>(repo.signIn("a", "b").exceptionOrNull())
    }

    @Test
    fun signInBootstrapsTheBackendIdentity() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        runCurrent()
        assertIs<SessionState.SignedOut>(repo.state.value)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        val signedIn = assertIs<SessionState.SignedIn>(repo.state.value)
        assertEquals("usr_alex_rivera", signedIn.user.userId, "the backend id, never the Firebase uid")
        assertEquals("usr_alex_rivera", graph.realtime.startedFor)
        assertTrue(
            graph.server.requestsTo("/api/Authentication/login").single().headers["Authorization"] ==
                "Bearer test-token",
        )
    }

    @Test
    fun wrongPasswordStaysSignedOut() = testScope.runTest {
        val repo = repository(FakeAuthGateway())
        runCurrent()
        assertIs<WaiveraError.InvalidCredentials>(repo.signIn("alex.rivera@example.com", "nope").exceptionOrNull())
        assertIs<SessionState.SignedOut>(repo.state.value)
    }

    @Test
    fun passwordChangeRequiredIsItsOwnState() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val repo = repository(FakeAuthGateway())
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        assertIs<SessionState.PasswordChangeRequired>(repo.state.value)
    }

    @Test
    fun rejectedBootstrapSignsOutWithAReason() = testScope.runTest {
        graph.server.json(
            "GET /api/Authentication/login",
            Fixtures.text("rest-error-apiresponse-unauthorized.json"),
            code = 401,
        )
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        assertTrue(repo.signIn("alex.rivera@example.com", "correct-horse").isFailure)
        val out = assertIs<SessionState.SignedOut>(repo.state.value)
        assertIs<WaiveraError.SessionExpired>(out.reason)
        assertEquals(1, gateway.signOuts)
        assertEquals(
            2,
            graph.server.requestsTo("/api/Authentication/login").size,
            "401 is retried once with a refreshed token first",
        )
    }

    @Test
    fun offlineStartUsesTheCachedSession() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        repository(gateway).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        val cached = graph.secureStore.values[SecureStore.CACHED_SESSION]
        assertTrue(cached != null)

        graph.server.status("GET /api/Authentication/login", 503)
        val restarted = repository(FakeAuthGateway(initialUser = gateway.user.value))
        eventually { restarted.state.value !is SessionState.Initializing }
        val state = assertIs<SessionState.SignedIn>(restarted.state.value)
        assertTrue(state.isOffline)
    }

    @Test
    fun offlineWithoutCacheIsABootstrapFailure() = testScope.runTest {
        graph.server.status("GET /api/Authentication/login", 503)
        val repo = repository(FakeAuthGateway(initialUser = AuthUser("uid-1", "alex.rivera@example.com")))
        eventually { repo.state.value !is SessionState.Initializing }
        assertIs<WaiveraError.Server>(assertIs<SessionState.BootstrapFailed>(repo.state.value).error)
    }

    @Test
    fun signOutStopsRealtimeUnregistersPushClearsThenSignsOut() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        graph.secureStore.values[SecureStore.DEVICE_ID] = "device-1"
        repo.signOut().getOrThrow()
        assertEquals(listOf("realtime-stop", "unregister", "clear"), calls)
        assertEquals(1, gateway.signOuts)
        assertIs<SessionState.SignedOut>(repo.state.value)
        assertEquals("device-1", graph.secureStore.values[SecureStore.DEVICE_ID], "the device id survives")
        assertNull(graph.secureStore.values[SecureStore.CACHED_SESSION])
    }

    @Test
    fun voluntaryPasswordChangeStaysSignedInWithoutALoadingState() = testScope.runTest {
        serveLogin()
        graph.server.status("POST /api/Authentication/confirm-password-change", 204)
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        val states = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.state.toList(states) }

        repo.changePassword("correct-horse", "battery-staple").getOrThrow()
        assertEquals("battery-staple", gateway.accounts["alex.rivera@example.com"])
        assertTrue(states.all { it is SessionState.SignedIn }, "no Initializing flash: $states")
        assertEquals(2, graph.server.requestsTo("/api/Authentication/login").size, "re-bootstrapped quietly")
    }

    @Test
    fun passwordChangeResumesAtTheBackendConfirmAfterAFailure() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        graph.server.status("POST /api/Authentication/confirm-password-change", 503)
        assertTrue(repo.changePassword("correct-horse", "battery-staple").isFailure)
        assertEquals("battery-staple", gateway.accounts["alex.rivera@example.com"], "Firebase already changed")

        // Retrying with the same new password skips Firebase; the old password would now be rejected there.
        serveLogin()
        graph.server.status("POST /api/Authentication/confirm-password-change", 204)
        repo.changePassword("correct-horse", "battery-staple").getOrThrow()
        assertIs<SessionState.SignedIn>(repo.state.value)
        assertEquals(2, graph.server.requestsTo("/api/Authentication/confirm-password-change").size)

        // A different new password is a new change and goes through Firebase again.
        graph.server.status("POST /api/Authentication/confirm-password-change", 503)
        assertIs<WaiveraError.InvalidCredentials>(
            repo.changePassword("correct-horse", "another-one").exceptionOrNull(),
        )
    }
}
