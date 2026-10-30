package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.api.MobilePushApi
import com.waivera.waivera.core.network.di.loggingScope
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.AttachmentScanStatus
import com.waivera.waivera.core.network.dto.CalendarFeedResponseDto
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.dto.ChannelType
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.network.dto.ChatReadThroughRequest
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.GetEmployeeProfileDto
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MessageType
import com.waivera.waivera.core.network.dto.MobilePushRegistrationRequest
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.network.dto.UserWithRoleDto
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.apiCallOrNull
import com.waivera.waivera.core.network.log.LogSink
import com.waivera.waivera.core.network.log.WaiveraLog
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit

/**
 * Payloads shaped like sprout_api's C# types as production sends them: nulls where the C# type is
 * a non-nullable reference (`string`, `List<T>`) but the row has none, omitted fields
 * (`WhenWritingNull`), zone-less `DateTime`s, `DateOnly` values, `DateTime.MinValue`, enum names
 * the app does not know, and string-encoded documents. Each must decode, never fail the screen.
 */
class ProdShapeResilienceTest {
    private val server = MockWebServer()
    private lateinit var retrofit: Retrofit

    @Before
    fun setUp() {
        server.start()
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor({ "token" }, server.url("/"))).build())
            .addConverterFactory(WaiveraConverterFactory())
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun anEmptyOkForAnObjectEndpointIsNoBodyNotADecodingFailure() = runTest {
        server.enqueue(MockResponse.Builder().code(200).build())
        server.enqueue(MockResponse.Builder().code(200).body("  ").build())
        val chat = retrofit.create(ChatApi::class.java)
        val push = retrofit.create(MobilePushApi::class.java)

        assertNull(apiCallOrNull { chat.readThrough(7, ChatReadThroughRequest(9312)) })
        assertFailsWith<WaiveraError.Unexpected> { apiCall { push.register(pushRequest()) } }
    }

    @Test
    fun theServerRejectingANativePushTokenIsATypedError() = runTest {
        // MobilePushController: ArgumentException becomes 400 { success = false, message }.
        server.enqueue(
            MockResponse.Builder().code(400)
                .body("""{"success":false,"message":"Unsupported push provider."}""").build(),
        )
        val push = retrofit.create(MobilePushApi::class.java)

        assertFailsWith<WaiveraError.Validation> { apiCall { push.register(pushRequest()) } }
    }

    @Test
    fun myConversationsWithNullsAndMissingFieldsDecodes() {
        val body = """
            [
              {
                "id": 7,
                "isBotDirect": false,
                "participants": [
                  {"id": "usr_1", "firstName": null, "lastName": null, "email": null,
                   "avatarUrl": null, "isParticipant": false, "isExternal": false, "deactivated": false,
                   "joinDate": "0001-01-01T00:00:00", "organization": null, "roles": null, "regions": null,
                   "participantCount": 0},
                  {"id": null, "displayName": "Former staff"},
                  {"id": "bot:3"}
                ],
                "lastMessage": {
                  "id": 9312, "conversationId": 7, "userId": "usr_1", "author": null,
                  "messageContent": null, "createDate": "2026-10-20T14:32:11.4829133",
                  "messageType": "SYSTEM", "reactions": null, "contentFormat": 1,
                  "messageDoc": "{\"type\":\"doc\",\"content\":[]}", "attachmentScanStatus": "Legacy",
                  "systemEventType": "ticket.updated", "systemEventData": {"ticketId": 12},
                  "threadReplyCount": 0
                },
                "unreadCount": 2,
                "channel": null,
                "activeMeeting": {"id": null, "roomId": null, "calendarEventId": "evt_1",
                  "createdAt": "2026-10-20T14:00:00", "startDate": null, "title": null},
                "ongoingCall": {"roomId": "room_1", "startTime": "2026-10-20T14:30:00Z",
                  "status": "Active", "callRecordId": 55}
              },
              {"id": 8, "participants": null, "lastMessage": null, "lastReadAt": null}
            ]
        """.trimIndent()

        val list = WaiveraJson.decodeNormalized(ListSerializer(ConversationResponse.serializer()), body)

        assertEquals(listOf(7L, 8L), list.map { it.id })
        val first = list[0]
        assertEquals(3, first.participants.size)
        assertNull(first.participants[1].id)
        val last = requireNotNull(first.lastMessage)
        assertEquals(MessageType.SYSTEM, last.messageType)
        assertEquals(emptyList(), last.reactions)
        assertEquals(AttachmentScanStatus.LEGACY, last.attachmentScanStatus)
        assertEquals(Instant.parse("2026-10-20T14:32:11.482913300Z"), last.createDate)
        assertTrue(last.messageDoc != null, "a string-encoded document is parsed")
        assertEquals(CallRecordStatus.ACTIVE, first.ongoingCall?.status)
        assertEquals(emptyList(), list[1].participants)
    }

    @Test
    fun allConversationsWithAnUnknownChannelTypeDecodes() {
        val body = """
            {"data": [
              {"conversationId": 41, "isChannel": true, "participants": [], "unreadCount": 0,
               "channel": {"channelId": "ch_1", "conversationId": 41, "name": null, "topic": null,
                           "channelType": "ANNOUNCEMENT", "ownerId": null, "isArchived": false, "canWrite": true},
               "activeMeeting": null, "ongoingCall": null, "lastMessage": null}
            ], "statusCode": 200, "message": "OK", "success": true}
        """.trimIndent()

        val response = WaiveraJson.decodeNormalized(
            ApiResponse.serializer(ListSerializer(UnifiedConversationResponse.serializer())),
            body,
        )

        assertEquals(ChannelType.UNKNOWN, response.data?.single()?.channel?.channelType)
    }

    @Test
    fun userDetailsWithNullListsAndMinValueDatesDecodes() {
        // UserWithRoleDto: `List<string> Roles` and `string Timezone` are null for older accounts.
        val body = """
            {"data": {"id": "usr_1", "firstName": null, "lastName": "Rivera", "email": null,
              "displayName": null, "deactivated": false, "joinDate": "0001-01-01T00:00:00",
              "hireDate": null, "organizationId": 3, "organizationName": null, "organization": null,
              "organizationRole": null, "roles": null, "regions": null, "teams": null,
              "waiverFamilyIds": null, "timezone": null, "birthday": "1990-02-28T00:00:00"},
             "statusCode": 200, "success": true}
        """.trimIndent()

        val user = WaiveraJson.decodeNormalized(ApiResponse.serializer(UserWithRoleDto.serializer()), body).data

        assertEquals("usr_1", user?.id)
        assertEquals(emptyList(), user?.roles)
        assertNull(user?.timezone)
        assertEquals(LocalDate.of(1990, 2, 28), user?.birthday)
    }

    @Test
    fun employeeProfileWithNullListsDecodes() {
        val body = """
            {"data": {"employeeId": "usr_1", "firstName": "Alex", "positions": null, "reporters": null,
              "regions": null, "agencyNames": null, "joinDate": "2024-01-08T00:00:00", "timezone": null},
             "statusCode": 200, "success": true}
        """.trimIndent()

        val profile = WaiveraJson.decodeNormalized(
            ApiResponse.serializer(GetEmployeeProfileDto.serializer()),
            body,
        ).data

        assertEquals(emptyList(), profile?.positions)
        assertEquals(emptyList(), profile?.reporters)
    }

    @Test
    fun calendarFeedWithAllDayEventsAndNullListsDecodes() {
        // CalendarEventResponseDto: all-day events still carry non-nullable DateTime Start/EndTime.
        val body = """
            {"data": {
              "events": [
                {"cancelledAllDayDates": [], "id": "evt_1", "title": null,
                 "startTime": "2026-11-26T00:00:00", "endTime": "2026-11-27T00:00:00",
                 "ownerUserID": "usr_1", "createdby": null, "attendees": null, "ownerColor": null,
                 "timeOffEmpData": null, "allDayStart": "2026-11-26", "allDayEndExclusive": "2026-11-27",
                 "isInvitationAccept": null, "holidays": null, "meetingType": null, "crossesMidnight": false},
                {"id": "evt_2", "startTime": "2026-11-02T15:00:00Z", "endTime": "2026-11-02T16:00:00Z",
                 "attendees": [{"attendeeId": "att_1", "type": "WAIVER_PARTICIPANT", "ref": null,
                   "inviteStatus": "NEEDS_ACTION", "respondedAt": null}]}
              ],
              "timeOff": null,
              "holidays": [{"id": 4, "title": "Thanksgiving", "description": null,
                "startDate": "2026-11-26T00:00:00", "endDate": "2026-11-27T00:00:00"}]
            }, "statusCode": 200, "success": true}
        """.trimIndent()

        val feed = WaiveraJson.decodeNormalized(ApiResponse.serializer(CalendarFeedResponseDto.serializer()), body).data

        val events = requireNotNull(feed).events
        assertEquals(LocalDate.of(2026, 11, 26), events[0].allDayStart)
        assertEquals(emptyList(), events[0].attendees)
        assertEquals(InviteStatus.UNKNOWN, events[1].attendees.single().inviteStatus)
        assertEquals(emptyList(), feed.timeOff)
        assertEquals(LocalDate.of(2026, 11, 26), feed.holidays.single().startDate)
    }

    @Test
    fun recentActivityWithAnUnknownTypeDecodes() {
        val body = """
            [{"id": 900, "activityType": "chat.call_missed", "actorUserId": "usr_2",
              "actorDisplayName": "Someone", "conversationId": 7, "messageId": 9312,
              "threadRootMessageId": null, "reactionType": null, "summary": "",
              "createdAt": "2026-10-20T14:32:11.48", "read": false}]
        """.trimIndent()

        val items = WaiveraJson.decodeNormalized(ListSerializer(ChatActivityResponse.serializer()), body)

        assertEquals(ChatActivityType.UNKNOWN, items.single().activityType)
    }

    @Test
    fun aFailedBackgroundJobIsLoggedByClassAndTheScopeKeepsWorking() {
        val logged = CopyOnWriteArrayList<String>()
        val previous = WaiveraLog.sink
        WaiveraLog.sink = LogSink { _, _, message, _ -> logged += message }
        try {
            val scope = loggingScope("Test")

            runBlocking {
                scope.launch { error("Bad payload. JSON input: {\"displayName\":\"Priya Raman\"}") }.join()
                assertEquals(42, scope.async { 42 }.await())
            }

            assertEquals(listOf("Background job failed: java.lang.IllegalStateException"), logged)
        } finally {
            WaiveraLog.sink = previous
        }
    }

    private fun pushRequest() = MobilePushRegistrationRequest(
        deviceId = "device-1",
        pushToken = "fcm-token",
        pushEnabled = true,
        chatMessagesEnabled = true,
        mentionsEnabled = true,
        reactionsEnabled = true,
        activeCallsEnabled = true,
        soundEnabled = true,
        vibrationEnabled = true,
        previewContentEnabled = true,
    )
}
