package com.waivera.waivera.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * The app's [UriHandler]: web links (call lobby, ticket and message links) open in a Custom Tab
 * tinted with the app's surface colour, so the person stays in Waivera. Other schemes, and devices
 * without a Custom Tabs browser, fall back to a plain view intent. Like Compose's default handler
 * it throws [IllegalArgumentException] when nothing can open the link, which screens already
 * turn into their "couldn't open" message.
 *
 * [context] is the activity, so no new task is needed.
 */
internal class CustomTabsUriHandler(private val context: Context, private val toolbarColor: Int) : UriHandler {
    override fun openUri(uri: String) {
        val parsed = uri.trim().toUri()
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            view(parsed)
            return
        }
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
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (error: ActivityNotFoundException) {
            throw IllegalArgumentException("No app can open this link", error)
        }
    }
}
