package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertEquals
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

    /**
     * The offscreen not-sent banner as the conversation shows it at 200%: compact, with "Show"
     * as a labelled text button and the dismiss in a row under the title, about a quarter of the
     * small phone's height at most (UI5-06).
     */
    @Test
    fun offscreenNotSentStaysSmallAndLabelsItsAction() {
        render {
            WaiveraBanner(
                title = NOT_SENT_OFFSCREEN.title,
                message = "Retry, edit or discard it in the conversation.",
                tone = NOT_SENT_OFFSCREEN.tone,
                actionLabel = NOT_SENT_OFFSCREEN.actionLabel,
                onAction = {},
                actionIcon = NOT_SENT_OFFSCREEN.actionIcon,
                onDismiss = {},
                compact = true,
                modifier = Modifier.testTag(BANNER_TAG),
            )
        }
        val banner = compose.onNodeWithTag(BANNER_TAG).getBoundsInRoot()
        assertTrue("banner is ${banner.height} tall", banner.height <= QUARTER_SCREEN_MAX)
        compose.onNode(hasText(checkNotNull(NOT_SENT_OFFSCREEN.actionLabel))).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription("Dismiss").assertHasClickAction()
        val title = compose.onNodeWithTag(BANNER_COMPACT_TITLE_TAG, useUnmergedTree = true).getBoundsInRoot()
        val show = compose.onNode(hasText(checkNotNull(NOT_SENT_OFFSCREEN.actionLabel))).getBoundsInRoot()
        assertTrue("Show is beside the title, not under it", show.top >= title.bottom)
    }

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
                detail = banner.detail,
            )
        }
        val title = compose.onNodeWithText(banner.title).assertIsDisplayed()
        val message = compose.onNodeWithText(banner.message).assertIsDisplayed()
        val titleLayout = title.fetchTextLayoutResult()
        assertTitleFits(banner.title, titleLayout)
        assertBreaksOnlyBetweenWords(banner.title, titleLayout)
        assertBreaksOnlyBetweenWords(banner.message, message.fetchTextLayoutResult())
        // An icon action is named by its description, a text action by its label.
        banner.actionLabel?.let { compose.onNode(hasContentDescription(it) or hasText(it)).assertHasClickAction() }
        banner.detail?.let { assertDetailWhole(it) }
    }

    /**
     * The detail (an email address) is shown whole inside the banner (UI6-03): every character
     * laid out, nothing ellipsized, no line past the edge, and when it wraps, only after its "@"
     * ("alex.rivera@" / "example.org"). Its semantics hold the plain address, so TalkBack reads
     * all of it.
     */
    private fun assertDetailWhole(detail: String) {
        val node = compose.onNodeWithTag(BANNER_DETAIL_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(detail).assertIsDisplayed()
        val layout = node.fetchTextLayoutResult()
        val shown = layout.layoutInput.text.text
        assertEquals("\"$detail\" is not fully laid out", shown.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertFalse("\"$detail\" line $line is ellipsized", layout.isLineEllipsized(line))
            assertTrue(
                "\"$detail\" line $line runs to ${layout.getLineRight(line)} past ${layout.size.width}",
                layout.getLineRight(line) <= layout.size.width,
            )
        }
        for (line in 0 until layout.lineCount - 1) {
            val visible = shown.substring(0, layout.getLineEnd(line)).filterNot { it == '\u2060' || it == '\u200B' }
            assertTrue("\"$detail\" breaks after \"$visible\"", visible.endsWith("@"))
        }
    }

    /**
     * Every character is laid out and no line runs past the text's own width or height.
     * `hasVisualOverflow` is not used: a one-line title narrower than the row is sized to its
     * text while its paragraph keeps the row's width, which Compose reports as a width overflow
     * even though nothing is cut.
     */
    private fun assertTitleFits(text: String, layout: TextLayoutResult) {
        assertFalse("\"$text\" is cut vertically", layout.didOverflowHeight)
        assertTrue("\"$text\" is not fully laid out", layout.getLineEnd(layout.lineCount - 1) == text.length)
        for (line in 0 until layout.lineCount) {
            assertTrue(
                "\"$text\" line $line ends at ${layout.getLineRight(line)} past ${layout.size.width}",
                layout.getLineRight(line) <= layout.size.width,
            )
        }
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
        val detail: String? = null,
    )

    private companion object {
        const val LARGE_FONT = 2f
        val ONE_ROW_MAX = 64.dp

        // One 15sp title line at 200% is 40dp tall (20sp line height).
        val ONE_TITLE_LINE_MAX = 48.dp

        // About a quarter of the 640dp small phone; a two-line compact title, its padding and
        // the 48dp action row come to about 164dp.
        val QUARTER_SCREEN_MAX = 172.dp
        const val BANNER_TAG = "banner"

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
            message = "If this address has a Waivera account, we've sent it a link to reset the password.",
            tone = BannerTone.Info,
            dismissible = true,
            // Longer than the small phone's banner at 200%, so it has to wrap after the "@", whole.
            detail = "alex.rivera@example.org",
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
