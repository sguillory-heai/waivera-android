package com.waivera.waivera.feature.chat.screenshot

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.waivera.waivera.core.demo.DemoBackend
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoPeopleRepository
import com.waivera.waivera.feature.chat.message.AvatarImages
import com.waivera.waivera.feature.chat.message.ChatImageEnvironment
import com.waivera.waivera.feature.chat.message.LocalChatImages
import kotlinx.coroutines.runBlocking

/**
 * The demo people's photos for chat captures, decoded before the capture starts, so rows show
 * Priya's photo as the tour and the profile captures do instead of her initials (UI4-10, UI5-15).
 * The chat list takes [chatListAvatars] as its map; messages read [WithDemoChatAvatars]'s
 * environment, whose cache is already warm, so the first frame has the photo.
 */
internal object DemoChatAvatars {
    private val people = DemoPeopleRepository(DemoBackend())

    val images: AvatarImages by lazy {
        AvatarImages(people).also { avatars -> runBlocking { avatars.load(DemoData.PRIYA) } }
    }

    val chatListAvatars: Map<String, ImageBitmap> by lazy {
        val bytes = runBlocking { people.avatar(DemoData.PRIYA) }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        bitmap?.let { mapOf(DemoData.PRIYA to it) }.orEmpty()
    }
}

@Composable
internal fun WithDemoChatAvatars(content: @Composable () -> Unit) {
    val environment = ChatImageEnvironment(loader = null, attachmentModel = { null }, avatars = DemoChatAvatars.images)
    CompositionLocalProvider(LocalChatImages provides environment, content = content)
}
