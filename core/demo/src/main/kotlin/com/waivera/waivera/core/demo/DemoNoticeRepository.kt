package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.Notice
import com.waivera.waivera.core.data.model.NoticeFeed
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.NoticeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Singleton
class DemoNoticeRepository @Inject constructor(private val backend: DemoBackend) : NoticeRepository {
    private val notices = MutableStateFlow(DemoNotices.all())

    override fun observeNotices(): Flow<Synced<NoticeFeed>> = backend.synced(
        notices.map { items -> NoticeFeed(items, items.count { !it.read }) },
    )

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    override suspend fun onArrived(notice: Notice) =
        notices.update { list -> if (list.any { it.id == notice.id }) list else listOf(notice) + list }

    override suspend fun markRead(id: String): Result<Unit> {
        notices.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
        return Result.success(Unit)
    }

    override suspend fun markAllRead(): Result<Unit> {
        notices.update { list -> list.map { it.copy(read = true) } }
        return Result.success(Unit)
    }
}

/** Sample bell notifications, worded as the server's templates render. */
object DemoNotices {
    private const val QUARTER_HOUR = 900L
    private const val HOUR = 3_600L
    private const val TWO_HOURS = 2 * HOUR
    private const val THREE_HOURS = 3 * HOUR
    private const val DAY = 86_400L
    private const val TWO_DAYS = 2 * DAY
    private const val THREE_DAYS = 3 * DAY

    fun all(): List<Notice> = listOf(
        Notice(
            id = "demo-n1",
            kind = NoticeKind.TaskAssigned,
            title = "A new task has been assigned to you",
            message = "Priya Raman assigned task 1042 to you",
            createdAt = DemoData.NOW.minusSeconds(QUARTER_HOUR),
            read = false,
            taskId = "1042",
            webPath = "/task/1042",
        ),
        Notice(
            id = "demo-n2",
            kind = NoticeKind.Signature,
            title = "Signature requested",
            message = "Jordan Lee asked you to sign the service agreement",
            createdAt = DemoData.NOW.minusSeconds(TWO_HOURS),
            read = false,
            webPath = "/esign/requests",
        ),
        Notice(
            id = "demo-n3",
            kind = NoticeKind.Meeting,
            title = "Team huddle",
            message = "Priya Raman invited you to a meeting",
            createdAt = DemoData.NOW.minusSeconds(THREE_HOURS),
            read = false,
            eventId = "demo_supervision",
        ),
        Notice(
            id = "demo-n4",
            kind = NoticeKind.TaskActivity,
            title = "New activity on task 987",
            message = "Jordan Lee has completed an activity Home visit on 987",
            createdAt = DemoData.NOW.minusSeconds(DAY + HOUR),
            read = true,
            taskId = "987",
            webPath = "/task/987",
        ),
        Notice(
            id = "demo-n5",
            kind = NoticeKind.TimeOff,
            title = "Time off approved",
            message = "Your request for Nov 26 to Nov 27 was approved",
            createdAt = DemoData.NOW.minusSeconds(TWO_DAYS),
            read = true,
        ),
        Notice(
            id = "demo-n6",
            kind = NoticeKind.Survey,
            title = "Storm readiness check-in",
            message = "Please respond as soon as possible",
            createdAt = DemoData.NOW.minusSeconds(THREE_DAYS),
            read = true,
            webPath = "/employeesurveys/demo",
        ),
    )
}
