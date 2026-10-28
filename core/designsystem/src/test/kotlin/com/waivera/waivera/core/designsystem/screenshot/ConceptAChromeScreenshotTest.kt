package com.waivera.waivera.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.component.ConfirmationContent
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbar
import com.waivera.waivera.core.designsystem.component.WaiveraSwitchRow
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Concept A's shared chrome carried past Today (phase 2): the plum top bar on a tab root, a
 * pushed screen and a screen with its own hero; grouped rows on a card; the gradient selected
 * pill on chips and segments; the plum snackbar; and the confirmation sheets.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ConceptAChromeScreenshotTest {
    /** The modal sheet's surface and curve, drawn inline (the content brings its own gutter). */
    private val sheetFrame: Modifier
        @Composable
        get() = Modifier
            .background(WaiveraTheme.colors.surface, WaiveraShapes.ModalSheet)
            .padding(top = WaiveraSpacing.Large)

    @Test
    fun heroTopBars() = captureScreenshots(name = "HeroTopBars") {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            WaiveraTopBar(
                title = "Chats",
                actions = listOf(TopBarAction(icon = WaiveraIcons.Add, contentDescription = "New chat", onClick = {})),
                account = TopBarAccount(name = "Alex Rivera", onClick = {}),
            )
            WaiveraTopBar(
                title = "North team check-in with a much longer conversation title",
                subtitle = "6 members",
                onBack = {},
                actions = listOf(
                    TopBarAction(icon = WaiveraIcons.Video, contentDescription = "Start a call", onClick = {
                    }),
                ),
            )
            WaiveraTopBar(
                title = "Profile",
                onBack = {},
                heroContent = {
                    Text(
                        text = "Alex Rivera",
                        style = WaiveraTextStyles.HeroGreeting,
                        color = WaiveraHero.colors.onHeader,
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium + WaiveraSpacing.ExtraSmall),
                    )
                },
            )
        }
    }

    @Test
    fun cardGroup() = captureScreenshots(name = "CardGroup") {
        Column(modifier = Modifier.padding(bottom = WaiveraSpacing.Medium)) {
            WaiveraSectionHeader(text = "Preferences")
            WaiveraCardGroup {
                WaiveraListRow(
                    headline = "Notifications",
                    supporting = "Messages, schedule and reminders",
                    onClick = {},
                    showChevron = true,
                    leading = { WaiveraIconTile(icon = WaiveraIcons.Notifications, tone = WaiveraTileTone.Amber) },
                )
                WaiveraInsetDivider(inset = WaiveraSpacing.Gutter + WaiveraSizes.IconTile + WaiveraSpacing.MediumSmall)
                WaiveraSwitchRow(
                    title = "Haptics",
                    checked = true,
                    onCheckedChange = {},
                    leading = { WaiveraIconTile(icon = WaiveraIcons.Check, tone = WaiveraTileTone.Rose) },
                )
            }
        }
    }

    @Test
    fun selectedPills() = captureScreenshots(name = "SelectedPills") {
        Column(
            modifier = Modifier.padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraSegmentedControl(options = listOf("All", "Unread", "Channels"), selectedIndex = 0, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraFilterChip(label = "All", selected = true, onClick = {})
                WaiveraFilterChip(label = "Messages", selected = false, onClick = {})
                WaiveraFilterChip(label = "Unread", selected = true, onClick = {}, leadingIcon = WaiveraIcons.Check)
            }
            WaiveraFilterChip(label = "Threads", selected = false, onClick = {}, enabled = false)
        }
    }

    @Test
    fun snackbarOnPlum() = captureScreenshots(name = "SnackbarPlum") {
        Column {
            WaiveraSnackbar(message = "Bookmark removed", actionLabel = "Undo")
            WaiveraSnackbar(message = "Couldn’t save your notification settings. Try again.")
        }
    }

    @Test
    fun confirmationSheets() = captureScreenshots(name = "ConfirmationSheets") {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium)) {
            Column(modifier = sheetFrame) {
                ConfirmationContent(
                    title = "Sign out?",
                    message = "You’ll need your email and password to sign back in.",
                    confirmLabel = "Sign out",
                    dismissLabel = "Cancel",
                    destructive = true,
                    confirmInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
            Column(modifier = sheetFrame) {
                ConfirmationContent(
                    title = "Leave north-team?",
                    message = "You can rejoin from the channel list.",
                    confirmLabel = "Leave",
                    dismissLabel = "Cancel",
                    destructive = false,
                    confirmInProgress = true,
                    confirmInProgressLabel = "Leaving…",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}
