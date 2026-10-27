package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable

/** Mirrors sprout_api `dto/Request/MobilePushRegistrationRequest`. Native apps always register FCM tokens. */
@Serializable
data class MobilePushRegistrationRequest(
    val deviceId: String,
    val pushToken: String,
    val platform: String = "android",
    val provider: String = "fcm",
    val pushEnabled: Boolean,
    val chatMessagesEnabled: Boolean,
    val mentionsEnabled: Boolean,
    val reactionsEnabled: Boolean,
    val activeCallsEnabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val previewContentEnabled: Boolean,
)

/** Mirrors sprout_api `MobilePushUnregisterRequest`. */
@Serializable
data class MobilePushUnregisterRequest(val deviceId: String)

/** `{ "success": bool, "message"?: string }` from the `api/mobile-push` endpoints. */
@Serializable
data class MobilePushResultDto(val success: Boolean = false, val message: String? = null)
