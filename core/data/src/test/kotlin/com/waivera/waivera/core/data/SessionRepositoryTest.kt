package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.auth.AuthUser
import com.waivera.waivera.core.data.live.LiveSessionRepository
import com.waivera.waivera.core.data.live.SessionEnder
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.outbox.OutboxItem
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.SessionRejections
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.error.TokenUnavailableException
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.FakeAuthGateway
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.core.testing.eventually
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SessionRepositoryTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val calls = mutableListOf<String>()
    private val rejections = SessionRejections()

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
        dataCleaners: Set<SignOutCleaner> = emptySet(),
        authApi: AuthenticationApi = graph.authApi,
        secureStore: SecureStore = graph.secureStore,
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
        val ender = SessionEnder(realtime, { push }, setOf(cleaner) + dataCleaners, graph.secureStore) {
            calls += "revoke-token"
        }
        return LiveSessionRepository(
            gateway,
            authApi,
            secureStore,
            realtime,
            ender,
            config,
            rejections,
            testScope.backgroundScope,
        )
    }

    /** The real Room and outbox cleaners, as Hilt contributes them. */
    private val roomAndOutbox: Set<SignOutCleaner> = setOf(
        SignOutCleaner { graph.cache.clear() },
        SignOutCleaner { graph.outbox.clear(graph.outboxDir) },
    )

    private suspend fun leaveUserData() {
        graph.cache.putMessages(listOf(Message(id = 9310, conversationId = 7, text = "private")))
        graph.outbox.put(
            OutboxItem(
                clientMessageId = "queued-by-alex",
                conversationId = 7,
                authorId = "usr_alex_rivera",
                text = "queued",
                createdAt = graph.clock.now,
            ),
        )
    }

    private suspend fun assertUserDataGone() {
        assertNull(graph.cache.message(9310), "cached messages are wiped")
        assertTrue(graph.db.outbox.rows.value.isEmpty(), "the outbox is wiped")
        assertNull(graph.secureStore.values[SecureStore.CACHED_SESSION])
        assertNull(graph.secureStore.values[SecureStore.FCM_TOKEN], "the FCM token is revoked and forgotten")
        assertTrue("unregister" in calls && "revoke-token" in calls, "push is unregistered and revoked: $calls")
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
    fun aKeystoreFailureCachingTheSessionStillSignsIn() = testScope.runTest {
        serveLogin()
        val failingCache = object : SecureStore by graph.secureStore {
            override suspend fun put(key: String, value: String?) {
                if (key == SecureStore.CACHED_SESSION) throw java.security.GeneralSecurityException("keystore")
                graph.secureStore.put(key, value)
            }
        }
        val repo = repository(FakeAuthGateway(), secureStore = failingCache)
        runCurrent()

        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()

        assertIs<SessionState.SignedIn>(repo.state.value)
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
    fun aTokenThatCannotBeFetchedKeepsTheCachedSessionInsteadOfSigningOut() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        repository(gateway).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        calls.clear()

        // IS-02: offline or rate limited, Firebase cannot hand out a token on the next start.
        val noToken = graph.server.retrofit { throw TokenUnavailableException() }.create(AuthenticationApi::class.java)
        val restarted = repository(FakeAuthGateway(initialUser = gateway.user.value), authApi = noToken)
        eventually { restarted.state.value !is SessionState.Initializing }
        assertTrue(assertIs<SessionState.SignedIn>(restarted.state.value).isOffline)
        assertEquals(emptyList(), calls, "nothing was wiped")
        assertEquals(0, gateway.signOuts)
    }

    @Test
    fun aRejectedCallEndsTheSessionOnlyWhenTheBootstrapIsRejectedToo() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        calls.clear()

        // One endpoint answering 401 while the bootstrap still works changes nothing.
        val logins = graph.server.requestsTo("/api/Authentication/login").size
        rejections.report()
        eventually { graph.server.requestsTo("/api/Authentication/login").size > logins }
        runCurrent()
        assertIs<SessionState.SignedIn>(repo.state.value)
        assertEquals(emptyList(), calls)

        // The account was disabled in sprout_api: the bootstrap is rejected as well (IF-05).
        graph.server.status("GET /api/Authentication/login", 401)
        advanceTimeBy(60_001)
        rejections.report()
        eventually { repo.state.value is SessionState.SignedOut }
        assertEquals(listOf("realtime-stop", "unregister", "clear", "revoke-token"), calls)
        assertEquals(1, gateway.signOuts)
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
        assertEquals(listOf("realtime-stop", "unregister", "clear", "revoke-token"), calls)
        assertEquals(1, gateway.signOuts)
        assertTrue(assertIs<SessionState.SignedOut>(repo.state.value).byUser, "sign-in confirms the sign-out")
        assertEquals("device-1", graph.secureStore.values[SecureStore.DEVICE_ID], "the device id survives")
        assertNull(graph.secureStore.values[SecureStore.CACHED_SESSION])
    }

    /** sprout_api's `change-password`: answers [code] and, on 200, takes the new password like Firebase would. */
    private fun serveChangePassword(gateway: FakeAuthGateway, code: Int = 200) {
        graph.server.route("POST /api/Authentication/change-password") { request ->
            if (code == 200) {
                val body = WaiveraJson.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
                gateway.accounts["alex.rivera@example.com"] = body.getValue("newPassword").jsonPrimitive.content
            }
            FixtureServer.json(if (code == 200) """{"data":null,"statusCode":200}""" else "", code)
        }
    }

    @Test
    fun voluntaryPasswordChangeStaysSignedInWithoutALoadingState() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        serveChangePassword(gateway)
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        val states = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.state.toList(states) }

        repo.changePassword("correct-horse", "Battery_staple9!").getOrThrow()
        assertEquals("Battery_staple9!", gateway.accounts["alex.rivera@example.com"])
        assertTrue(states.all { it is SessionState.SignedIn }, "no Initializing flash: $states")
        assertEquals(2, graph.server.requestsTo("/api/Authentication/login").size, "re-bootstrapped quietly")
    }

    @Test
    fun passwordChangeSendsAFreshTokenAndTheNewPasswordToTheServer() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val gateway = FakeAuthGateway()
        serveChangePassword(gateway)
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        serveLogin()

        repo.changePassword("correct-horse", "Battery_staple9!").getOrThrow()

        val sent = graph.server.requestsTo("/api/Authentication/change-password").single()
        val body = WaiveraJson.parseToJsonElement(sent.body?.utf8().orEmpty()).jsonObject
        assertEquals("reauthenticated-token-1", body.getValue("idToken").jsonPrimitive.content)
        assertEquals("Battery_staple9!", body.getValue("newPassword").jsonPrimitive.content)
        assertEquals(listOf("correct-horse"), gateway.reauthentications, "the current password is checked first")
        assertTrue(graph.server.requestsTo("/api/Authentication/confirm-password-change").isEmpty())
        assertIs<SessionState.SignedIn>(repo.state.value, "the gate lifts once the server stamped the change")
    }

    @Test
    fun aWrongCurrentPasswordNeverReachesTheServer() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val gateway = FakeAuthGateway()
        serveChangePassword(gateway)
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()

        assertIs<WaiveraError.InvalidCredentials>(repo.changePassword("wrong", "Battery_staple9!").exceptionOrNull())
        assertTrue(graph.server.requestsTo("/api/Authentication/change-password").isEmpty())
        assertIs<SessionState.PasswordChangeRequired>(repo.state.value)
    }

    @Test
    fun aRefusedRequestAndAStaleSignInGetTheirOwnErrors() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()

        serveChangePassword(gateway, code = 400)
        assertIs<WaiveraError.Validation>(repo.changePassword("correct-horse", "Battery_staple9!").exceptionOrNull())
        serveChangePassword(gateway, code = 401)
        assertIs<WaiveraError.RecentLoginRequired>(
            repo.changePassword("correct-horse", "Battery_staple9!").exceptionOrNull(),
        )
        assertEquals("correct-horse", gateway.accounts["alex.rivera@example.com"])
        assertIs<SessionState.PasswordChangeRequired>(repo.state.value, "a 401 here is not a revoked session")
    }

    @Test
    fun aServerErrorAfterFirebaseTookThePasswordIsFinishedInTheSameTap() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        // The server set the Firebase password, then failed before stamping the date.
        val codes = ArrayDeque(listOf(500, 200))
        graph.server.route("POST /api/Authentication/change-password") {
            gateway.accounts["alex.rivera@example.com"] = "Battery_staple9!"
            FixtureServer.json("", codes.removeFirst())
        }

        repo.changePassword("correct-horse", "Battery_staple9!").getOrThrow()
        assertEquals(2, graph.server.requestsTo("/api/Authentication/change-password").size, "sent again to stamp it")
        assertEquals(listOf("correct-horse", "Battery_staple9!"), gateway.reauthentications)
        assertIs<SessionState.SignedIn>(repo.state.value)
    }

    @Test
    fun aRetryAfterALostAnswerFinishesWithTheNewPassword() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val gateway = FakeAuthGateway()
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        // The server changed the password, but neither answer arrived.
        val codes = ArrayDeque(listOf(503, 503, 200))
        graph.server.route("POST /api/Authentication/change-password") {
            gateway.accounts["alex.rivera@example.com"] = "Battery_staple9!"
            FixtureServer.json("", codes.removeFirst())
        }
        assertIs<WaiveraError.Server>(repo.changePassword("correct-horse", "Battery_staple9!").exceptionOrNull())

        // The old password is refused now; the same new password proves the change and sends it again.
        serveLogin()
        repo.changePassword("correct-horse", "Battery_staple9!").getOrThrow()
        assertIs<SessionState.SignedIn>(repo.state.value)
        assertEquals(3, graph.server.requestsTo("/api/Authentication/change-password").size)

        // A different new password is a new change, and the wrong current password says so.
        assertIs<WaiveraError.InvalidCredentials>(
            repo.changePassword("correct-horse", "Another_one99!").exceptionOrNull(),
        )
    }

    @Test
    fun aRefusedCurrentPasswordNeverTriesAnUnrelatedNewOne() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        serveChangePassword(gateway, code = 400)
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        assertTrue(repo.changePassword("correct-horse", "Battery_staple9!").isFailure, "a refusal forgets the try")

        assertIs<WaiveraError.InvalidCredentials>(repo.changePassword("wrong", "Battery_staple9!").exceptionOrNull())
        assertEquals(listOf("correct-horse", "wrong"), gateway.reauthentications)
    }

    @Test
    fun signingBackInFailingEndsTheSessionWithAReason() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        serveChangePassword(gateway)
        val repo = repository(gateway)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        gateway.signInError = WaiveraError.Network()

        assertIs<WaiveraError.PasswordChangedSignInAgain>(
            repo.changePassword("correct-horse", "Battery_staple9!").exceptionOrNull(),
        )
        assertEquals("Battery_staple9!", gateway.accounts["alex.rivera@example.com"])
        assertIs<WaiveraError.PasswordChangedSignInAgain>(assertIs<SessionState.SignedOut>(repo.state.value).reason)
        assertEquals(1, gateway.signOuts)
    }

    @Test
    fun aRejectedBootstrapRunsTheSameCleanupAsSigningOut() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        repository(gateway).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        leaveUserData()
        graph.secureStore.values[SecureStore.FCM_TOKEN] = "fcm-1"
        calls.clear()

        graph.server.json(
            "GET /api/Authentication/login",
            Fixtures.text("rest-error-apiresponse-unauthorized.json"),
            code = 401,
        )
        val restarted = repository(FakeAuthGateway(initialUser = gateway.user.value), dataCleaners = roomAndOutbox)
        eventually { restarted.state.value is SessionState.SignedOut }
        runCurrent()
        assertIs<WaiveraError.SessionExpired>(assertIs<SessionState.SignedOut>(restarted.state.value).reason)
        assertUserDataGone()
        assertEquals(1, calls.count { it == "clear" }, "the cleanup runs once: $calls")
    }

    @Test
    fun firebaseDroppingTheUserRunsTheSameCleanup() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        val repo = repository(gateway, dataCleaners = roomAndOutbox)
        repo.signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        leaveUserData()
        graph.secureStore.values[SecureStore.FCM_TOKEN] = "fcm-1"
        calls.clear()

        gateway.signOut() // a revoked or disabled account: the auth-state listener reports null
        runCurrent()
        assertFalse(assertIs<SessionState.SignedOut>(repo.state.value).byUser)
        assertEquals("realtime-stop", calls.first())
        assertUserDataGone()
    }

    @Test
    fun aSessionThatEndedWhileTheAppWasDeadIsCleanedOnTheNextStart() = testScope.runTest {
        serveLogin()
        repository(FakeAuthGateway()).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        leaveUserData()
        calls.clear()

        val restarted = repository(FakeAuthGateway(initialUser = null), dataCleaners = roomAndOutbox)
        runCurrent()
        assertIs<SessionState.SignedOut>(restarted.state.value)
        assertUserDataGone()

        calls.clear()
        repository(FakeAuthGateway(initialUser = null), dataCleaners = roomAndOutbox)
        runCurrent()
        assertTrue(calls.isEmpty(), "nothing left to clean on the start after that: $calls")
    }

    @Test
    fun anotherAccountTakingOverTheDeviceClearsThePreviousAccountsData() = testScope.runTest {
        serveLogin()
        repository(FakeAuthGateway()).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()
        leaveUserData()
        calls.clear()

        val other = repository(
            FakeAuthGateway(initialUser = AuthUser("firebase-uid-someone-else", "sam@example.com")),
            dataCleaners = roomAndOutbox,
        )
        eventually { other.state.value is SessionState.SignedIn }
        assertNull(graph.cache.message(9310), "the previous account's messages are gone")
        assertTrue(graph.db.outbox.rows.value.isEmpty(), "and so is its outbox")
    }

    @Test
    fun anOfflineStartKeepsThePasswordChangeGate() = testScope.runTest {
        serveLogin("rest-auth-login-requires-password-change.json")
        val gateway = FakeAuthGateway()
        repository(gateway).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()

        graph.server.status("GET /api/Authentication/login", 503)
        val restarted = repository(FakeAuthGateway(initialUser = gateway.user.value))
        eventually { restarted.state.value !is SessionState.Initializing }
        assertIs<SessionState.PasswordChangeRequired>(restarted.state.value, "airplane mode is no way around it")
    }

    @Test
    fun aSlowColdStartAnswersTheUserIdFromTheCachedSession() = testScope.runTest {
        serveLogin()
        val gateway = FakeAuthGateway()
        repository(gateway).signIn("alex.rivera@example.com", "correct-horse").getOrThrow()

        val release = java.util.concurrent.CountDownLatch(1)
        graph.server.route("GET /api/Authentication/login") {
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            com.waivera.waivera.core.testing.FixtureServer.json(Fixtures.text("rest-auth-login.json"))
        }
        val restarted = repository(FakeAuthGateway(initialUser = gateway.user.value))
        runCurrent()
        assertIs<SessionState.Initializing>(restarted.state.value)
        assertEquals("usr_alex_rivera", restarted.settledUserId(timeoutMillis = 1_000))
        release.countDown()
    }

    @Test
    fun settledUserIdIsNullWhenSignedOut() = testScope.runTest {
        val repo = repository(FakeAuthGateway())
        assertNull(repo.settledUserId(timeoutMillis = 1_000))
    }

    @Test
    fun pushesAreAcceptedOnlyForASignedInOrStillStartingSession() = testScope.runTest {
        val session = com.waivera.waivera.core.testing.TestSessionRepository()
        assertTrue(session.acceptsPushes(timeoutMillis = 1_000))
        session.mutableState.value = SessionState.SignedOut()
        assertTrue(!session.acceptsPushes(timeoutMillis = 1_000), "signed out: the push is dropped")
        session.mutableState.value = SessionState.BootstrapFailed(WaiveraError.Network())
        assertTrue(!session.acceptsPushes(timeoutMillis = 1_000))
        session.mutableState.value = SessionState.Initializing
        assertTrue(session.acceptsPushes(timeoutMillis = 1_000), "a cold start from the push still shows it")
    }
}
