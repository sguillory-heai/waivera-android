package com.waivera.waivera.core.network.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Mirrors sprout_api `MobilePushController` (`api/mobile-push`). */
interface MobilePushApi {
    @POST("api/mobile-push/register")
    suspend fun register(@Body request: MobilePushRegistrationRequest)

    @POST("api/mobile-push/unregister")
    suspend fun unregister(@Body request: MobilePushUnregisterRequest)
}

/** Mirrors sprout_api `dto/Request/MobilePushRegistrationRequest`. Native apps always register FCM tokens. */
@Serializable
data class MobilePushRegistrationRequest(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("pushToken") val pushToken: String,
    @SerialName("platform") val platform: String = "android",
    @SerialName("provider") val provider: String = "fcm",
    @SerialName("pushEnabled") val pushEnabled: Boolean,
    @SerialName("chatMessagesEnabled") val chatMessagesEnabled: Boolean,
    @SerialName("mentionsEnabled") val mentionsEnabled: Boolean,
    @SerialName("reactionsEnabled") val reactionsEnabled: Boolean,
    @SerialName("activeCallsEnabled") val activeCallsEnabled: Boolean,
    @SerialName("soundEnabled") val soundEnabled: Boolean,
    @SerialName("vibrationEnabled") val vibrationEnabled: Boolean,
    @SerialName("previewContentEnabled") val previewContentEnabled: Boolean,
)

/** Mirrors sprout_api `MobilePushUnregisterRequest`. */
@Serializable
data class MobilePushUnregisterRequest(@SerialName("deviceId") val deviceId: String)
