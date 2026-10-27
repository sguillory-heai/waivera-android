package com.waivera.waivera.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import com.waivera.waivera.core.network.ExternalLinks

/**
 * The app's [UriHandler]: web links (call lobby, ticket and message links) open in a Custom Tab
 * tinted with the app's surface colour, so the person stays in Waivera. Devices without a Custom
 * Tabs browser fall back to a browsable view intent. Anything [ExternalLinks] does not allow is
 * refused here too, whatever the caller checked. Like Compose's default handler it throws
 * [IllegalArgumentException] when the link is refused or nothing can open it, which screens
 * already turn into their "couldn't open" message.
 *
 * [context] is the activity, so no new task is needed.
 */
internal class CustomTabsUriHandler(private val context: Context, private val toolbarColor: Int) : UriHandler {
    override fun openUri(uri: String) {
        require(ExternalLinks.isOpenable(uri)) { "Only web links can be opened" }
        val parsed = uri.trim().toUri()
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
            .setShowTitle(true)
            .build()
        try {
            tab.launchUrl(context, parsed)
        } catch (_: ActivityNotFoundException) {
            view(parsed)
        }
    }

    private fun view(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                selector = null
            }
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw IllegalArgumentException("No app can open this link", error)
        }
    }
}
