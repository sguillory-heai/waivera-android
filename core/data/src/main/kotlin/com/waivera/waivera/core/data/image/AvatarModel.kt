package com.waivera.waivera.core.data.image

/**
 * A Coil image model for a person's avatar by backend user id. Pass it as the image model of
 * `WaiveraAvatar`; the app's image loader fetches it through `PeopleRepository.avatar`, which
 * decodes the API's data URI and keeps its own positive and negative cache.
 */
data class AvatarModel(val userId: String) {
    /** Bots and placeholder ids never have an avatar, so no request is made for them (10 §5). */
    val isLoadable: Boolean
        get() = userId.isNotBlank() && !userId.startsWith("bot:") && userId != "null" && userId != "undefined"

    companion object {
        /** The model for [userId], or null when there is nothing to load. */
        fun of(userId: String?): AvatarModel? = userId?.trim()?.let(::AvatarModel)?.takeIf { it.isLoadable }
    }
}
