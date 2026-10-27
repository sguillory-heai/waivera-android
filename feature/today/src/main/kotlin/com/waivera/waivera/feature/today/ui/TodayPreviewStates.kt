package com.waivera.waivera.feature.today.ui

import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.feature.today.model.ActivityRow
import com.waivera.waivera.feature.today.model.ActivitySection
import com.waivera.waivera.feature.today.model.ActivitySummary
import com.waivera.waivera.feature.today.model.CelebrationItem
import com.waivera.waivera.feature.today.model.MessageSnippet
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.NeedsYouSection
import com.waivera.waivera.feature.today.model.NextUpItem
import com.waivera.waivera.feature.today.model.NextUpSection
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.LocalDate
import java.time.LocalTime

/** Hand-built states for IDE previews. Screenshot tests render real states from the demo data instead. */
@Suppress("MagicNumber")
internal object TodayPreviewStates {
    fun callbacks() = TodayCallbacks({}, {}, {}, { _, _, _ -> }, {}, {})

    fun populated() = TodayUiState(
        accountName = "Alex Rivera",
        date = LocalDate.of(2026, 11, 1),
        isInitialLoad = false,
        needsYou = NeedsYouSection(
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
        ),
        nextUp = NextUpSection(
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
                NextUpItem(
                    "demo_visit",
                    "Client home visit",
                    UpcomingLabel.InMinutes(30),
                    LocalTime.of(11, 0),
                    LocalTime.of(12, 0),
                    false,
                    "1200 W Lake St",
                    false,
                ),
            ),
            isReady = true,
        ),
        milestones = listOf(CelebrationItem("usr_jordan_lee", "Jordan Lee", isBirthday = false, anniversaryYears = 5)),
        activity = ActivitySection(
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
        ),
    )

    fun caughtUp() = populated().copy(
        needsYou = NeedsYouSection(isReady = true),
        activity = ActivitySection(isReady = true),
    )
}
