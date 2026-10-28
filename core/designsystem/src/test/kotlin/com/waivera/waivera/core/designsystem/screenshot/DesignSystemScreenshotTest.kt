package com.waivera.waivera.core.designsystem.screenshot

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConfirmationContent
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.MilestoneBadge
import com.waivera.waivera.core.designsystem.component.MilestoneKind
import com.waivera.waivera.core.designsystem.component.NavigationBarItemModel
import com.waivera.waivera.core.designsystem.component.SkeletonList
import com.waivera.waivera.core.designsystem.component.SyncState
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomActionBar
import com.waivera.waivera.core.designsystem.component.WaiveraBottomActionLayout
import com.waivera.waivera.core.designsystem.component.WaiveraBrandSurface
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCelebrationBurst
import com.waivera.waivera.core.designsystem.component.WaiveraCelebrationNote
import com.waivera.waivera.core.designsystem.component.WaiveraCountBadge
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraEmojiTile
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingScreen
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingState
import com.waivera.waivera.core.designsystem.component.WaiveraLogo
import com.waivera.waivera.core.designsystem.component.WaiveraLogoTile
import com.waivera.waivera.core.designsystem.component.WaiveraLogoVariant
import com.waivera.waivera.core.designsystem.component.WaiveraMilestoneAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraNavigationBar
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.component.WaiveraSectionCount
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraSheetContent
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbar
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraSwitchRow
import com.waivera.waivera.core.designsystem.component.WaiveraSyncIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
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
        WaiveraButton(text = "Sign out", onClick = {}, loadingText = "Signing out…", fullWidth = true)
        WaiveraButton(text = "Sign in", onClick = {}, enabled = false, fullWidth = true)
        WaiveraButton(text = "Cancel", onClick = {}, variant = WaiveraButtonVariant.Secondary, fullWidth = true)
        // An outlined action's glyph takes the accent, never a heavy near-black fill.
        WaiveraButton(
            text = "Message organizer",
            onClick = {},
            variant = WaiveraButtonVariant.Secondary,
            leadingIcon = WaiveraIcons.Chat,
            fullWidth = true,
        )
        WaiveraButton(text = "Join on web", onClick = {}, variant = WaiveraButtonVariant.Tonal)
        WaiveraButton(
            text = "Delete message",
            onClick = {},
            variant = WaiveraButtonVariant.Destructive,
            fullWidth = true,
        )
        WaiveraButton(
            text = "Sign out",
            onClick = {},
            variant = WaiveraButtonVariant.DestructiveOutline,
            fullWidth = true,
        )
        WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text)
        WaiveraButton(text = "New chat", onClick = {}, leadingIcon = WaiveraIcons.Add, enabled = false)
    }

    /** A single-choice pair after an answer: the chosen side is the heavier in both themes (UI4-04). */
    @Test
    fun choicePair() = capture("ChoicePair") {
        WaiveraButton(text = "Not going", onClick = {}, variant = WaiveraButtonVariant.Unselected, fullWidth = true)
        WaiveraButton(
            text = "Going",
            onClick = {},
            variant = WaiveraButtonVariant.Selected,
            leadingIcon = WaiveraIcons.Check,
            fullWidth = true,
        )
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
        WaiveraListRow(
            headline = "Jordan Lee",
            supporting = "5-year anniversary",
            detail = "jordan.castellanos-whitaker@example.com",
            onClick = {},
            showChevron = true,
        )
    }

    /** The real logo: mark and lockup, gradient loop with text-coloured lines in both themes. */
    @Test
    fun logo() = capture("Logo") {
        WaiveraLogo()
        WaiveraLogo(variant = WaiveraLogoVariant.Lockup, height = WaiveraSizes.BrandLockupHero)
        Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            WaiveraLogo(height = WaiveraSizes.Icon)
            WaiveraLogo(variant = WaiveraLogoVariant.Lockup, height = WaiveraSizes.BrandLockup)
        }
    }

    /** One icon language: every tone, plus emoji tiles, in light and dark. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun iconTiles() = capture("IconTiles") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraIconTile(icon = WaiveraIcons.Chat)
            WaiveraIconTile(icon = WaiveraIcons.Bookmark, tone = WaiveraTileTone.Rose)
            WaiveraIconTile(icon = WaiveraIcons.Notifications, tone = WaiveraTileTone.Amber)
            WaiveraIconTile(icon = WaiveraIcons.Calendar, tone = WaiveraTileTone.Teal)
            WaiveraIconTile(icon = WaiveraIcons.Archive, tone = WaiveraTileTone.Neutral)
            WaiveraIconTile(icon = WaiveraIcons.Error, tone = WaiveraTileTone.Danger)
            WaiveraEmojiTile(emoji = "🎂")
            WaiveraEmojiTile(emoji = "🎉", tone = WaiveraTileTone.Brand)
            WaiveraEmojiTile(emoji = "👋", tone = WaiveraTileTone.Brand)
            WaiveraLogoTile()
            WaiveraLogoTile(size = WaiveraSizes.AvatarSmall, logoHeight = WaiveraSizes.IconSmall)
            WaiveraIconTile(
                icon = WaiveraIcons.Error,
                tone = WaiveraTileTone.Neutral,
                size = WaiveraSizes.IconTileSmall,
                iconSize = WaiveraSizes.IconTileSmallGlyph,
            )
        }
        WaiveraListRow(
            headline = "Bookmarks",
            onClick = {},
            showChevron = true,
            leading = { WaiveraIconTile(icon = WaiveraIcons.Bookmark, tone = WaiveraTileTone.Rose) },
        )
    }

    /** Empty states with their brand illustrations and an inviting action. */
    @Test
    fun illustratedEmptyStates() = capture("EmptyStatesIllustrated") {
        WaiveraEmptyState(
            title = "No conversations yet",
            message = "Say hello to a teammate and it will show up here.",
            illustration = WaiveraIllustration.Chats,
            actionLabel = "New chat",
            onAction = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            WaiveraIllustration.entries.forEach { art ->
                Image(
                    painter = painterResource(art.drawable),
                    contentDescription = null,
                    modifier = Modifier.size(WaiveraSizes.IconTile),
                )
            }
        }
    }

    /** The brand wash behind the lockup. The confetti waits for its clock, so a capture shows the wash alone. */
    @Test
    fun brandWash() = capture("BrandWash") {
        Box(
            modifier = Modifier
                .height(WaiveraSizes.HeroBand)
                .fillMaxWidth()
                .clip(WaiveraShapes.Hero)
                .background(WaiveraTheme.colors.surface)
                .background(WaiveraBrand.wash()),
            contentAlignment = Alignment.Center,
        ) {
            WaiveraLogo(variant = WaiveraLogoVariant.Lockup, height = WaiveraSizes.BrandLockupHero)
            WaiveraCelebrationBurst(trigger = Unit, modifier = Modifier.matchParentSize())
        }
    }

    /** The wash container for brand moments and the celebration note with its emoji tile. */
    @Test
    fun brandSurfaces() = capture("BrandSurfaces") {
        WaiveraBrandSurface {
            WaiveraSectionHeader(text = "Event")
            WaiveraListRow(
                headline = "Mon, Nov 2, 9:00 AM",
                supporting = "Until 10:00 AM",
                leading = { WaiveraIconTile(icon = WaiveraIcons.Schedule, tone = WaiveraTileTone.Teal) },
            )
        }
        WaiveraCelebrationNote(emoji = "🎂", text = "Happy birthday, Priya Raman!")
        WaiveraCelebrationNote(
            emoji = "🎉",
            text = "Happy 5-year work anniversary, Jordan Lee!",
            tone = WaiveraTileTone.Brand,
            centered = true,
        )
    }

    @Test
    fun switchRows() = capture("SwitchRows") {
        WaiveraSwitchRow(
            title = "Haptics",
            subtitle = "Vibrate for failed sends, errors and toggles",
            checked = true,
            onCheckedChange = {},
        )
        WaiveraSwitchRow(title = "Group activity", checked = false, onCheckedChange = {})
        WaiveraSwitchRow(
            title = "Schedule reminders",
            subtitle = "Shifts and events starting soon.",
            checked = true,
            onCheckedChange = {},
            leading = { WaiveraIconTile(icon = WaiveraIcons.Schedule, tone = WaiveraTileTone.Teal) },
        )
        // Blocked by Android while on: the muted accent track (UI3-12), then blocked while off.
        WaiveraSwitchRow(title = "Mentions", checked = true, onCheckedChange = {}, enabled = false)
        WaiveraSwitchRow(title = "Digest", checked = false, onCheckedChange = {}, enabled = false)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun avatars() = capture("Avatars") {
        // Wraps instead of clipping the last avatar at the frame edge on small phones and large font (UI2-31).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraAvatar(name = "Alex Rivera")
            WaiveraAvatar(name = "Priya Raman", milestone = MilestoneKind.Birthday)
            WaiveraAvatar(name = "Sam Lee", milestone = MilestoneKind.WorkAnniversary)
            WaiveraAvatar(name = "Jo", milestone = MilestoneKind.NewHire, size = WaiveraSizes.AvatarSmall)
            WaiveraAvatar(name = "", size = WaiveraSizes.AvatarLarge)
            WaiveraAvatar(name = "Jordan Lee")
            // The hero size, as the profile and person heroes draw it.
            WaiveraAvatar(name = "Alex Rivera", size = WaiveraSizes.AvatarHero)
        }
    }

    /** The photo path: crop over a real image in the one rounded-square shape, beside initials. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun avatarsWithPhoto() = capture("AvatarsPhoto") {
        val photo = remember { photoPainter() }
        // Wraps instead of clipping the last avatar at the frame edge on small phones and large font (UI2-31).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraAvatar(name = "Priya Raman", painter = photo)
            WaiveraAvatar(name = "Priya Raman", painter = photo, milestone = MilestoneKind.Birthday)
            WaiveraAvatar(name = "Priya Raman", painter = photo, size = WaiveraSizes.AvatarLarge)
            WaiveraAvatar(name = "Priya Raman", painter = photo, size = WaiveraSizes.AvatarSmall)
            WaiveraMilestoneAvatar(
                "Priya Raman",
                MilestoneBadge(MilestoneKind.WorkAnniversary, years = 5),
                WaiveraSizes.AvatarMedium,
                painter = photo,
            )
            WaiveraAvatar(name = "Jordan Lee")
        }
    }

    @Test
    fun milestoneAvatars() = capture("MilestoneAvatars") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraMilestoneAvatar("Priya Raman", MilestoneBadge(MilestoneKind.Birthday), WaiveraSizes.AvatarMedium)
            WaiveraMilestoneAvatar(
                "Jordan Lee",
                MilestoneBadge(MilestoneKind.WorkAnniversary, years = 5),
                WaiveraSizes.AvatarMedium,
            )
            WaiveraMilestoneAvatar(
                "Jordan Lee",
                MilestoneBadge(MilestoneKind.WorkAnniversary, years = 5),
                WaiveraSizes.AvatarSmall,
            )
            WaiveraMilestoneAvatar("Sam Ortiz", MilestoneBadge(MilestoneKind.Multiple), WaiveraSizes.AvatarMedium)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun badges() = capture("Badges") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraCountBadge(count = 3)
            WaiveraCountBadge(count = 42)
            WaiveraCountBadge(count = 120)
            // v5: attention is the solid rose; a plain total is the neutral chip.
            WaiveraCountBadge(text = "14", contentDescription = "14 events", tone = WaiveraCountTone.Info)
            WaiveraSectionCount(text = "4", contentDescription = "4 items")
            WaiveraSectionCount(text = "9", contentDescription = "9 total", tone = WaiveraCountTone.Info)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            BadgeTone.entries.forEach { tone -> WaiveraStatusBadge(text = tone.name, tone = tone) }
        }
    }

    @Test
    fun chipsAndSegmented() = capture("ChipsSegmented") {
        // Scrolls sideways as the real chip rows do; a fixed row squeezed the last chip at 200%.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraFilterChip(label = "All", selected = true, onClick = {})
            WaiveraFilterChip(label = "Mentions", selected = false, onClick = {})
            WaiveraFilterChip(label = "Unread", selected = false, onClick = {})
            WaiveraFilterChip(label = "Unread", selected = true, onClick = {}, leadingIcon = WaiveraIcons.Check)
        }
        WaiveraSegmentedControl(options = listOf("Day", "Week", "Month"), selectedIndex = 1, onSelect = {})
        WaiveraSegmentedControl(options = listOf("All", "Unread", "Channels"), selectedIndex = 0, onSelect = {})
    }

    @Test
    fun textFields() = capture("TextFields") {
        WaiveraTextField(value = "", onValueChange = {}, label = "Email", placeholder = "you@company.com")
        WaiveraTextField(
            value = "alex@waivera.test",
            onValueChange = {},
            label = "Email",
            placeholder = "you@company.com",
        )
        WaiveraTextField(
            value = "secret",
            onValueChange = {},
            label = "Password",
            placeholder = "Enter your password",
            errorText = "That email and password don’t match. Try again.",
            visualTransformation = PasswordVisualTransformation(),
        )
        WaiveraTextField(
            value = "Read only",
            onValueChange = {},
            label = "Organization",
            placeholder = "Your organization",
            enabled = false,
        )
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
        WaiveraNavigationBar(
            items = listOf(
                NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat, badgeCount = 120),
                NavigationBarItemModel(key = "schedule", label = "Schedule", icon = WaiveraIcons.Calendar),
            ),
            selectedKey = "today",
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
            message = "You’ll stop getting notifications on this device until you sign in again.",
            confirmLabel = "Sign out",
            dismissLabel = "Cancel",
            destructive = true,
            confirmInProgress = true,
            onConfirm = {},
            onDismiss = {},
            confirmInProgressLabel = "Signing out…",
        )
    }

    @Test
    fun snackbar() = capture("Snackbar") {
        WaiveraSnackbar(message = "Bookmark removed", actionLabel = "Undo")
        WaiveraSnackbar(message = "Copied")
    }

    /**
     * Full banners. At 200% every title and message shows in full; actions with an icon become a
     * 48dp icon button and the rest move under the text (UI4-01).
     */
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
            actionIcon = WaiveraIcons.Refresh,
            onDismiss = {},
        )
        WaiveraBanner(message = "You can read this channel but not post in it.", tone = BannerTone.Info)
        // A long action label: it must move under the text instead of squeezing it.
        WaiveraBanner(
            title = "Notifications are off for Waivera",
            message = "Android is blocking notifications from this app, so nothing below will reach you.",
            tone = BannerTone.Warning,
            actionLabel = "Open Android notification settings",
            onAction = {},
            actionIcon = WaiveraIcons.OpenExternal,
        )
        WaiveraBanner(
            title = "Couldn’t sign in",
            message = "You appear to be offline. Check your connection and try again.",
            tone = BannerTone.Error,
            onDismiss = {},
        )
    }

    /**
     * A full banner whose message would take more than two lines beside a short action: the
     * action moves under the text so the message keeps the width (UI4-06).
     */
    @Test
    fun bannersLongMessage() = capture("BannersLongMessage", padded = false) {
        WaiveraBanner(
            title = "You’re offline",
            message = "Showing your saved conversations. New messages arrive when you’re back online.",
            tone = BannerTone.Offline,
            actionLabel = "Retry now",
            onAction = {},
            actionIcon = WaiveraIcons.Refresh,
        )
        WaiveraBanner(
            message = "Couldn’t refresh. Showing saved conversations.",
            tone = BannerTone.Warning,
            actionLabel = "Retry",
            onAction = {},
            actionIcon = WaiveraIcons.Refresh,
        )
    }

    /**
     * The compact form status banners take above 130% font, forced here at every scale. Titles
     * wrap instead of being cut, a chevron shows the folded detail, and at 200% the action is an
     * icon button (UI3-08, UI4-01).
     */
    @Test
    fun bannersCompact() = capture("BannersCompact", padded = false) {
        WaiveraBanner(
            title = "You’re offline",
            message = "Showing your saved details.",
            tone = BannerTone.Offline,
            actionLabel = "Retry now",
            onAction = {},
            actionIcon = WaiveraIcons.Refresh,
            compact = true,
        )
        WaiveraBanner(
            title = "Couldn’t refresh your schedule",
            message = "Showing what was saved.",
            tone = BannerTone.Warning,
            actionLabel = "Retry",
            onAction = {},
            actionIcon = WaiveraIcons.Refresh,
            compact = true,
        )
        WaiveraBanner(
            title = "Still can’t reach Waivera after several tries",
            message = "Showing saved conversations. Pull down to try again.",
            tone = BannerTone.Offline,
            onDismiss = {},
            compact = true,
        )
    }

    @Test
    fun bottomActionLayoutFits() = capture("BottomActionLayoutFits", padded = false) {
        BottomActionLayoutSample(fields = 1)
    }

    /**
     * Too tall for the small phone. At 100% the actions stay pinned and the fade sits above them,
     * over the content; at 200% they follow the content and the fade only shows while they are
     * entirely below the edge.
     */
    @Test
    fun bottomActionLayoutOverflows() = capture("BottomActionLayoutOverflows", padded = false) {
        BottomActionLayoutSample(fields = 6)
    }

    @Composable
    private fun BottomActionLayoutSample(fields: Int) {
        Box(Modifier.height(BOTTOM_LAYOUT_SAMPLE_HEIGHT)) {
            WaiveraBottomActionLayout(
                actions = {
                    WaiveraButton(text = "Sign in", onClick = {}, fullWidth = true)
                    WaiveraButton(
                        text = "Forgot password?",
                        onClick = {},
                        variant = WaiveraButtonVariant.Text,
                        fullWidth = true,
                    )
                },
            ) {
                repeat(fields) { index ->
                    WaiveraTextField(
                        value = "",
                        onValueChange = {},
                        label = "Field ${index + 1}",
                        placeholder = "Type here",
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
                    )
                }
            }
        }
    }

    @Test
    fun bottomActionBar() = capture("BottomActionBar", padded = false) {
        WaiveraBottomActionBar {
            WaiveraButton(text = "Turn on notifications", onClick = {}, fullWidth = true)
            WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text, fullWidth = true)
        }
        WaiveraBottomActionBar(showDivider = false) {
            WaiveraButton(
                text = "Update password",
                onClick = {},
                loadingText = "Updating your password…",
                fullWidth = true,
            )
        }
    }

    @Test
    fun skeleton() = capture("Skeleton", padded = false) {
        SkeletonList(rows = 3)
    }

    /** Captures run with "Remove animations", so this is the still ring a person sees then (UI3-09). */
    @Test
    fun busyIndicators() = capture("BusyIndicators") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraBusyIndicator()
            WaiveraBusyIndicator(size = WaiveraSizes.ProgressLarge, strokeWidth = WaiveraSizes.ProgressStrokeLarge)
        }
        WaiveraButton(text = "Sign in", onClick = {}, loadingText = "Connecting to Waivera…", fullWidth = true)
    }

    @Test
    fun states() = capture("States") {
        WaiveraEmptyState(
            title = "All caught up",
            message = "New messages and mentions will show up here.",
            icon = WaiveraIcons.Check,
        )
        WaiveraErrorState(
            message = "We couldn’t load your conversations. Check your connection and try again.",
            onRetry = {},
        )
        WaiveraLoadingState(caption = "Loading your schedule…")
    }

    @Test
    fun loadingScreen() = capture("LoadingScreen", padded = false) {
        WaiveraLoadingScreen()
    }

    @Test
    fun placeholderScreen() = capture("PlaceholderScreen", padded = false) {
        WaiveraPlaceholderScreen(
            title = "Schedule",
            message = "Your shifts and events will appear here.",
            icon = WaiveraIcons.Calendar,
            onBack = {},
        )
    }

    @Test
    fun syncIndicator() = capture("SyncIndicator") {
        WaiveraSyncIndicator(state = SyncState.Pending, onRetry = {})
        WaiveraSyncIndicator(state = SyncState.Failed, onRetry = {})
        WaiveraSyncIndicator(state = SyncState.Synced, onRetry = {})
    }

    private companion object {
        val BOTTOM_LAYOUT_SAMPLE_HEIGHT = 560.dp
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

    private fun photoPainter(): BitmapPainter {
        // An abstract 128px test image (shapes, no face), the same one the demo serves as an avatar.
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream("avatar-photo.png")).use { it.readBytes() }
        return BitmapPainter(BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap())
    }
}
