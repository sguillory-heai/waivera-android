package com.waivera.waivera.core.network.dto

import com.waivera.waivera.core.network.json.LenientEnumSerializer
import kotlinx.serialization.Serializable

/** `dto/common/SD.cs` ChannelType. Integer codes are what the hub sends (`COMPANY=0, TEAM=1, CUSTOM=2`). */
@Serializable(with = ChannelTypeSerializer::class)
enum class ChannelType(val code: Int?) { COMPANY(0), TEAM(1), CUSTOM(2), UNKNOWN(null) }

internal object ChannelTypeSerializer : LenientEnumSerializer<ChannelType>(
    "waivera.ChannelType",
    ChannelType.entries.toTypedArray(),
    ChannelType.UNKNOWN,
    { it.name },
    { it.code },
)

/** `MessageType` is converted with `.ToString()` server-side, so it is a string on both transports. */
@Serializable(with = MessageTypeSerializer::class)
enum class MessageType { USER, SYSTEM, CHANNEL_EVENT, UNKNOWN }

internal object MessageTypeSerializer : LenientEnumSerializer<MessageType>(
    "waivera.MessageType",
    MessageType.entries.toTypedArray(),
    MessageType.UNKNOWN,
    { it.name },
)

/** `CALL_RECORD_STATUS` member names. Only [END] means the call is over (gaps.md G-10). */
@Serializable(with = CallRecordStatusSerializer::class)
enum class CallRecordStatus(val wireName: String) {
    INITIATED("Initiated"),
    ACTIVE("Active"),
    REJOINED("Rejoined"),
    END("End"),
    UNKNOWN(""),
    ;

    val isLive: Boolean get() = this == INITIATED || this == ACTIVE || this == REJOINED
}

internal object CallRecordStatusSerializer : LenientEnumSerializer<CallRecordStatus>(
    "waivera.CallRecordStatus",
    CallRecordStatus.entries.toTypedArray(),
    CallRecordStatus.UNKNOWN,
    { it.wireName },
)

/** `attachmentScanStatus` (`Models/Chat/ChatAttachmentUpload.cs`). */
@Serializable(with = AttachmentScanStatusSerializer::class)
enum class AttachmentScanStatus { PENDING, CLEAN, REJECTED, LEGACY, UNKNOWN }

internal object AttachmentScanStatusSerializer : LenientEnumSerializer<AttachmentScanStatus>(
    "waivera.AttachmentScanStatus",
    AttachmentScanStatus.entries.toTypedArray(),
    AttachmentScanStatus.UNKNOWN,
    { it.name.lowercase() },
)

/** `ChatActivityResponse.activityType`. */
@Serializable(with = ChatActivityTypeSerializer::class)
enum class ChatActivityType(val wireName: String) {
    MENTION("chat.mention"),
    THREAD_REPLY("chat.thread_reply"),
    REACTION("chat.reaction"),
    UNKNOWN(""),
}

internal object ChatActivityTypeSerializer : LenientEnumSerializer<ChatActivityType>(
    "waivera.ChatActivityType",
    ChatActivityType.entries.toTypedArray(),
    ChatActivityType.UNKNOWN,
    { it.wireName },
)

/** `AttendeeResponseDto.type` (declared `string` in C#, so a string on both transports). */
@Serializable(with = AttendeeTypeSerializer::class)
enum class AttendeeType { EMPLOYEE, WAIVER_PARTICIPANT, EXTERNAL, UNKNOWN }

internal object AttendeeTypeSerializer : LenientEnumSerializer<AttendeeType>(
    "waivera.AttendeeType",
    AttendeeType.entries.toTypedArray(),
    AttendeeType.UNKNOWN,
    { it.name },
)

/** `AttendeeResponseDto.inviteStatus`. */
@Serializable(with = InviteStatusSerializer::class)
enum class InviteStatus { INVITED, ACCEPTED, DECLINED, TENTATIVE, CANCELLED, UNKNOWN }

internal object InviteStatusSerializer : LenientEnumSerializer<InviteStatus>(
    "waivera.InviteStatus",
    InviteStatus.entries.toTypedArray(),
    InviteStatus.UNKNOWN,
    { it.name },
)

/** `CalendarEmployeeMilestoneResponseDto.type`. */
@Serializable(with = MilestoneTypeSerializer::class)
enum class MilestoneType { BIRTHDAY, WORK_ANNIVERSARY, UNKNOWN }

internal object MilestoneTypeSerializer : LenientEnumSerializer<MilestoneType>(
    "waivera.MilestoneType",
    MilestoneType.entries.toTypedArray(),
    MilestoneType.UNKNOWN,
    { it.name },
)

/** `ReceiveChatAttention` reason argument. */
@Serializable(with = AttentionReasonSerializer::class)
enum class AttentionReason(val wireName: String) {
    MENTION("mention"),
    THREAD_REPLY("thread_reply"),
    MESSAGE("message"),
    UNKNOWN(""),
}

internal object AttentionReasonSerializer : LenientEnumSerializer<AttentionReason>(
    "waivera.AttentionReason",
    AttentionReason.entries.toTypedArray(),
    AttentionReason.UNKNOWN,
    { it.wireName },
)

/** `ReceiveReaction` action argument. */
@Serializable(with = ReactionActionSerializer::class)
enum class ReactionAction(val wireName: String) {
    ADDED("added"),
    UPDATED("updated"),
    REMOVED("removed"),
    UNKNOWN(""),
}

internal object ReactionActionSerializer : LenientEnumSerializer<ReactionAction>(
    "waivera.ReactionAction",
    ReactionAction.entries.toTypedArray(),
    ReactionAction.UNKNOWN,
    { it.wireName },
)
