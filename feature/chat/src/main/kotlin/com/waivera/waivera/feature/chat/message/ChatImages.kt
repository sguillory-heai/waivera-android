package com.waivera.waivera.feature.chat.message

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a bitmap for an image model. The app implementation goes through the Coil singleton. */
fun interface ChatImageLoader {
    suspend fun load(model: Any, maxSizePx: Int): ImageBitmap?
}

/**
 * Coil 3 through `SingletonImageLoader`, which the app installs with the authenticated OkHttp
 * client (G-15), so relative attachment URLs load with the bearer token and are cached on disk.
 */
class CoilChatImageLoader @Inject constructor(@ApplicationContext private val context: Context) : ChatImageLoader {
    override suspend fun load(model: Any, maxSizePx: Int): ImageBitmap? {
        val request = ImageRequest.Builder(context).data(model).size(maxSizePx).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap().asImageBitmap()
    }
}

/**
 * What to load for a message's attachment: the local staged file while it is unsent, else the
 * absolute API URL; null when there is nothing loadable (for example demo data).
 */
class AttachmentModels @Inject constructor(private val repository: AttachmentRepository) {
    fun modelFor(message: Message): Any? {
        val attachment = message.attachment ?: return null
        if (message.isPending) return File(attachment.url).takeIf(File::exists)
        return repository.resolve(attachment.url)
    }
}

/**
 * Avatar bitmaps with a synchronous memory peek, so an avatar already seen never flashes its
 * initials (10 §5). Misses are remembered by the repository's negative cache.
 */
@Singleton
class AvatarImages @Inject constructor(private val people: PeopleRepository) {
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun peek(userId: String): ImageBitmap? = cache.get(userId)

    suspend fun load(userId: String): ImageBitmap? {
        if (userId.isBlank() || userId.startsWith("bot:")) return null
        cache.get(userId)?.let { return it }
        val bytes = people.avatar(userId) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } ?: return null
        cache.put(userId, bitmap)
        return bitmap
    }

    private companion object {
        const val MAX_ENTRIES = 64
    }
}

/** Everything image-related a message row needs, provided once per screen. */
@Immutable
class ChatImageEnvironment(
    val loader: ChatImageLoader?,
    val attachmentModel: (Message) -> Any?,
    val avatars: AvatarImages?,
)

/** Previews and screenshots leave this empty: rows then show initials and image placeholders. */
val LocalChatImages =
    staticCompositionLocalOf { ChatImageEnvironment(loader = null, attachmentModel = { null }, avatars = null) }

sealed interface ChatImageState {
    data object Loading : ChatImageState

    data class Ready(val bitmap: ImageBitmap) : ChatImageState

    data object Unavailable : ChatImageState
}

@Composable
fun rememberChatImage(model: Any?, maxSizePx: Int): ChatImageState {
    val loader = LocalChatImages.current.loader
    val state by produceState<ChatImageState>(ChatImageState.Loading, model, loader) {
        value = if (model == null || loader == null) {
            ChatImageState.Unavailable
        } else {
            runCatching { loader.load(model, maxSizePx) }.getOrNull()?.let { ChatImageState.Ready(it) }
                ?: ChatImageState.Unavailable
        }
    }
    return state
}

@Composable
fun rememberAvatar(userId: String?): ImageBitmap? {
    val avatars = LocalChatImages.current.avatars
    val initial = userId?.let { avatars?.peek(it) }
    val bitmap by produceState(initial, userId, avatars) {
        if (userId != null && avatars != null && value == null) value = runCatching { avatars.load(userId) }.getOrNull()
    }
    return bitmap
}
