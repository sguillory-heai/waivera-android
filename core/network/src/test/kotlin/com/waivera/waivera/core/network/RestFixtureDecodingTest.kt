package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.AttachmentScanStatus
import com.waivera.waivera.core.network.dto.Base64AvatarResultDto
import com.waivera.waivera.core.network.dto.CalendarEmployeeMilestoneResponseDto
import com.waivera.waivera.core.network.dto.CalendarFeedResponseDto
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.dto.ChannelType
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.network.dto.ChatReadStateDto
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.CreateMeetingRoomResponse
import com.waivera.waivera.core.network.dto.EmployeeFilesPageDto
import com.waivera.waivera.core.network.dto.GetEmployeeProfileDto
import com.waivera.waivera.core.network.dto.GetUserNameResponseDBVM
import com.waivera.waivera.core.network.dto.GetUserNameResponseVM
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MessageResponse
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.MessageType
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.dto.MobilePushResultDto
import com.waivera.waivera.core.network.dto.OrganizationDto
import com.waivera.waivera.core.network.dto.OrganizationResponse
import com.waivera.waivera.core.network.dto.TimeOffProfileDto
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.network.dto.UploadAttachmentResponse
import com.waivera.waivera.core.network.dto.UserLoginResponseDto
import com.waivera.waivera.core.network.dto.UserWithRoleDto
import com.waivera.waivera.core.testing.Fixtures
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RestFixtureDecodingTest {
    private val messages = ListSerializer(MessageSendResponse.serializer())

    private val decoders: Map<String, KSerializer<*>> = mapOf(
        "rest-auth-get-combined-user-list.json" to envelope(ListSerializer(GetUserNameResponseDBVM.serializer())),
        "rest-auth-get-user-details.json" to envelope(UserWithRoleDto.serializer()),
        "rest-auth-get-username-list.json" to envelope(ListSerializer(GetUserNameResponseVM.serializer())),
        "rest-auth-login-requires-password-change.json" to envelope(UserLoginResponseDto.serializer()),
        "rest-auth-login.json" to envelope(UserLoginResponseDto.serializer()),
        "rest-auth-organization-details.json" to envelope(ListSerializer(OrganizationDto.serializer())),
        "rest-auth-update-contact-and-timezone.json" to envelope(String.serializer()),
        "rest-calendar-get-all-event.json" to envelope(CalendarFeedResponseDto.serializer()),
        "rest-calendar-get-milestones.json" to envelope(ListSerializer(CalendarEmployeeMilestoneResponseDto.serializer())),
        "rest-chat-create-conversation-existing.json" to envelope(ConversationResponse.serializer()),
        "rest-chat-create-conversation.json" to envelope(ConversationResponse.serializer()),
        "rest-chat-delete-message.json" to envelope(MessageResponse.serializer()),
        "rest-chat-edit-message.json" to envelope(MessageResponse.serializer()),
        "rest-chat-get-all.json" to envelope(ListSerializer(UnifiedConversationResponse.serializer())),
        "rest-chat-get-bookmarks.json" to envelope(ListSerializer(MessageResponse.serializer())),
        "rest-chat-get-conversation-channel.json" to ConversationResponse.serializer(),
        "rest-chat-get-conversation.json" to ConversationResponse.serializer(),
        "rest-chat-get-messages-pagination-deleted.json" to messages,
        "rest-chat-get-messages-pagination-empty.json" to messages,
        "rest-chat-get-messages-pagination-reply.json" to messages,
        "rest-chat-get-messages-pagination-richtext.json" to messages,
        "rest-chat-get-messages-pagination-system-message.json" to messages,
        "rest-chat-get-messages-pagination-with-attachment.json" to messages,
        "rest-chat-get-messages-pagination-with-reaction.json" to messages,
        "rest-chat-get-messages-pagination.json" to messages,
        "rest-chat-get-my-conversations-null-last-message.json" to ListSerializer(ConversationResponse.serializer()),
        "rest-chat-get-my-conversations-with-meeting.json" to ListSerializer(ConversationResponse.serializer()),
        "rest-chat-get-my-conversations.json" to ListSerializer(ConversationResponse.serializer()),
        "rest-chat-get-private-conversation.json" to envelope(ConversationResponse.serializer()),
        "rest-chat-get-thread.json" to messages,
        "rest-chat-getorcreate-conversation-for-event.json" to envelope(ConversationResponse.serializer()),
        "rest-chat-leave-conversation.json" to envelope(String.serializer()),
        "rest-chat-read-through-thread.json" to ChatReadStateDto.serializer(),
        "rest-chat-read-through.json" to ChatReadStateDto.serializer(),
        "rest-chat-recent-activity.json" to ListSerializer(ChatActivityResponse.serializer()),
        "rest-chat-search-conversation.json" to envelope(messages),
        "rest-chat-send-message.json" to MessageSendResponse.serializer(),
        "rest-chat-toggle-bookmark.json" to envelope(Boolean.serializer()),
        "rest-chat-upload-attachment.json" to UploadAttachmentResponse.serializer(),
        "rest-employee-get-employee-files.json" to envelope(EmployeeFilesPageDto.serializer()),
        "rest-employee-get-profile.json" to envelope(GetEmployeeProfileDto.serializer()),
        "rest-error-apiresponse-unauthorized.json" to envelope(String.serializer()),
        "rest-error-controller-500.json" to envelope(String.serializer()),
        "rest-error-exception-middleware-500.json" to envelope(String.serializer()),
        "rest-file-avatar-base64.json" to Base64AvatarResultDto.serializer(),
        "rest-invitation-accept-deny-meeting.json" to envelope(Boolean.serializer()),
        "rest-mobile-push-register-error.json" to MobilePushResultDto.serializer(),
        "rest-mobile-push-register.json" to MobilePushResultDto.serializer(),
        "rest-mobile-push-unregister.json" to MobilePushResultDto.serializer(),
        "rest-timeoff-get-for-user-profile.json" to envelope(ListSerializer(TimeOffProfileDto.serializer())),
        "rest-users-organization.json" to OrganizationResponse.serializer(),
        "rest-video-create-meeting-room.json" to envelope(CreateMeetingRoomResponse.serializer()),
    )

    @Test
    fun everyRestResponseFixtureDecodes() {
        val responseFixtures = Fixtures.names("rest-").filterNot { "-request-" in it }
        assertEquals(responseFixtures.toSet(), decoders.keys, "every response fixture needs a decoder")
        responseFixtures.forEach { name -> decode(name, decoders.getValue(name)) }
    }

    @Test
    fun loginReadsTheEnvelopeData() {
        val login = decode("rest-auth-login-requires-password-change.json", envelope(UserLoginResponseDto.serializer()))
        assertEquals("usr_alex_rivera", login.data?.userId)
        assertTrue(login.data?.requiresPasswordChange == true)
        assertFalse(login.data?.requiresOnboarding == true)
    }

    @Test
    fun loginAcceptsPascalCaseKeysAndLooseFlags() {
        val body = """{"Data":{"UserId":4471,"RequiresPasswordChange":"TRUE","RequiresOnboarding":1},"StatusCode":200}"""
        val login = WaiveraJson.decodeNormalized(envelope(UserLoginResponseDto.serializer()), body).data

        assertEquals("4471", login?.userId)
        assertEquals(true, login?.requiresPasswordChange)
        assertEquals(true, login?.requiresOnboarding)
    }

    @Test
    fun unifiedListDecodesChannelsAttachmentsAndArchivedRows() {
        val items = decode("rest-chat-get-all.json", envelope(ListSerializer(UnifiedConversationResponse.serializer()))).data!!
        val channel = items.single { it.conversationId == 41L }

        assertEquals(ChannelType.TEAM, channel.channel?.channelType)
        assertEquals("application/pdf", channel.lastMessage?.attachmentContentType)
        assertEquals(AttachmentScanStatus.CLEAN, channel.lastMessage?.attachmentScanStatus)
        assertEquals("usr_jordan_lee", channel.lastMessage?.userId)
        assertTrue(items.single { it.conversationId == 58L }.channel?.isArchived == true)
        assertEquals(Instant.parse("2026-10-20T14:32:11.482913Z"), items.first().lastMessage?.createDate)
    }

    @Test
    fun stringEncodedMessageDocDecodesToAnObject() {
        val body = """{"id":1,"conversationId":7,"createDate":"2026-10-20T14:32:11","messageDoc":"{\"type\":\"doc\",\"content\":[]}"}"""
        val message = WaiveraJson.decodeNormalized(MessageSendResponse.serializer(), body)

        assertEquals("doc", (message.messageDoc as JsonObject)["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun fatAuthorDecodesToTheSmallAuthorModel() {
        val conversation = decode("rest-chat-get-conversation.json", ConversationResponse.serializer())
        assertEquals("usr_alex_rivera", conversation.lastMessage?.author?.id)
        assertEquals("Alex Rivera", conversation.lastMessage?.author?.displayName)
    }

    @Test
    fun conversationListCarriesCallsMeetingsBotsAndMissingLastMessages() {
        val list = decode("rest-chat-get-my-conversations.json", ListSerializer(ConversationResponse.serializer()))
        assertEquals(CallRecordStatus.ACTIVE, list.single { it.id == 11L }.ongoingCall?.status)
        assertTrue(list.single { it.id == 14L }.isBotDirect)
        assertEquals("avatars/bot-3.png", list.single { it.id == 14L }.participants.last().avatarUrl)

        val meeting = decode("rest-chat-get-my-conversations-with-meeting.json", ListSerializer(ConversationResponse.serializer()))
        assertEquals("room_7f3c91", meeting.single().activeMeeting?.roomId)

        val empty = decode("rest-chat-get-my-conversations-null-last-message.json", ListSerializer(ConversationResponse.serializer()))
        assertNull(empty.single().lastMessage)
    }

    @Test
    fun messageVariantsDecode() {
        val deleted = decode("rest-chat-get-messages-pagination-deleted.json", messages).single()
        assertTrue(deleted.isDeleted)
        assertNotNull(deleted.deletedAt)

        val rich = decode("rest-chat-get-messages-pagination-richtext.json", messages).single()
        assertEquals(1, rich.contentFormat)
        assertEquals("doc", rich.messageDoc?.jsonObject?.get("type")?.jsonPrimitive?.content)

        val system = decode("rest-chat-get-messages-pagination-system-message.json", messages).single()
        assertEquals(MessageType.CHANNEL_EVENT, system.messageType)
        assertEquals("bot:3", system.author?.id)

        val reactions = decode("rest-chat-get-messages-pagination-with-reaction.json", messages).single().reactions
        assertEquals(listOf("👍", "🎉"), reactions.map { it.reactionType })

        val replies = decode("rest-chat-get-thread.json", messages)
        assertEquals(listOf(null, 9330L, 9330L), replies.map { it.threadRootMessageId })
    }

    @Test
    fun calendarFeedKeepsInstantsDatesAndStatuses() {
        val feed = decode("rest-calendar-get-all-event.json", envelope(CalendarFeedResponseDto.serializer())).data!!
        val weekly = feed.events.first()
        val allDay = feed.events.last()

        assertEquals(Instant.parse("2026-10-20T15:00:00Z"), weekly.startTime)
        assertEquals("usr_priya_raman", weekly.ownerUserID)
        assertEquals(listOf(InviteStatus.ACCEPTED, InviteStatus.INVITED, InviteStatus.TENTATIVE), weekly.attendees.map { it.inviteStatus })
        assertEquals(LocalDate.of(2026, 11, 5), allDay.allDayStart)
        assertEquals(LocalDate.of(2026, 11, 6), allDay.allDayEndExclusive)
        assertEquals(listOf(LocalDate.of(2026, 11, 26)), allDay.cancelledAllDayDates)
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), feed.timeOff.single().startTime)
        assertEquals(LocalDate.of(2026, 11, 26), feed.holidays.single().startDate)
    }

    @Test
    fun milestonesAndProfileDatesStayCalendarDates() {
        val milestones = decode("rest-calendar-get-milestones.json", envelope(ListSerializer(CalendarEmployeeMilestoneResponseDto.serializer()))).data!!
        assertEquals(listOf(MilestoneType.BIRTHDAY, MilestoneType.WORK_ANNIVERSARY), milestones.map { it.type })
        assertEquals(2024, milestones.last().startYear)

        val profile = decode("rest-employee-get-profile.json", envelope(GetEmployeeProfileDto.serializer())).data!!
        assertEquals(LocalDate.of(1991, 6, 14), profile.birthday)
        assertEquals("America/Chicago", profile.timezone)
        assertEquals("avatars/usr_alex_rivera.png", profile.avatarURL)
    }

    @Test
    fun activityTypesDecodeAndUnknownTypesAreKept() {
        val activity = decode("rest-chat-recent-activity.json", ListSerializer(ChatActivityResponse.serializer()))
        assertEquals(
            listOf(ChatActivityType.MENTION, ChatActivityType.THREAD_REPLY, ChatActivityType.REACTION),
            activity.map { it.activityType },
        )
        val unknown = WaiveraJson.decodeNormalized(
            ChatActivityResponse.serializer(),
            """{"id":1,"activityType":"chat.poll","conversationId":7,"messageId":2}""",
        )
        assertEquals(ChatActivityType.UNKNOWN, unknown.activityType)
    }

    @Test
    fun pascalCaseExceptionMiddlewareBodyDecodes() {
        val body = decode("rest-error-exception-middleware-500.json", envelope(String.serializer()))
        assertEquals(500, body.statusCode)
        assertFalse(body.success)
    }

    @Test
    fun readThroughOmitsNullThreadRoot() {
        val state = decode("rest-chat-read-through.json", ChatReadStateDto.serializer())
        assertNull(state.threadRootMessageId)
        assertEquals(listOf(4411L, 4412L), state.readActivityIds)
        assertEquals(Instant.parse("2026-10-20T14:40:02.118374Z"), state.readAt)
    }

    @Test
    fun requestFixturesHaveTheRegisteredShape() {
        val ios = WaiveraJson.parseToJsonElement(Fixtures.text("rest-mobile-push-register-request-ios.json")).jsonObject
        assertEquals("fcm", ios.getValue("provider").jsonPrimitive.content)
        assertTrue(Fixtures.names("rest-chat-get-messages").isNotEmpty())
        assertTrue(WaiveraJson.parseToJsonElement(Fixtures.text("rest-chat-get-messages-pagination-empty.json")).jsonArray.isEmpty())
    }

    private fun <T> envelope(inner: KSerializer<T>) = ApiResponse.serializer(inner)

    private fun <T> decode(name: String, serializer: KSerializer<T>): T =
        WaiveraJson.decodeNormalized(serializer, Fixtures.text(name))
}
