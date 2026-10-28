package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Banners at 200% on the small phone. Status banners go compact (one row, icon action, a
 * chevron for the folded detail, UI3-08). Error and warning banners stay full and show their
 * whole title and reason (UI4-01).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class CompactBannerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    content()
                }
            }
        }
    }

    private fun renderCompact(title: String, message: String, actionLabel: String) = render {
        WaiveraBanner(
            title = title,
            message = message,
            tone = BannerTone.Offline,
            actionLabel = actionLabel,
            onAction = {},
            actionIcon = WaiveraIcons.Refresh,
            compact = true,
        )
    }

    @Test
    fun offlineBannerIsOneRowWithAnIconAction() {
        renderCompact(
            title = "You're offline",
            message = "Showing what you had at 10:18 AM.",
            actionLabel = "Retry now",
        )
        compose.onNodeWithContentDescription("Retry now").assertHasClickAction()
        val text = compose.onNodeWithContentDescription("You're offline. Showing what you had at 10:18 AM.")
            .getBoundsInRoot()
        // One 15sp title line at 200% plus its padding; a second line would add another 40dp.
        assertTrue("banner text is ${text.height} tall", text.height <= ONE_ROW_MAX)
        compose.onNodeWithTag(BANNER_CHEVRON_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aLongCompactTitleWrapsInsteadOfBeingCut() {
        renderCompact(
            title = "Still can't reach Waivera after several tries",
            message = "Showing saved conversations.",
            actionLabel = "Retry now",
        )
        val title = compose.onNodeWithTag(BANNER_COMPACT_TITLE_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("title is ${title.height} tall, so it was cut to one line", title.height > ONE_TITLE_LINE_MAX)
        compose.onNodeWithTag(BANNER_CHEVRON_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun signInFailureShowsItsReason() = assertFull(SIGN_IN_FAILED)

    @Test
    fun signInOfflineShowsItsReason() = assertFull(SIGN_IN_OFFLINE)

    @Test
    fun resetSentShowsTheAddress() = assertFull(RESET_SENT)

    @Test
    fun configurationMissingShowsItsWholeTitle() = assertFull(CONFIGURATION_MISSING)

    @Test
    fun joinFailureShowsItsReason() = assertFull(JOIN_FAILED)

    @Test
    fun responseFailureShowsItsReasonAndAnIconRetry() = assertFull(RESPONSE_FAILED)

    @Test
    fun notSentOffscreenKeepsNotSentInTheTitle() = assertFull(NOT_SENT_OFFSCREEN)

    @Test
    fun notificationsBlockedSaysTheyAreOff() = assertFull(NOTIFICATIONS_BLOCKED)

    @Test
    fun passwordSavedButNotOpenedShowsItsWholeTitle() = assertFull(PASSWORD_CONTINUE_FAILED)

    /** A full banner at 200%: title and message on screen, the title not cut, no mid-word breaks. */
    private fun assertFull(banner: InAppBanner) {
        render {
            WaiveraBanner(
                title = banner.title,
                message = banner.message,
                tone = banner.tone,
                actionLabel = banner.actionLabel,
                onAction = banner.actionLabel?.let { { } },
                actionIcon = banner.actionIcon,
                onDismiss = if (banner.dismissible) ({ }) else null,
            )
        }
        val title = compose.onNodeWithText(banner.title).assertIsDisplayed()
        val message = compose.onNodeWithText(banner.message).assertIsDisplayed()
        val titleLayout = title.fetchTextLayoutResult()
        assertFalse("\"${banner.title}\" overflows", titleLayout.hasVisualOverflow)
        assertBreaksOnlyBetweenWords(banner.title, titleLayout)
        assertBreaksOnlyBetweenWords(banner.message, message.fetchTextLayoutResult())
        banner.actionLabel?.let { compose.onNodeWithContentDescription(it).assertHasClickAction() }
    }

    private fun assertBreaksOnlyBetweenWords(text: String, layout: TextLayoutResult) {
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("\"$text\" breaks mid-word at $end", text[end - 1] == ' ' || text[end - 1] == '-')
        }
    }

    private fun SemanticsNodeInteraction.fetchTextLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    /** Copies of the in-app error and warning banners (feature strings are not visible from here). */
    private class InAppBanner(
        val title: String,
        val message: String,
        val tone: BannerTone,
        val actionLabel: String? = null,
        val actionIcon: Int? = null,
        val dismissible: Boolean = false,
    )

    private companion object {
        const val LARGE_FONT = 2f
        val ONE_ROW_MAX = 64.dp

        // One 15sp title line at 200% is 40dp tall (20sp line height).
        val ONE_TITLE_LINE_MAX = 48.dp

        val SIGN_IN_FAILED = InAppBanner(
            title = "Couldn't sign in",
            message = "That email and password don't match. Try again.",
            tone = BannerTone.Error,
            dismissible = true,
        )
        val SIGN_IN_OFFLINE = InAppBanner(
            title = "Couldn't sign in",
            message = "You appear to be offline. Check your connection and try again.",
            tone = BannerTone.Error,
            dismissible = true,
        )
        val RESET_SENT = InAppBanner(
            title = "Check your email",
            message = "If sam@example.com has a Waivera account, we've sent it a link to reset the password.",
            tone = BannerTone.Info,
            dismissible = true,
        )
        val CONFIGURATION_MISSING = InAppBanner(
            title = "This build isn't set up yet",
            message = "This copy of the app is missing its server settings, so sign-in is turned off. " +
                "Ask your administrator for a supported version of Waivera.",
            tone = BannerTone.Warning,
        )
        val JOIN_FAILED = InAppBanner(
            title = "Unable to join meeting",
            message = "The web meeting experience could not be opened.",
            tone = BannerTone.Error,
            dismissible = true,
        )
        val RESPONSE_FAILED = InAppBanner(
            title = "Response not saved",
            message = "You appear to be offline. Check your connection and try again.",
            tone = BannerTone.Error,
            actionLabel = "Retry",
            actionIcon = WaiveraIcons.Refresh,
        )
        val NOT_SENT_OFFSCREEN = InAppBanner(
            title = "1 message not sent",
            message = "It's further up the conversation.",
            tone = BannerTone.Error,
            actionLabel = "Show",
            actionIcon = WaiveraIcons.ArrowUp,
            dismissible = true,
        )
        val NOTIFICATIONS_BLOCKED = InAppBanner(
            title = "Notifications are off for Waivera",
            message = "Android is blocking notifications from this app, so nothing below will reach you " +
                "until you turn them on.",
            tone = BannerTone.Warning,
            actionLabel = "Open settings",
            actionIcon = WaiveraIcons.OpenExternal,
        )
        val PASSWORD_CONTINUE_FAILED = InAppBanner(
            title = "Your password is saved, but Waivera didn't open",
            message = "You appear to be offline. Check your connection and try again.",
            tone = BannerTone.Warning,
            dismissible = true,
        )
    }
}
