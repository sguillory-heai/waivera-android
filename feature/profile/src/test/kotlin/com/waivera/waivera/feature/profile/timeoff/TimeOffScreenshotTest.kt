package com.waivera.waivera.feature.profile.timeoff

import com.waivera.waivera.core.data.model.LeaveBlock
import com.waivera.waivera.core.data.model.LeaveImpact
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveSendProblem
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Request time off sheet and its leave type list, named `time_off_<state>`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class TimeOffScreenshotTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val types = listOf(
        LeaveType(1, "Vacation", tracksBalance = true, availableMinutes = 2400),
        LeaveType(2, "Sick leave", tracksBalance = true, availableMinutes = 960),
        LeaveType(3, "Personal day", tracksBalance = true, availableMinutes = 480),
        LeaveType(4, "Floating holiday", tracksBalance = true, availableMinutes = 0),
        LeaveType(5, "Bereavement", tracksBalance = false, availableMinutes = null),
        LeaveType(6, "Jury duty", tracksBalance = false, availableMinutes = null),
        LeaveType(7, "Military leave", tracksBalance = false, availableMinutes = null),
        LeaveType(8, "Parental leave", tracksBalance = true, availableMinutes = 7200),
        LeaveType(9, "Unpaid leave", tracksBalance = false, availableMinutes = null),
    )
    private val ready = LeavePreview(
        requestedMinutes = 1440,
        tracksBalance = true,
        beforeMinutes = 2400,
        afterMinutes = 960,
        block = null,
        impact = LeaveImpact(dueBefore = 1, dueDuring = 2, shortNotice = false),
    )
    private val base = TimeOffFormState(types = LeaveTypes.Ready(types), typeId = 1, today = today)

    private fun capture(name: String, heightDp: Int, state: TimeOffFormState, failure: SendFailure? = null) =
        captureScreenshots(name, heightDp = heightDp) {
            WaiveraSheetFrame(title = "Request time off") {
                TimeOffFormContent(state = state, failure = failure, actions = TimeOffFormActions())
            }
        }

    @Test
    fun empty() = capture("time_off_empty", 900, TimeOffFormState(types = LeaveTypes.Ready(types), today = today))

    @Test
    fun fullDays() = capture(
        "time_off_full_days",
        1300,
        base.copy(
            firstDay = today.plusDays(20),
            lastDay = today.plusDays(22),
            note = "Family trip for Thanksgiving.",
            check = LeaveCheck.Ready(ready),
        ),
    )

    @Test
    fun partialTooLong() = capture(
        "time_off_partial_too_long",
        1100,
        base.copy(
            typeId = 6,
            partialDay = true,
            day = today.plusDays(3),
            from = LocalTime.of(9, 0),
            to = LocalTime.of(18, 30),
            issue = LeaveIssue.TooLong,
        ),
    )

    @Test
    fun pastHoliday() = capture(
        "time_off_past_holiday",
        1100,
        base.copy(
            firstDay = today.minusDays(1),
            lastDay = today.minusDays(1),
            past = true,
            check = LeaveCheck.Ready(ready.copy(requestedMinutes = 0, block = LeaveBlock.Holiday)),
        ),
    )

    @Test
    fun overBalance() = capture(
        "time_off_over_balance",
        1300,
        base.copy(
            typeId = 2,
            firstDay = today.plusDays(7),
            lastDay = today.plusDays(9),
            check = LeaveCheck.Ready(ready.copy(beforeMinutes = 960, afterMinutes = -480, impact = null)),
        ),
    )

    @Test
    fun sendFailed() = capture(
        "time_off_send_failed",
        1100,
        base.copy(typeId = 2),
        failure = SendFailure(LeaveSendProblem.Probation),
    )

    @Test
    fun typesFailed() =
        capture("time_off_types_failed", 900, TimeOffFormState(types = LeaveTypes.Failed, today = today))

    @Test
    fun leaveTypes() = captureScreenshots("time_off_leave_types", heightDp = 1100) {
        WaiveraSheetFrame(title = "Leave type") { LeaveTypeList(types = types, selectedId = 1, onPick = {}) }
    }
}
