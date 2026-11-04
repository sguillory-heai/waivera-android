package com.waivera.waivera.core.data.live

/**
 * Told when a stored notification is read, here or on another device, so its tray entry closes
 * too. A null id means all of them.
 */
fun interface NoticeReadListener {
    fun onRead(notificationId: String?)
}
