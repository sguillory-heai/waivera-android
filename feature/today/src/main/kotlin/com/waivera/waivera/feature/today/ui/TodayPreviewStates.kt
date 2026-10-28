package com.waivera.waivera.feature.today.ui

import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.feature.today.model.ActivityRow
import com.waivera.waivera.feature.today.model.ActivitySection
import com.waivera.waivera.feature.today.model.ActivitySummary
import com.waivera.waivera.feature.today.model.CelebrationItem
import com.waivera.waivera.feature.today.model.DayBlock
import com.waivera.waivera.feature.today.model.DayHourLabel
import com.waivera.waivera.feature.today.model.DayPart
import com.waivera.waivera.feature.today.model.DayTimeline
import com.waivera.waivera.feature.today.model.Greeting
import com.waivera.waivera.feature.today.model.MessageSnippet
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.NeedsYouSection
import com.waivera.waivera.feature.today.model.NextUpItem
import com.waivera.waivera.feature.today.model.NextUpSection
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.model.TodayProgress
import com.waivera.waivera.feature.today.model.TodayStats
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Hand-built states for IDE previews. Screenshot tests render real states from the demo data instead. */
@Suppress("MagicNumber")
internal object TodayPreviewStates {
    fun callbacks() = TodayCallbacks({}, {}, {}, { _, _, _ -> }, {}, {})

    fun populated() = TodayUiState(
        accountName = "Alex Rivera",
        greeting = Greeting(DayPart.MORNING, firstName = "Alex"),
        date = LocalDate.of(2026, 11, 1),
        isInitialLoad = false,
        needsYou = needsYou(),
        nextUp = nextUp(),
        milestones = listOf(CelebrationItem("usr_jordan_lee", "Jordan Lee", isBirthday = false, anniversaryYears = 5)),
        activity = activity(),
        progress = TodayProgress(handled = 5, open = 2),
        stats = TodayStats(eventsToday = 3, unreadChats = 6, eventsThisWeek = 14),
        day = day(),
    )

    fun caughtUp() = populated().copy(
        needsYou = NeedsYouSection(isReady = true),
        activity = ActivitySection(isReady = true),
        progress = TodayProgress(handled = 9, open = 0),
    )

    /** Concept B's day: 11 to 12, 2 to 2:30 and 4 to 4:30 on an 8 AM to 6 PM track, at 10:30. */
    fun day() = DayTimeline(
        windowStart = LocalTime.of(8, 0),
        windowEnd = LocalTime.of(18, 0),
        blocks = listOf(
            DayBlock(eventId = "demo_visit", start = 0.3f, end = 0.4f, tone = 0),
            DayBlock(eventId = "demo_call", start = 0.6f, end = 0.65f, tone = 1),
            DayBlock(eventId = "demo_team", start = 0.8f, end = 0.85f, tone = 2),
        ),
        ticks = (1..9).map { it / 10f },
        labels = (0..5).map { DayHourLabel(at = it / 5f, time = LocalTime.of(8 + it * 2, 0)) },
        now = 0.25f,
        eventCount = 3,
        scheduled = Duration.ofHours(2),
    )

    fun visit() = NextUpItem(
        eventId = "demo_visit",
        title = "Client home visit",
        time = UpcomingLabel.InMinutes(30),
        start = LocalTime.of(11, 0),
        end = LocalTime.of(12, 0),
        endsOnLaterDay = false,
        location = "1200 W Lake St",
        isOnline = false,
        startsToday = true,
    )

    private fun needsYou() = NeedsYouSection(
        items = listOf(
            NeedsYouItem.DirectMessage(
                7,
                "Priya Raman",
                MessageSnippet.Text("Can you cover Thursday?"),
                2,
                PastLabel.MinutesAgo(4),
            ),
            NeedsYouItem.Invitation(
                "demo_invite",
                "Care plan review",
                "Priya Raman",
                UpcomingLabel.TomorrowAt(LocalTime.of(9, 0)),
            ),
        ),
        isReady = true,
    )

    private fun nextUp() = NextUpSection(
        items = listOf(
            NextUpItem(
                "demo_allday",
                "Daylight saving ends",
                UpcomingLabel.AllDayToday,
                null,
                null,
                false,
                null,
                false,
            ),
            visit(),
        ),
        isReady = true,
    )

    private fun activity() = ActivitySection(
        rows = listOf(
            ActivityRow(
                1,
                ChatActivityType.REACTION,
                ActivitySummary.Reaction("Dana", "🎉"),
                PastLabel.MinutesAgo(12),
                false,
                7,
                9,
                null,
            ),
            ActivityRow(
                2,
                ChatActivityType.THREAD_REPLY,
                ActivitySummary.Server("Jordan replied in a thread"),
                PastLabel.Yesterday,
                true,
                11,
                10,
                9,
            ),
        ),
        unreadCount = 1,
        totalCount = 2,
        isReady = true,
    )
}
