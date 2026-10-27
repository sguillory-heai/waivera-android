package com.waivera.waivera.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConfirmationContent
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.MilestoneKind
import com.waivera.waivera.core.designsystem.component.NavigationBarItemModel
import com.waivera.waivera.core.designsystem.component.PresenceStatus
import com.waivera.waivera.core.designsystem.component.SkeletonList
import com.waivera.waivera.core.designsystem.component.SyncState
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCountBadge
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingScreen
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingState
import com.waivera.waivera.core.designsystem.component.WaiveraNavigationBar
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraSheetContent
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbar
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraSwitchRow
import com.waivera.waivera.core.designsystem.component.WaiveraSyncIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One capture per design-system component across the shared matrix
 * (light/dark × small/large phone × 100%/200% font). Sample copy here is test data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class DesignSystemScreenshotTest {

    @Test
    fun buttons() = capture("Buttons") {
        WaiveraButton(text = "Send", onClick = {}, fullWidth = true)
        WaiveraButton(text = "Send", onClick = {}, loading = true, fullWidth = true)
        WaiveraButton(text = "Cancel", onClick = {}, variant = WaiveraButtonVariant.Secondary, fullWidth = true)
        WaiveraButton(
            text = "Delete message",
            onClick = {},
            variant = WaiveraButtonVariant.Destructive,
            fullWidth = true,
        )
        WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text)
        WaiveraButton(text = "New chat", onClick = {}, leadingIcon = WaiveraIcons.Add, enabled = false)
    }

    @Test
    fun iconButtons() = capture("IconButtons") {
        Row {
            WaiveraIconButton(icon = WaiveraIcons.Back, contentDescription = "Go back", onClick = {})
            WaiveraIconButton(icon = WaiveraIcons.Add, contentDescription = "New chat", onClick = {})
            WaiveraIconButton(icon = WaiveraIcons.Search, contentDescription = "Search", onClick = {}, enabled = false)
        }
    }

    @Test
    fun listRows() = capture("ListRows") {
        WaiveraSectionHeader(text = "Settings")
        WaiveraListRow(
            headline = "Notifications",
            supporting = "Messages, schedule changes and reminders",
            onClick = {},
            showChevron = true,
            leading = { WaiveraIconTile(icon = WaiveraIcons.Notifications) },
        )
        WaiveraDivider()
        WaiveraListRow(
            headline = "Alex Rivera",
            supporting = "can you cover Friday? I have a dentist appointment in the morning",
            onClick = {},
            leading = { WaiveraAvatar(name = "Alex Rivera", contentDescription = null) },
            trailing = { WaiveraCountBadge(count = 3) },
        )
        WaiveraListRow(headline = "Disabled row", supporting = "Turn on push notifications first", enabled = false)
    }

    @Test
    fun switchRows() = capture("SwitchRows") {
        WaiveraSwitchRow(
            title = "Haptics",
            subtitle = "Vibrate for sends, errors and toggles",
            checked = true,
            onCheckedChange = {},
        )
        WaiveraSwitchRow(title = "Group activity", checked = false, onCheckedChange = {})
        WaiveraSwitchRow(title = "Mentions", checked = true, onCheckedChange = {}, enabled = false)
    }

    @Test
    fun avatars() = capture("Avatars") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraAvatar(name = "Alex Rivera", presence = PresenceStatus.Online)
            WaiveraAvatar(name = "Priya Raman", milestone = MilestoneKind.Birthday, rounded = false)
            WaiveraAvatar(name = "Sam Lee", milestone = MilestoneKind.WorkAnniversary, presence = PresenceStatus.Away)
            WaiveraAvatar(name = "Jo", milestone = MilestoneKind.NewHire, size = WaiveraSizes.AvatarSmall)
            WaiveraAvatar(name = "", size = WaiveraSizes.AvatarLarge, presence = PresenceStatus.Offline)
        }
    }

    @Test
    fun badges() = capture("Badges") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraCountBadge(count = 3)
            WaiveraCountBadge(count = 42)
            WaiveraCountBadge(count = 120)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            BadgeTone.entries.forEach { tone -> WaiveraStatusBadge(text = tone.name, tone = tone) }
        }
    }

    @Test
    fun chipsAndSegmented() = capture("ChipsSegmented") {
        Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            WaiveraFilterChip(label = "All", selected = true, onClick = {})
            WaiveraFilterChip(label = "Mentions", selected = false, onClick = {})
            WaiveraFilterChip(label = "Unread", selected = false, onClick = {})
        }
        WaiveraSegmentedControl(options = listOf("Day", "Week", "Month"), selectedIndex = 1, onSelect = {})
        WaiveraSegmentedControl(options = listOf("All", "Unread", "Channels"), selectedIndex = 0, onSelect = {})
    }

    @Test
    fun textFields() = capture("TextFields") {
        WaiveraTextField(value = "", onValueChange = {}, label = "Email", placeholder = "you@company.com")
        WaiveraTextField(value = "alex@waivera.test", onValueChange = {}, label = "Email")
        WaiveraTextField(
            value = "secret",
            onValueChange = {},
            label = "Password",
            errorText = "That email and password don't match. Try again.",
            visualTransformation = PasswordVisualTransformation(),
        )
        WaiveraTextField(value = "Read only", onValueChange = {}, label = "Organization", enabled = false)
    }

    @Test
    fun topBars() = capture("TopBars", padded = false) {
        WaiveraTopBar(
            title = "Today",
            subtitle = "Tuesday, October 27",
            account = TopBarAccount(name = "Alex Rivera", onClick = {}),
        )
        WaiveraTopBar(
            title = "Chat",
            actions = listOf(TopBarAction(icon = WaiveraIcons.Add, contentDescription = "New chat", onClick = {})),
            account = TopBarAccount(name = "Alex Rivera", onClick = {}),
        )
        WaiveraTopBar(
            title = "North team check-in with a much longer conversation title",
            subtitle = "6 members",
            onBack = {},
        )
    }

    @Test
    fun navigationBar() = capture("NavigationBar", padded = false) {
        WaiveraNavigationBar(
            items = listOf(
                NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat, badgeCount = 4),
                NavigationBarItemModel(key = "schedule", label = "Schedule", icon = WaiveraIcons.Calendar),
            ),
            selectedKey = "chat",
            onSelect = {},
        )
    }

    @Test
    fun bottomSheetContent() = capture("BottomSheet") {
        WaiveraSheetContent(title = "Account") {
            WaiveraListRow(
                headline = "Profile",
                onClick = {},
                leading = { WaiveraIconTile(icon = WaiveraIcons.Person) },
            )
            WaiveraListRow(headline = "People", onClick = {}, leading = { WaiveraIconTile(icon = WaiveraIcons.People) })
            WaiveraListRow(
                headline = "Bookmarks",
                onClick = {},
                leading = { WaiveraIconTile(icon = WaiveraIcons.Bookmark) },
            )
        }
    }

    @Test
    fun confirmationSheet() = capture("ConfirmationSheet") {
        ConfirmationContent(
            title = "Delete message?",
            message = "This removes the message for everyone in the conversation.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            confirmInProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
        ConfirmationContent(
            title = "Sign out?",
            message = "You'll stop getting notifications on this device until you sign in again.",
            confirmLabel = "Sign out",
            dismissLabel = "Cancel",
            destructive = true,
            confirmInProgress = true,
            onConfirm = {},
            onDismiss = {},
        )
    }

    @Test
    fun snackbar() = capture("Snackbar") {
        WaiveraSnackbar(message = "Bookmark removed", actionLabel = "Undo")
        WaiveraSnackbar(message = "Copied")
    }

    @Test
    fun banners() = capture("Banners", padded = false) {
        ConnectionBanner(state = ConnectionBannerState.Reconnecting, onRetry = {})
        ConnectionBanner(state = ConnectionBannerState.Offline, onRetry = {})
        ConnectionBanner(state = ConnectionBannerState.Connecting, onRetry = {})
        WaiveraBanner(
            message = "Message not sent.",
            tone = BannerTone.Error,
            actionLabel = "Retry",
            onAction = {},
            onDismiss = {},
        )
        WaiveraBanner(message = "You can read this channel but not post in it.", tone = BannerTone.Info)
    }

    @Test
    fun skeleton() = capture("Skeleton", padded = false) {
        SkeletonList(rows = 3)
    }

    @Test
    fun states() = capture("States") {
        WaiveraEmptyState(
            title = "All caught up",
            message = "New messages and mentions will show up here.",
            icon = WaiveraIcons.Check,
        )
        WaiveraErrorState(
            message = "We couldn't load your conversations. Check your connection and try again.",
            onRetry = {},
        )
        WaiveraLoadingState(caption = "Loading your schedule…")
    }

    @Test
    fun loadingScreen() = capture("LoadingScreen", padded = false) {
        WaiveraLoadingScreen()
    }

    @Test
    fun syncIndicator() = capture("SyncIndicator") {
        WaiveraSyncIndicator(state = SyncState.Pending, onRetry = {})
        WaiveraSyncIndicator(state = SyncState.Failed, onRetry = {})
        WaiveraSyncIndicator(state = SyncState.Synced, onRetry = {})
    }

    private fun capture(name: String, padded: Boolean = true, content: @Composable () -> Unit) {
        captureScreenshots(name = name) {
            Column(
                modifier = if (padded) Modifier.padding(WaiveraSpacing.Medium) else Modifier,
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                content()
            }
        }
    }
}
