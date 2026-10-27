package com.waivera.waivera.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.screenshottesting.ScreenshotHeights
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every profile-module screen in every major state, across the shared matrix (light/dark ×
 * small/large × 100%/200% font), named `<screen>_<state>`. States are built from the demo
 * fixtures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ProfileScreenshotTest {
    private val scope = CoroutineScope(SupervisorJob())
    private val graph = DemoGraph(scope)
    private val me = DemoData.currentUser()

    @After
    fun tearDown() = scope.cancel()

    private fun capture(
        name: String,
        heightDp: Int? = null,
        content: @Composable () -> Unit,
    ) = captureScreenshots(name, heightDp = heightDp) {
        // Avatars stay on initials: an async image decode would make captures racy.
        CompositionLocalProvider(LocalSkeletonDelayMillis provides 0L) { content() }
    }

    private fun ready(
        details: Section<EmployeeProfile> = Section.Ready(DemoData.profile()),
        celebration: Celebration? = null,
        connection: ConnectionBannerState = ConnectionBannerState.Connected,
        refreshError: WaiveraError? = null,
        refreshing: Boolean = false,
    ) = ProfileUiState.Ready(
        user = me,
        details = details,
        detailsRefreshError = refreshError,
        timeOff = Section.Ready(DemoData.timeOffSummary()),
        files = Section.Ready(DemoData.files().take(ProfileViewModel.VISIBLE_FILES)),
        organization = Section.Ready(OrganizationInfo(me.organizationId, me.organizationName)),
        celebration = celebration,
        isRefreshing = refreshing,
        connection = connection,
    )

    @Composable
    private fun Profile(state: ProfileUiState) = ProfileContent(
        state = state,
        onBack = {},
        onRefresh = {},
        onRetryConnection = {},
        onOpenPerson = {},
        onOpenSettings = {},
    )

    @Test
    fun profilePopulated() = capture("profile_populated") { Profile(ready()) }

    @Test
    fun profileCelebration() = capture("profile_celebration") {
        Profile(ready(celebration = Celebration(birthday = true, anniversaryYears = 4)))
    }

    @Test
    fun profileLoading() = capture("profile_loading") {
        Profile(
            ready(details = Section.Loading, refreshing = true).copy(
                timeOff = Section.Loading,
                files = Section.Loading,
                organization = Section.Loading,
            ),
        )
    }

    @Test
    fun profileError() = capture("profile_error") { Profile(ready(details = Section.Failed(WaiveraError.Server(500)))) }

    @Test
    fun profileOffline() = capture("profile_offline") {
        Profile(ready(connection = ConnectionBannerState.Offline, refreshError = WaiveraError.Network()))
    }

    // Tall window: the failed sections are below the first screen of the profile.
    @Test
    fun profileSectionErrors() = capture("profile_section_errors", heightDp = ScreenshotHeights.TALL_DP) {
        Profile(
            ready().copy(
                timeOff = Section.Failed(WaiveraError.Timeout()),
                files = Section.Ready(emptyList()),
                organization = Section.Ready(null),
            ),
        )
    }

    @Test
    fun profileLongContent() = capture("profile_long") {
        val long = DemoData.profile().copy(
            displayName = "Alexandria Catherine Rivera-Montgomery",
            address = "4100 Very Long Street Name Boulevard, Building 12, Suite 3400, Austin, TX 78701",
            positions = listOf("Senior support coordinator", "Community integration specialist", "Trainer"),
            agencies = listOf("Central Texas Community Services", "Hill Country Waiver Partners"),
        )
        val many = DemoData.timeOffSummary().let { summary ->
            val extra = (1L..4L).map { TimeOffRequest(id = 900 + it, leaveType = "Sick leave") }
            summary.copy(requests = summary.requests + extra)
        }
        Profile(ready(details = Section.Ready(long)).copy(timeOff = Section.Ready(many)))
    }

    @Test
    fun profileUnavailable() = capture("profile_unavailable") { Profile(ProfileUiState.Unavailable) }

    // People

    private fun peopleState(): PeopleUiState {
        val people = runBlocking { graph.people.observeDirectory().first().data }
        val rows = PeopleFilter.apply(people, me.userId, "").map {
            PersonRow(
                it.id,
                it.name,
                it.email,
                if (it.id == DemoData.JORDAN) Celebration(birthday = false, anniversaryYears = 5) else null,
            )
        }
        return PeopleUiState(rows = rows, totalCount = rows.size, phase = ListPhase.Content)
    }

    @Composable
    private fun People(state: PeopleUiState) = PeopleContent(
        state = state,
        onBack = {},
        onQueryChange = {},
        onRefresh = {},
        onRetryConnection = {},
        onOpenPerson = {},
    )

    @Test
    fun peoplePopulated() = capture("people_populated") { People(peopleState()) }

    @Test
    fun peopleSearch() = capture("people_search") {
        val all = peopleState()
        People(all.copy(query = "pri", rows = all.rows.filter { it.name.contains("Pri") }))
    }

    @Test
    fun peopleNoMatch() = capture("people_no_match") {
        People(peopleState().copy(query = "zz", rows = emptyList(), phase = ListPhase.Empty))
    }

    @Test
    fun peopleEmpty() = capture("people_empty") { People(PeopleUiState(phase = ListPhase.Empty)) }

    @Test
    fun peopleLoading() = capture("people_loading") {
        People(PeopleUiState(phase = ListPhase.Loading, isRefreshing = true))
    }

    @Test
    fun peopleError() = capture("people_error") {
        People(PeopleUiState(phase = ListPhase.Error, error = WaiveraError.Server(500)))
    }

    // Offline with nothing cached: the error state alone, no offline banner on top of it.
    @Test
    fun peopleErrorOffline() = capture("people_error_offline") {
        People(
            PeopleUiState(
                phase = ListPhase.Error,
                error = WaiveraError.Network(),
                connection = ConnectionBannerState.Offline,
            ),
        )
    }

    @Test
    fun peopleOffline() = capture("people_offline") {
        People(peopleState().copy(connection = ConnectionBannerState.Offline, error = WaiveraError.Network()))
    }

    // Person detail

    private val priyaDetails = PersonDetails(
        displayName = "Priya Raman",
        email = "priya.raman@example.com",
        phoneNumber = "(512) 555-0142",
        roles = listOf("Care team lead", "Trainer"),
        organization = me.organizationName,
    )

    @Composable
    private fun Person(state: PersonDetailUiState) = PersonDetailContent(
        state = state,
        snackbar = remember { SnackbarHostState() },
        onBack = {},
        onMessage = {},
        onRetry = {},
        onRetryConnection = {},
    )

    @Test
    fun personPopulated() = capture("person_populated") {
        Person(
            PersonDetailUiState(
                userId = DemoData.PRIYA,
                name = "Priya Raman",
                email = "priya.raman@example.com",
                details = Section.Ready(priyaDetails),
                celebration = Celebration(birthday = true, anniversaryYears = null),
            ),
        )
    }

    @Test
    fun personLoading() = capture("person_loading") {
        Person(PersonDetailUiState(userId = DemoData.JORDAN, name = "Jordan Lee", email = "jordan.lee@example.com"))
    }

    @Test
    fun personOpeningChat() = capture("person_opening_chat") {
        Person(
            PersonDetailUiState(
                userId = DemoData.PRIYA,
                name = "Priya Raman",
                details = Section.Ready(priyaDetails),
                isOpeningChat = true,
            ),
        )
    }

    @Test
    fun personError() = capture("person_error") {
        Person(
            PersonDetailUiState(
                userId = DemoData.JORDAN,
                name = "Jordan Lee",
                details = Section.Failed(WaiveraError.Server(500)),
                connection = ConnectionBannerState.Reconnecting,
            ),
        )
    }

    // Bookmarks

    /** The demo bookmarks plus one thread reply, built once (captures recompose per variant). */
    private val bookmarkRows: List<BookmarkRow> by lazy {
        runBlocking {
            val reply = graph.backend.messages.value.values.flatten().first { it.isThreadReply }
            graph.bookmarks.toggle(reply.id)
            BookmarkRules.rows(graph.bookmarks.observeBookmarks().first().data)
        }
    }

    @Composable
    private fun Bookmarks(state: BookmarksUiState) = BookmarksContent(
        state = state,
        snackbar = remember { SnackbarHostState() },
        onBack = {},
        onQueryChange = {},
        onRefresh = {},
        onRetryConnection = {},
        onOpen = {},
        onRemove = {},
    )

    @Test
    fun bookmarksPopulated() = capture("bookmarks_populated") {
        val rows = bookmarkRows
        Bookmarks(BookmarksUiState(rows = rows, totalCount = rows.size, phase = ListPhase.Content, now = DemoData.NOW))
    }

    @Test
    fun bookmarksNoMatch() = capture("bookmarks_no_match") {
        Bookmarks(BookmarksUiState(query = "zz", totalCount = 3, phase = ListPhase.Empty, now = DemoData.NOW))
    }

    @Test
    fun bookmarksEmpty() = capture("bookmarks_empty") { Bookmarks(BookmarksUiState(phase = ListPhase.Empty)) }

    @Test
    fun bookmarksLoading() = capture("bookmarks_loading") { Bookmarks(BookmarksUiState(phase = ListPhase.Loading)) }

    @Test
    fun bookmarksError() = capture("bookmarks_error") {
        Bookmarks(BookmarksUiState(phase = ListPhase.Error, error = WaiveraError.Server(500)))
    }

    @Test
    fun bookmarksOffline() = capture("bookmarks_offline") {
        val rows = bookmarkRows
        Bookmarks(
            BookmarksUiState(
                rows = rows,
                totalCount = rows.size,
                phase = ListPhase.Content,
                error = WaiveraError.Network(),
                connection = ConnectionBannerState.Offline,
                now = DemoData.NOW,
            ),
        )
    }
}
