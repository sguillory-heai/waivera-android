package com.waivera.waivera.navtour

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.waivera.waivera.MainActivity
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris
import com.waivera.waivera.core.notifications.payload.PushPayloadParser
import com.waivera.waivera.core.notifications.presentation.PresentationDecision
import com.waivera.waivera.core.screenshottesting.ScreenshotMatrix
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.AppRoute
import com.waivera.waivera.navigation.AppShellViewModel
import com.waivera.waivera.navigation.NavState
import com.waivera.waivera.navigation.ShellState
import com.waivera.waivera.navigation.TopLevelTab
import com.waivera.waivera.navigation.contentKey
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app (MainActivity, the real shell, Hilt graph and feature screens) on the
 * demo data layer, one navigation path per test. Every step asserts where the app is, by the
 * shell's back stack and by what is on screen, and writes a Roborazzi image named
 * `path_<NN>_<flow>_<step>` (see docs/testing/navigation-paths.md).
 *
 * Phone 412×915dp light by default; [shotDark] adds a `_dark` image of the same step and
 * [NavigationPathVariantsTest] runs key steps at 200% font (`_font200`).
 *
 * Subclasses must be annotated `@HiltAndroidTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [ScreenshotSdk.VALUE],
    application = HiltTestApplication::class,
    qualifiers = "w412dp-h915dp-notnight-xxhdpi",
)
@Suppress("TooManyFunctions")
abstract class NavTourHarness {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var launchIntent: Intent? = null
    private val originalZone: TimeZone = TimeZone.getDefault()
    private val originalLocale: Locale = Locale.getDefault()

    protected val app: Application get() = ApplicationProvider.getApplicationContext()

    protected val world: TourWorld by lazy { EntryPointAccessors.fromApplication(app, TourWorld::class.java) }

    /** False for tests that need the Android 13 notification primer. */
    protected open val notificationPermissionGranted: Boolean = true

    @Before
    fun setUpTour() {
        // The demo employee's saved zone, so device-zone bugs show up as wrong times, not passes.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
        Locale.setDefault(Locale.US)
        // "Remove animations": transitions finish at once and infinite animations stop.
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        if (notificationPermissionGranted) {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        hilt.inject()
        // WaiveraApplication does this at start-up; HiltTestApplication does not.
        world.channels().install()
        installImmediateImageLoader()
    }

    /**
     * The app's image loader (avatars through the demo people repository), run on the main
     * thread so a photo is decoded within the frames [settle] advances. Off the main thread a
     * capture could land before the photo and show initials (UI3-06).
     */
    @OptIn(DelicateCoilApi::class)
    private fun installImmediateImageLoader() {
        val loader = world.imageLoader().imageLoader.newBuilder()
            .coroutineContext(Dispatchers.Main.immediate)
            .build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After
    fun tearDownTour() {
        // After a back press that left the app, the activity is finishing but Robolectric never
        // destroys it, and close() would wait forever for DESTROYED. Clearing the flag lets
        // close() finish it again and drive it through onDestroy.
        scenario?.onActivity { activity ->
            if (activity.isFinishing) shadowOf(activity).resetIsFinishing()
            // ActivityScenario follows the lifecycle only of the activity whose intent matches the
            // one it launched; MainActivity.onNewIntent calls setIntent, so put the launch intent back.
            launchIntent?.let { activity.intent = it }
        }
        scenario?.close()
        scenario = null
        launchIntent = null
        TimeZone.setDefault(originalZone)
        Locale.setDefault(originalLocale)
    }

    // Launching and links -----------------------------------------------------------------------

    /** Cold start, optionally from a `waivera://` link (a notification tap while the app is closed). */
    protected fun launch(uri: String? = null) {
        val intent = Intent(app, MainActivity::class.java)
        if (uri != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(uri)
        }
        launch(intent)
    }

    protected fun launch(intent: Intent) {
        check(scenario == null) { "The app is already running in this test" }
        scenario = ActivityScenario.launch(intent)
        launchIntent = onActivity { it.intent }
        settle()
    }

    /** A link delivered to the running app (singleTop: a notification tap while it is open). */
    protected fun openLink(uri: String) {
        deliver(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    protected fun deliver(intent: Intent) {
        onActivity { activity ->
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, intent)
        }
        settle()
    }

    protected fun <T> onActivity(block: (MainActivity) -> T): T {
        val running = checkNotNull(scenario) { "launch() first" }
        var result: Result<T>? = null
        running.onActivity { activity -> result = runCatching { block(activity) } }
        return checkNotNull(result).getOrThrow()
    }

    // Push --------------------------------------------------------------------------------------

    /** One payload from `src/testDebug/resources/push/` (copies of docs/push-test-payloads). */
    protected fun pushFixture(name: String): PushFixture {
        val raw = checkNotNull(javaClass.classLoader?.getResource("push/$name")) { "Missing fixture push/$name" }
            .readText()
        return PushFixture.parse(raw)
    }

    /** Runs the payload through the real handler, as the FCM service does. */
    protected fun receivePush(fixture: PushFixture): PresentationDecision? {
        val decision = runBlocking { world.pushHandler().handle(fixture.data, fixture.title, fixture.body) }
        settle()
        return decision
    }

    /** The tap intent of the newest posted notification, exactly as the system would send it. */
    protected fun latestNotificationTapIntent(): Intent? {
        val manager = app.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(manager).allNotifications
        val tap = posted.lastOrNull()?.contentIntent ?: return null
        return shadowOf(tap).savedIntent
    }

    protected fun postedNotificationCount(): Int =
        shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size

    /** Where a tap on this payload's notification leads, through the real parser and URI codec. */
    protected fun tapUriFor(fixture: PushFixture): String {
        val message = checkNotNull(PushPayloadParser.parse(fixture.data, fixture.title, fixture.body)) {
            "Payload did not parse"
        }
        return DeepLinkUris.toUri(message.target)
    }

    // Shell state -------------------------------------------------------------------------------

    protected val shell: ShellState
        get() = onActivity { activity ->
            ViewModelProvider(activity)[AppShellViewModel::class.java].uiState.value.shell
        }

    protected val nav: NavState get() = shell.nav

    protected fun navSummary(): String = runCatching {
        val state = shell
        "gate=${state.gate}, selected=${state.nav.selected}, " +
            "stack=${state.nav.visibleBackStack.map { it.contentKey }}, top=${state.nav.top}, " +
            "pending=${state.pendingDeepLink}"
    }.getOrElse { "shell unavailable: ${it.message}" }

    // The semantics tree at the moment a wait times out, so a failure says what was on screen instead.
    private fun screenSummary(): String = runCatching {
        compose.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)
    }.getOrElse { "screen unavailable: ${it.message}" }

    protected fun expectGate(gate: AppGate) = waitUntil("gate $gate") { shell.gate == gate }

    /** The visible back stack (Today's stack plus the selected tab's), compared by screen identity. */
    protected fun expectStack(vararg routes: AppRoute) {
        val wanted = routes.map { it.contentKey }
        waitUntil("stack $wanted") {
            shell.gate == AppGate.Main && nav.visibleBackStack.map { it.contentKey } == wanted
        }
    }

    protected fun expectSelected(tab: TopLevelTab) = waitUntil("tab $tab selected") { nav.selected == tab }

    /** The exact route on top, including the highlighted message. */
    protected fun expectTop(route: AppRoute) = waitUntil("top $route") { nav.top == route }

    /** Something with this text or description is on screen (a heading, a row, a button). */
    protected fun expectScreen(text: String, substring: Boolean = false) =
        waitUntil("'$text' on screen") { exists(textOrDescription(text, substring)) }

    protected fun expectGone(text: String, substring: Boolean = false) =
        waitUntil("'$text' gone") { !exists(textOrDescription(text, substring)) }

    // Finding and acting ------------------------------------------------------------------------

    // Ignores case because section headings render their string resource in capitals ("NEEDS YOU").
    protected fun textOrDescription(value: String, substring: Boolean = false): SemanticsMatcher =
        hasText(value, substring = substring, ignoreCase = true) or
            hasContentDescription(value, substring = substring) or
            hasStateDescription(value, substring)

    // A busy button ("Connecting to Waivera…") carries its visible progress label as its state description.
    private fun hasStateDescription(value: String, substring: Boolean): SemanticsMatcher =
        SemanticsMatcher("stateDescription ${if (substring) "contains" else "is"} '$value'") { node ->
            val state = node.config.getOrNull(SemanticsProperties.StateDescription) ?: return@SemanticsMatcher false
            if (substring) value in state else state == value
        }

    protected fun hasClickLabel(label: String): SemanticsMatcher = SemanticsMatcher("click label '$label'") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    protected fun clickable(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    protected fun longClickable(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    protected fun tab(label: String): SemanticsMatcher =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    protected fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Scrolls every lazy or scrollable container until [matcher] is composed. */
    protected fun reveal(matcher: SemanticsMatcher) {
        if (exists(matcher)) return
        waitUntil("$matcher revealed") {
            if (!exists(matcher)) {
                compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
                    runCatching { compose.onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(matcher) }
                }
            }
            exists(matcher)
        }
    }

    protected fun node(matcher: SemanticsMatcher, last: Boolean = false): SemanticsNodeInteraction {
        reveal(matcher)
        val all = compose.onAllNodes(matcher)
        return if (last) all.onLast() else all.onFirst()
    }

    /**
     * Clicks through the node's semantics action, as TalkBack and the test framework do. [last]
     * picks the newest window's match (a sheet's button over a screen's row with the same text).
     */
    protected fun click(matcher: SemanticsMatcher, last: Boolean = false) {
        node(matcher and clickable(), last).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    protected fun click(text: String, last: Boolean = false) = click(textOrDescription(text), last)

    protected fun clickTab(label: String) = click(tab(label))

    protected fun longPress(matcher: SemanticsMatcher) {
        node(matcher and longClickable()).performSemanticsAction(SemanticsActions.OnLongClick)
        settle()
    }

    protected fun typeInto(label: String, text: String) {
        node(hasSetTextAction() and textOrDescription(label)).performTextReplacement(text)
        settle()
    }

    protected fun openAccountMenu() {
        click(hasContentDescription(ACCOUNT_MENU) or hasClickLabel(ACCOUNT_MENU))
        expectScreen(VIEW_PROFILE)
    }

    // Back --------------------------------------------------------------------------------------

    /** The system back gesture or button, through the activity's dispatcher. */
    protected fun systemBack() {
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /**
     * A predictive back swipe from the left edge: started, dragged halfway (captured as
     * `<shotName>`), then committed.
     */
    protected fun predictiveBack(shotName: String? = null) {
        onActivity { activity ->
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(EDGE_X, EDGE_Y, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(EDGE_X * 8, EDGE_Y, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        settle()
        if (shotName != null) shot(shotName)
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** A predictive back swipe that is abandoned: nothing may change. */
    protected fun cancelledPredictiveBack() {
        onActivity { activity ->
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(EDGE_X, EDGE_Y, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(EDGE_X * 4, EDGE_Y, 0.3f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackCancelled()
        }
        settle()
    }

    /**
     * Back at Today's root belongs to the system (app-shell.md "Tabs and back"): the shell must
     * be at its exit point and no in-app callback may still intercept back.
     */
    protected fun assertBackLeavesApp() {
        assertTrue("Not at the exit point: ${navSummary()}", nav.isAtExit)
        val intercepted = onActivity { it.onBackPressedDispatcher.hasEnabledCallbacks() }
        if (intercepted) {
            systemBack()
            val finishing = onActivity { it.isFinishing }
            assertTrue("Back at Today's root was consumed in the app: ${navSummary()}", finishing)
        }
    }

    // Screenshots -------------------------------------------------------------------------------

    @OptIn(ExperimentalRoborazziApi::class)
    protected fun shot(name: String) {
        settle()
        captureScreenRoboImage("${ScreenshotMatrix.outputDirectory}/$name.png")
    }

    /** The same step in the dark theme (`<name>_dark`), then back to the theme it had. */
    protected fun shotDark(name: String) {
        val settings = world.backend().settings
        val previous = settings.value.theme
        settings.value = settings.value.copy(theme = ThemePreference.DARK)
        settle()
        shot("${name}_dark")
        settings.value = settings.value.copy(theme = previous)
        settle()
    }

    // Waiting -----------------------------------------------------------------------------------

    /** Lets the main looper, Compose and the demo repositories' coroutines run to rest. */
    protected fun settle() {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))
            compose.waitForIdle()
        }
    }

    protected fun waitUntil(what: String, timeoutMillis: Long = TIMEOUT_MILLIS, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            compose.waitForIdle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for $what. ${navSummary()}\n${screenSummary()}")
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))
            Thread.sleep(BACKGROUND_SLICE_MILLIS)
        }
    }

    protected fun assertDecision(expected: PresentationDecision?, actual: PresentationDecision?) =
        assertEquals("push decision", expected, actual)

    /** A push as the app receives it: the FCM data map plus the notification block, if any. */
    data class PushFixture(val data: Map<String, String>, val title: String?, val body: String?) {
        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            /** Accepts a bare data map or a whole FCM request body (`message.data`, `message.notification`). */
            fun parse(raw: String): PushFixture {
                val root = json.parseToJsonElement(raw) as JsonObject
                val message = root["message"] as? JsonObject
                val data = (message?.get("data") ?: root["data"] ?: root) as JsonObject
                val notification = (message?.get("notification") ?: root["notification"]) as? JsonObject
                return PushFixture(
                    data = data.mapNotNull { (key, value) -> value.asString()?.let { key to it } }.toMap(),
                    title = notification?.get("title")?.asString(),
                    body = notification?.get("body")?.asString(),
                )
            }

            private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
        }
    }

    protected companion object {
        const val ACCOUNT_MENU = "Account menu"
        const val VIEW_PROFILE = "View profile"
        const val GO_BACK = "Go back"
        private const val TIMEOUT_MILLIS = 10_000L
        private const val FRAME_MILLIS = 16L
        private const val BACKGROUND_SLICE_MILLIS = 4L
        private const val SETTLE_ROUNDS = 3
        private const val EDGE_X = 8f
        private const val EDGE_Y = 900f
    }
}
