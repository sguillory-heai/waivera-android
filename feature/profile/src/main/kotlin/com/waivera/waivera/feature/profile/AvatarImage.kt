package com.waivera.waivera.feature.profile

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads avatar bytes for a backend user id (PeopleRepository.avatar, which caches and negative-caches). */
internal fun interface AvatarLoader {
    suspend fun load(userId: String): ByteArray?
}

/** Screens provide their ViewModel's loader; previews and screenshots fall back to initials. */
internal val LocalAvatarLoader = staticCompositionLocalOf { AvatarLoader { null } }

/** Ids that never have an avatar, so no request is made for them (spec 10 §5). */
internal fun isAvatarId(userId: String?): Boolean {
    val id = userId?.trim().orEmpty()
    return id.isNotEmpty() && !id.startsWith("bot:") && id != "null" && id != "undefined"
}

/**
 * The avatar for [userId], or null while it loads or when there is none. The last image stays
 * on screen while a new one loads, so rows never flash back to initials.
 */
@Composable
internal fun rememberAvatarPainter(userId: String?): Painter? {
    val loader = LocalAvatarLoader.current
    val painter by produceState<Painter?>(initialValue = null, userId, loader) {
        if (!isAvatarId(userId)) return@produceState
        val bytes = loader.load(requireNotNull(userId)) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let(::BitmapPainter)
        } ?: value
    }
    return painter
}
