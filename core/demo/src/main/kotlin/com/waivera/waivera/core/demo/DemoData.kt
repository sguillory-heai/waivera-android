@file:Suppress("MagicNumber")

package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.live.ConversationMerge
import com.waivera.waivera.core.data.live.LiveSessionRepository
import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.EmployeeFile
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.Holiday
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.OngoingCall
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.TimeOffBalance
import com.waivera.waivera.core.data.model.TimeOffEntry
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.CalendarEmployeeMilestoneResponseDto
import com.waivera.waivera.core.network.dto.CalendarFeedResponseDto
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.EmployeeFilesPageDto
import com.waivera.waivera.core.network.dto.GetEmployeeProfileDto
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.MessageType
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.dto.TimeOffProfileDto
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.network.dto.UserWithRoleDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The deterministic demo world: the shared fixtures plus scripted extras so every screen has
 * something to show. "Now" is Sunday 1 November 2026, 10:30 in Chicago, the day clocks fall
 * back, so DST handling is visible in the schedule.
 */
@Suppress("TooManyFunctions")
object DemoData {
    val NOW: Instant = Instant.parse("2026-11-01T16:30:00Z")
    val ZONE: ZoneId = ZoneId.of("America/Chicago")
    const val ME = "usr_alex_rivera"
    const val PRIYA = "usr_priya_raman"
    const val JORDAN = "usr_jordan_lee"
    const val DM_PRIYA = 7L
    const val GROUP = 11L
    const val BOT = 14L
    const val CHANNEL = 41L
    const val THREAD_ROOT = 9330L
    const val CALL_RECORD = 2210L
    const val FAILED_CLIENT_ID = "demo-failed-0001"

    fun currentUser(): CurrentUser {
        val details = DemoFixtures.decode(
            "rest-auth-get-user-details.json",
            ApiResponse.serializer(UserWithRoleDto.serializer()),
        ).data
        return LiveSessionRepository.currentUser(ME, details, "alex.rivera@example.com", requiresOnboarding = false)
            .copy(timeZoneId = ZONE.id)
    }

    private fun page(name: String): List<Message> =
        DemoFixtures.decode(name, ListSerializer(MessageSendResponse.serializer())).map(Mappers::message)

    fun conversations(): List<Conversation> {
        val mine = DemoFixtures.decode(
            "rest-chat-get-my-conversations.json",
            ListSerializer(ConversationResponse.serializer()),
        )
            .map(Mappers::conversation).associateBy { it.id }
        val all = DemoFixtures.decode(
            "rest-chat-get-all.json",
            ApiResponse.serializer(ListSerializer(UnifiedConversationResponse.serializer())),
        ).data.orEmpty().map(Mappers::conversation).associateBy { it.id }
        val merged = (mine.keys + all.keys).mapNotNull { ConversationMerge.combine(mine[it], all[it]) }
            // The archived pilot channel is filtered server-side in the live app (G-13).
            .filter { it.channel?.isArchived != true && it.id != 58L }
        val lastById = messages().groupBy {
            it.conversationId
        }.mapValues { (_, list) -> list.filter { !it.isThreadReply }.maxByOrNull { it.id } }
        return merged.map { c ->
            val latest = lastById[c.id]
            val withPreview = if (latest !=
                null
            ) {
                c.copy(lastMessage = ConversationMerge.mergePreview(c.lastMessage, Mappers.preview(latest)))
            } else {
                c
            }
            when (c.id) {
                GROUP -> withPreview.copy(
                    unreadCount = 2,
                    ongoingCall = OngoingCall(
                        "room_demo_11",
                        NOW.minusSeconds(240),
                        CallRecordStatus.ACTIVE,
                        CALL_RECORD,
                    ),
                )
                BOT -> withPreview.copy(unreadCount = 1)
                else -> withPreview
            }
        }
    }

    fun people(): List<Person> {
        val fromChats = conversations().flatMap { it.participants }.filter { !it.id.startsWith("bot:") }
        val extra = listOf(
            Person("usr_sam_okafor", "Sam", "Okafor", "Sam Okafor", "sam.okafor@example.com"),
            Person("usr_mei_chen", "Mei", "Chen", "Mei Chen", "mei.chen@example.com"),
        )
        return (fromChats + extra).distinctBy { it.id }.filter { it.id != ME }.sortedBy { it.name }
    }

    private val priya = Person(PRIYA, "Priya", "Raman", "Priya Raman", "priya.raman@example.com")
    private val jordan = Person(JORDAN, "Jordan", "Lee", "Jordan Lee", "jordan.lee@example.com")

    // Scripted sample data reads best as one list.
    @Suppress("LongMethod")
    fun messages(): List<Message> {
        val fixtures = listOf(
            "rest-chat-get-messages-pagination.json",
            "rest-chat-get-messages-pagination-with-attachment.json",
            "rest-chat-get-messages-pagination-with-reaction.json",
            "rest-chat-get-messages-pagination-deleted.json",
            "rest-chat-get-messages-pagination-richtext.json",
            "rest-chat-get-messages-pagination-system-message.json",
            "rest-chat-get-thread.json",
        ).flatMap(::page)
        val scripted = listOf(
            Message(
                id = 9500,
                conversationId = GROUP,
                author = priya,
                authorId = PRIYA,
                text = "Morning all, the Hughes visit moved to 10.",
                createdAt = NOW.minusSeconds(5_400),
                reactions = listOf(Reaction(JORDAN, "👍"), Reaction(ME, "👍")),
            ),
            Message(
                id = 9501,
                conversationId = GROUP,
                authorId = null,
                type = MessageType.SYSTEM,
                text = "Video call started",
                createdAt = NOW.minusSeconds(4_200),
                callRecordId = CALL_RECORD,
            ),
            Message(
                id = 9502,
                conversationId = GROUP,
                authorId = null,
                type = MessageType.SYSTEM,
                text = "Video call ended in 12m 4s",
                createdAt = NOW.minusSeconds(3_476),
                callRecordId = CALL_RECORD,
            ),
            Message(
                id = 9503,
                conversationId = GROUP,
                author = jordan,
                authorId = JORDAN,
                text = "Thanks, notes are in the care log.",
                createdAt = NOW.minusSeconds(1_800),
            ),
            Message(
                id = 9510, conversationId = BOT, authorId = null, botId = "3", type = MessageType.SYSTEM,
                text = "Ticket updated", createdAt = NOW.minusSeconds(3_000),
                systemEventType = "ticket.update",
                systemEventData = buildJsonObject {
                    put("ticketId", "WAIV-2527")
                    put("title", "Update Hughes care plan goals")
                    put("url", "https://app.example.test/tickets/WAIV-2527")
                    put("type", "Task")
                    put("priority", "HIGH")
                    put("createdBy", "Priya Raman")
                    put("createdAt", "2026-10-30T15:00:00Z")
                    put("dueDate", "2026-11-04T23:00:00Z")
                },
            ),
            Message(
                id = 0 - NOW.toEpochMilli(),
                conversationId = DM_PRIYA,
                clientMessageId = FAILED_CLIENT_ID,
                authorId = ME,
                text = "Sending the updated schedule now",
                createdAt = NOW.minusSeconds(120),
                sendState = SendState.FAILED,
            ),
        )
        return (fixtures + scripted).distinctBy { it.id }
    }

    fun bookmarkedIds(): Set<Long> = setOf(THREAD_ROOT, 9320L)

    fun activity(): List<ActivityItem> = DemoFixtures.decode(
        "rest-chat-recent-activity.json",
        ListSerializer(ChatActivityResponse.serializer()),
    ).map(Mappers::activity) +
        ActivityItem(
            id = 4420,
            type = com.waivera.waivera.core.network.dto.ChatActivityType.MENTION,
            actorUserId = PRIYA,
            actorName = "Priya Raman",
            conversationId = GROUP,
            messageId = 9500,
            summary = "Priya Raman mentioned you: \"the Hughes visit moved to 10\"",
            createdAt = NOW.minusSeconds(5_400),
        )

    @Suppress("LongMethod")
    fun events(): List<ScheduleEvent> {
        val feed = DemoFixtures.decode(
            "rest-calendar-get-all-event.json",
            ApiResponse.serializer(CalendarFeedResponseDto.serializer()),
        ).data
        val fixtureEvents = feed?.events.orEmpty().mapNotNull { CalendarMath.event(it, ME) }
        fun me(status: InviteStatus) =
            Attendee("att-me", AttendeeType.EMPLOYEE, ME, displayName = "Alex Rivera", inviteStatus = status)
        val priyaAttendee =
            Attendee(
                "att-priya",
                AttendeeType.EMPLOYEE,
                PRIYA,
                displayName = "Priya Raman",
                inviteStatus = InviteStatus.ACCEPTED,
            )
        val scripted = listOf(
            // 22:00 CDT Saturday to 06:00 CST Sunday: nine hours across the fall-back hour.
            ScheduleEvent(
                id = "demo_overnight",
                title = "Overnight respite shift",
                location = "Hughes residence",
                startAt = Instant.parse("2026-11-01T03:00:00Z"),
                endAt = Instant.parse("2026-11-01T12:00:00Z"),
                ownerUserId = ME,
                ownerName = "Alex Rivera",
                myInvitation = MyInvitation.ORGANIZER,
            ),
            ScheduleEvent(
                id = "demo_allday_today",
                title = "Daylight saving ends",
                allDayStart = LocalDate.of(2026, 11, 1),
                allDayEndExclusive = LocalDate.of(2026, 11, 2),
                ownerUserId = PRIYA,
                ownerName = "Priya Raman",
            ),
            ScheduleEvent(
                id = "demo_visit", title = "Client home visit", location = "1200 W Lake St",
                startAt = Instant.parse("2026-11-01T17:00:00Z"), endAt = Instant.parse("2026-11-01T18:00:00Z"),
                ownerUserId = ME, ownerName = "Alex Rivera",
                attendees = listOf(
                    me(InviteStatus.ACCEPTED),
                ),
                myInvitation = MyInvitation.ORGANIZER,
            ),
            ScheduleEvent(
                id = "demo_supervision", title = "Supervision 1:1", online = true, isSprout = true,
                startAt = Instant.parse("2026-11-01T20:00:00Z"), endAt = Instant.parse("2026-11-01T20:30:00Z"),
                ownerUserId = PRIYA, ownerName = "Priya Raman",
                attendees = listOf(
                    priyaAttendee,
                    me(InviteStatus.ACCEPTED),
                ),
                myInvitation = MyInvitation.ACCEPTED,
            ),
            ScheduleEvent(
                id = "demo_invite", title = "Care plan review", online = true,
                meetingDetails = "https://meet.example.test/care-plan-review",
                startAt = Instant.parse("2026-11-02T15:00:00Z"), endAt = Instant.parse("2026-11-02T16:00:00Z"),
                ownerUserId = PRIYA, ownerName = "Priya Raman",
                attendees = listOf(
                    priyaAttendee,
                    me(InviteStatus.INVITED),
                ),
                myInvitation = MyInvitation.PENDING,
            ),
        )
        return fixtureEvents + scripted
    }

    fun timeOff(): List<TimeOffEntry> {
        val feed = DemoFixtures.decode(
            "rest-calendar-get-all-event.json",
            ApiResponse.serializer(CalendarFeedResponseDto.serializer()),
        ).data
        return feed?.timeOff.orEmpty().map {
            TimeOffEntry(
                it.id, it.userId, it.startTime, it.endTime, it.leaveType, it.requestType,
                it.durationMinutes, it.status, it.isCancellationPending,
            )
        }
    }

    fun holidays(): List<Holiday> =
        listOf(
            Holiday(9, "Thanksgiving", LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 26), "Office closed"),
        )

    fun milestones(): List<Milestone> {
        val fixture = DemoFixtures.decode(
            "rest-calendar-get-milestones.json",
            ApiResponse.serializer(ListSerializer(CalendarEmployeeMilestoneResponseDto.serializer())),
        ).data.orEmpty().mapNotNull(CalendarMath::milestone)
        // Jordan's anniversary is today; Priya's birthday (fixture) is tomorrow.
        return fixture +
            Milestone(MilestoneType.WORK_ANNIVERSARY, JORDAN, "Jordan Lee", month = 11, day = 1, startYear = 2021)
    }

    fun profile(): EmployeeProfile {
        val dto = DemoFixtures.decode(
            "rest-employee-get-profile.json",
            ApiResponse.serializer(GetEmployeeProfileDto.serializer()),
        ).data
        return EmployeeProfile(
            employeeId = dto?.employeeId ?: ME,
            firstName = dto?.firstName,
            lastName = dto?.lastName,
            displayName = dto?.displayName,
            email = dto?.email,
            avatarUrl = dto?.avatarURL,
            phoneNumber = dto?.phoneNumber,
            address = dto?.address,
            hireDate = dto?.hireDate,
            birthday = dto?.birthday,
            joinedAt = dto?.joinDate,
            positions = dto?.positions.orEmpty(),
            managers = dto?.reporters.orEmpty().mapNotNull { r ->
                r.employeeId?.let { Person(it, displayName = r.name) }
            },
            regions = dto?.regions.orEmpty().mapNotNull { it.name },
            agencies = dto?.agencyNames.orEmpty(),
            timeZoneId = ZONE.id,
            waiverFamilies = dto?.waiverFamilies.orEmpty(),
            scId = currentUser().scId,
        )
    }

    fun timeOffSummary(): TimeOffSummary {
        val rows = DemoFixtures.decode(
            "rest-timeoff-get-for-user-profile.json",
            ApiResponse.serializer(ListSerializer(TimeOffProfileDto.serializer())),
        ).data.orEmpty()
        return TimeOffSummary(
            requests = rows.map {
                TimeOffRequest(
                    it.id, it.timeOffStartDate, it.timeOffEndDate, it.typeOfLeave, it.requestType,
                    it.comment, it.isApproved, it.isRejected, it.isCancellationPending,
                )
            },
            balance = rows.firstOrNull()?.let {
                TimeOffBalance(
                    remainingDays = it.remainingLeaveDays ?: it.remainingLeaveCount,
                    remainingHours = it.remainingLeaveHours,
                    usedDays = it.usedLeaveDays,
                    usedHours = it.usedLeaveHours,
                    allocated = it.allocatedLeaveCount,
                    pending = it.pendingTimeOffCount,
                    usedPercentage = it.usedLeavePercentage,
                )
            },
        )
    }

    fun files(): List<EmployeeFile> = DemoFixtures.decode(
        "rest-employee-get-employee-files.json",
        ApiResponse.serializer(EmployeeFilesPageDto.serializer()),
    ).data?.items.orEmpty()
        .mapNotNull { f ->
            f.id?.let {
                EmployeeFile(
                    it,
                    f.fileName,
                    f.documentCategoryName,
                    f.dateAdded,
                    f.mimeType,
                    f.size,
                    f.uploadedByName,
                )
            }
        }

    /** A tiny PNG for every avatar, from the avatar fixture. */
    fun avatarBytes(): ByteArray? {
        val raw = Regex(
            "\"base64Image\"\\s*:\\s*\"([^\"]+)\"",
        ).find(DemoFixtures.text("rest-file-avatar-base64.json"))?.groupValues?.get(1)
        return raw?.let(com.waivera.waivera.core.data.live.LivePeopleRepository::decodeDataUri)
    }
}
