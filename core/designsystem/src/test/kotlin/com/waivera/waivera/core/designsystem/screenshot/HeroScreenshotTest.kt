package com.waivera.waivera.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.WaiveraDayTimeline
import com.waivera.waivera.core.designsystem.component.WaiveraGradientButton
import com.waivera.waivera.core.designsystem.component.WaiveraGradientButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraHeroBackdrop
import com.waivera.waivera.core.designsystem.component.WaiveraHeroCard
import com.waivera.waivera.core.designsystem.component.WaiveraHeroHeader
import com.waivera.waivera.core.designsystem.component.WaiveraHeroLogo
import com.waivera.waivera.core.designsystem.component.WaiveraHeroSection
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTopBar
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTrail
import com.waivera.waivera.core.designsystem.component.WaiveraProgressRing
import com.waivera.waivera.core.designsystem.component.WaiveraRewardRing
import com.waivera.waivera.core.designsystem.component.WaiveraSectionAction
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSheetCard
import com.waivera.waivera.core.designsystem.component.WaiveraStatRow
import com.waivera.waivera.core.designsystem.component.WaiveraStatTile
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineBlock
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineLabel
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineTone
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
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
 * Concept A's hero and sheet pieces across the matrix: the sign-in header with its trails, the
 * gradient CTA, the tab hero (bar, glass card, ring, stat tiles, sheet lip), the reward ring, and
 * the sheet's section header, card, day track and field.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class HeroScreenshotTest {
    @Test
    fun heroHeader() = captureScreenshots(name = "HeroHeader") {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium)) {
            WaiveraHeroHeader(modifier = Modifier.height(WaiveraHero.HeaderHeight)) {
                WaiveraHeroLogo(height = WaiveraHero.Lockup)
            }
            WaiveraHeroHeader(modifier = Modifier.height(WaiveraHero.HeaderHeightCompact)) {
                WaiveraHeroLogo(height = WaiveraHero.LockupCompact)
            }
        }
    }

    @Test
    fun gradientButton() = captureScreenshots(name = "GradientButton") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WaiveraHero.colors.sheet)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraGradientButton(text = "Sign in", onClick = {})
            WaiveraGradientButton(text = "Sign in", onClick = {}, loadingText = "Signing you in…")
            WaiveraGradientButton(text = "Sign in", onClick = {}, enabled = false)
            WaiveraGradientButton(text = "Accept", onClick = {}, size = WaiveraGradientButtonSize.Compact)
        }
    }

    /** The tab hero with its bar, the glass card with the ring and the stat tiles, and the sheet's lip. */
    @Test
    fun tabHero() = captureScreenshots(name = "TabHero") {
        Column(modifier = Modifier.background(WaiveraTheme.colors.background)) {
            WaiveraHeroSection(
                topBar = {
                    WaiveraHeroTopBar(title = "Today", account = TopBarAccount(name = "Alex Rivera", onClick = {}))
                },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                ) {
                    WaiveraHeroCard {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WaiveraProgressRing(
                                done = 5,
                                total = 9,
                                caption = "handled",
                                contentDescription = "5 of 9 handled today",
                            )
                            Text(
                                text = "4 things need you",
                                style = WaiveraTextStyles.HeroTitle,
                                color = WaiveraHero.colors.onHeader,
                            )
                        }
                    }
                    WaiveraStatRow {
                        WaiveraStatTile(value = "3", label = "events today", contentDescription = "3 events today")
                        WaiveraStatTile(value = "2", label = "unread chats", contentDescription = "2 unread chats")
                        WaiveraStatTile(
                            value = "14",
                            label = "events this week",
                            contentDescription = "14 events this week",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun rewardRing() = captureScreenshots(name = "RewardRing") {
        WaiveraHeroBackdrop(trail = WaiveraHeroTrail.Reward, contentAlignment = Alignment.Center) {
            WaiveraRewardRing(contentDescription = null, modifier = Modifier.padding(WaiveraSpacing.Large))
        }
    }

    /** The sheet pieces: a section header with its gradient count and link, a card and the day track. */
    @Test
    fun sheetPieces() = captureScreenshots(name = "SheetPieces") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraSectionHeader(
                title = "Needs you",
                count = 4,
                countDescription = "4 items",
                action = WaiveraSectionAction(label = "Show all", onClick = {}),
            )
            WaiveraSheetCard {
                Text(text = "Care plan review", style = MaterialTheme.typography.titleSmall)
            }
            WaiveraDayTimeline(
                title = "Your day",
                summary = "3 events · 2 hr scheduled",
                trackDescription = "3 events between 9 AM and 3 PM",
                blocks = listOf(
                    WaiveraTimelineBlock(start = 0.1f, end = 0.2f, tone = WaiveraTimelineTone.Teal),
                    WaiveraTimelineBlock(start = 0.3f, end = 0.4f, tone = WaiveraTimelineTone.Violet),
                    WaiveraTimelineBlock(start = 0.6f, end = 0.7f, tone = WaiveraTimelineTone.Gold),
                ),
                ticks = (1..9).map { it / 10f },
                labels = listOf(
                    WaiveraTimelineLabel(at = 0f, text = "8 AM"),
                    WaiveraTimelineLabel(at = 0.4f, text = "12 PM"),
                    WaiveraTimelineLabel(at = 1f, text = "6 PM"),
                ),
                now = 0.25f,
            )
            WaiveraTextField(value = "", onValueChange = {}, label = "Email", placeholder = "you@organization.com")
        }
    }
}
