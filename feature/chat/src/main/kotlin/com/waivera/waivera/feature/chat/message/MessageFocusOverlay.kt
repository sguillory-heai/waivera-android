package com.waivera.waivera.feature.chat.message

import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.popScale
import com.waivera.waivera.core.designsystem.component.rememberPopScale
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.chat.R
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the focus overlay's controls do; every one but Copy closes the overlay through the view model. */
@Immutable
class MessageFocusActions(
    val onDismiss: () -> Unit,
    val onReact: (String) -> Unit,
    val onReply: () -> Unit,
    val onCopy: () -> Unit,
    val onEdit: () -> Unit,
    val onBookmark: () -> Unit,
    val onDelete: () -> Unit,
    val onLink: (String) -> Unit,
) {
    companion object {
        val None = MessageFocusActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * The long press on a message (owner's pick, option B "focus mode"; conversations and threads
 * alike): the chat dims, the pressed bubble lifts where it is, the six quick reactions float above
 * it in one pill and a short menu sits under it, or beside it on a short window. It replaces the
 * old actions sheet, whose actions a long message pushed out of reach (wave 28 B1) and whose emoji
 * row overflowed its gutter (B2).
 *
 * It is its own full-window dialog, as the sheets and the image preview it sits among are, rather
 * than a Popup or a layer in the screen: a dialog window covers the top bar and the composer, is
 * handed the real insets (status bar, navigation bar, cutout, keyboard) with
 * `decorFitsSystemWindows = false`, takes back away from the screen so back closes only the
 * overlay, and is a new window, so TalkBack moves into it. A Popup gets no insets, and a layer in
 * the screen would sit under the app bar's window and share the screen's back handling.
 *
 * [anchorOnScreen] is where the bubble was pressed, in screen pixels, from [MessageFocusAnchors].
 */
@Composable
internal fun MessageFocusOverlay(
    model: MessageActionsModel,
    mine: Boolean,
    anchorOnScreen: Rect?,
    actions: MessageFocusActions,
) {
    Dialog(
        onDismissRequest = actions.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        // The overlay draws its own scrim and its own entrance, so the window adds neither.
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        val anchor = remember(anchorOnScreen, view) {
            anchorOnScreen?.let {
                val origin = IntArray(2)
                view.getLocationOnScreen(origin)
                it.translate(-origin[0].toFloat(), -origin[1].toFloat())
            }
        }
        var back by remember { mutableFloatStateOf(0f) }
        val latest by rememberUpdatedState(actions)
        // Predictive back, as everywhere else in the app: the overlay follows the swipe and
        // closes when it commits. A plain back press arrives as an empty swipe and closes it too.
        PredictiveBackHandler { swipe ->
            try {
                swipe.collect { back = it.progress }
                latest.onDismiss()
            } catch (cancelled: CancellationException) {
                back = 0f
                throw cancelled
            }
        }
        MessageFocusLayer(model = model, mine = mine, actions = actions, anchor = anchor, backProgress = { back })
    }
}

/**
 * The overlay's content without its window: the scrim, the lifted bubble, the reaction pill and
 * the menu, placed by [FocusGeometry] inside the safe drawing area. For previews and screenshot
 * tests too. [anchor] is the pressed bubble in this layer's pixels; without it the bubble sits
 * just above the menu at the bottom, on its author's side.
 */
@Suppress("LongMethod")
@Composable
fun MessageFocusLayer(
    model: MessageActionsModel,
    mine: Boolean,
    actions: MessageFocusActions,
    modifier: Modifier = Modifier,
    anchor: Rect? = null,
    backProgress: () -> Float = { 0f },
) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val entrance = remember(motion) { focusEntrance(motion) }
    val appear = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val latest by rememberUpdatedState(actions)
    LaunchedEffect(entrance) {
        launch { appear.animateTo(1f, entrance.fade) }
        lift.animateTo(1f, entrance.lift)
    }
    // Copied: the row's glyph pops into a check and reads "Copied", then the overlay closes on its own.
    LaunchedEffect(model.copied) {
        if (model.copied) {
            delay(ChatDimens.FOCUS_COPIED_HOLD_MILLIS)
            latest.onDismiss()
        }
    }
    val paneLabel = stringResource(R.string.feature_chat_open_actions)
    val closeLabel = stringResource(R.string.feature_chat_action_close)
    val large = isLargeFontScale()
    val insets = WindowInsets.safeDrawing
    val anchorPx = anchor?.roundToIntRect()
    val backEasing = motion.gesture.progress
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                paneTitle = paneLabel
                isTraversalGroup = true
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(colors.focusScrim, alpha = appear.value * (1f - backProgress())) }
                .pointerInput(Unit) { detectTapGestures { latest.onDismiss() } }
                .semantics {
                    contentDescription = closeLabel
                    role = Role.Button
                    // After the menu and the message: TalkBack starts in the menu.
                    traversalIndex = 1f
                    onClick {
                        latest.onDismiss()
                        true
                    }
                },
        )
        Layout(
            content = {
                if (model.canReact) {
                    ReactionPill(onReact = actions.onReact, modifier = Modifier.layoutId(FocusPart.Pill))
                }
                LiftedBubble(
                    model = model,
                    mine = mine,
                    onLink = actions.onLink,
                    modifier = Modifier.layoutId(FocusPart.Bubble),
                )
                ActionMenu(
                    model = model,
                    actions = actions,
                    modifier = Modifier
                        .layoutId(FocusPart.Menu)
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = -1f
                        },
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shrink = (1f - WaiveraMotionScheme.BACK_PREVIEW_SCALE) * backEasing.transform(backProgress())
                    scaleX = 1f - shrink
                    scaleY = 1f - shrink
                },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val gap = ChatDimens.FocusGap.roundToPx()
            val gutter = WaiveraSpacing.Gutter.roundToPx()
            val safe = IntRect(
                left = insets.getLeft(this, layoutDirection) + gutter,
                top = insets.getTop(this) + gap,
                right = width - insets.getRight(this, layoutDirection) - gutter,
                bottom = height - insets.getBottom(this) - gap,
            )
            val safeWidth = safe.width.coerceAtLeast(0)
            val pill = measurables.firstOrNull { it.layoutId == FocusPart.Pill }
                ?.measure(Constraints(maxWidth = safeWidth))
            val menuWidth = focusMenuWidth(
                safeWidth = safeWidth,
                pillWidth = pill?.width ?: 0,
                gap = gap,
                target = (if (large) ChatDimens.FocusMenuWidthLarge else ChatDimens.FocusMenuWidth).roundToPx(),
                minBeside = ChatDimens.FocusMenuMinWidth.roundToPx(),
            )
            val menu = measurables.first { it.layoutId == FocusPart.Menu }.measure(Constraints.fixedWidth(menuWidth))
            val geometry = FocusGeometry(
                safe = safe,
                anchor = anchorPx,
                mine = mine,
                pill = pill?.let { IntSize(it.width, it.height) },
                menu = IntSize(menu.width, menu.height),
                gap = gap,
                minBubbleHeight = ChatDimens.FocusMinBubble.roundToPx(),
                maxBubbleHeight = (safe.height * ChatDimens.FOCUS_BUBBLE_MAX_FRACTION).toInt(),
                minBubbleWidth = ChatDimens.FocusMinBubbleWidth.roundToPx(),
                fallbackBubbleWidth = (safeWidth * ChatDimens.BUBBLE_MAX_WIDTH_FRACTION).toInt(),
            )
            val frame = geometry.frame()
            val bubble = measurables.first { it.layoutId == FocusPart.Bubble }.measure(
                Constraints(
                    maxWidth = frame.bubbleMaxWidth.coerceAtLeast(0),
                    maxHeight = frame.bubbleMaxHeight.coerceAtLeast(0),
                ),
            )
            val at = geometry.place(frame, IntSize(bubble.width, bubble.height))
            // The bubble starts where the press left it and springs to where it fits.
            val travel = if (entrance.travels) ((anchorPx?.top ?: at.bubble.y) - at.bubble.y).toFloat() else 0f
            val menuFollows = at.arrangement == FocusArrangement.Stacked

            // Read in each layer, so the spring only redraws; the pill and menu keep clear of the bubble.
            fun lifted() = liftFrame(lift.value, entrance.bubbleFrom, travel, bubble.height, menuFollows)
            val pivotX = if (mine) 1f else 0f
            fun GraphicsLayerScope.piece(pivotY: Float) {
                val scale = entrance.piecesFrom + (1f - entrance.piecesFrom) * lift.value
                scaleX = scale
                scaleY = scale
                alpha = appear.value
                // Out of the bubble's edge: the pill grows up from it, the menu down or across.
                transformOrigin = TransformOrigin(pivotX, pivotY)
            }
            layout(width, height) {
                bubble.placeWithLayer(at.bubble) {
                    val now = lifted()
                    scaleX = now.bubbleScale
                    scaleY = now.bubbleScale
                    translationY = now.bubbleShift
                    alpha = if (entrance.bubbleFades) appear.value else 1f
                }
                val pillAt = at.pill
                if (pill != null && pillAt != null) {
                    // Grown from its bottom edge, the one facing the bubble, so its pop never reaches down.
                    pill.placeWithLayer(pillAt) {
                        piece(pivotY = 1f)
                        translationY = lifted().pillShift
                    }
                }
                menu.placeWithLayer(at.menu) {
                    piece(pivotY = 0f)
                    translationY = lifted().menuShift
                }
            }
        }
    }
}

private enum class FocusPart { Pill, Bubble, Menu }

/** The lifted bubble and the reaction pill, for tests that follow them through the entrance. */
internal const val FOCUS_BUBBLE_TAG = "message_focus_bubble"
internal const val FOCUS_PILL_TAG = "message_focus_pill"

/** The lifted bubble's scrolling body and its top and bottom fades. */
internal const val FOCUS_BUBBLE_SCROLL_TAG = "message_focus_bubble_scroll"
internal const val FOCUS_FADE_TOP_TAG = "message_focus_fade_top"
internal const val FOCUS_FADE_BOTTOM_TAG = "message_focus_fade_bottom"

/**
 * How the overlay arrives (WaiveraMotion). With motion on, the bubble starts at the long press's
 * sink ([PressDepth.longPressScale]) where it was pressed and lifts to its place on the bouncy
 * pop spring, while the pill and the menu scale out of its edge and fade in, riding its edges
 * through the spring so they never cover it ([liftFrame]). With animations removed nothing moves
 * or scales: the overlay only fades in, the bubble with it.
 */
@Immutable
internal class FocusEntrance(
    val bubbleFrom: Float,
    val piecesFrom: Float,
    val travels: Boolean,
    val bubbleFades: Boolean,
    val lift: FiniteAnimationSpec<Float>,
    val fade: FiniteAnimationSpec<Float>,
)

internal fun focusEntrance(motion: WaiveraMotionScheme): FocusEntrance = if (motion.reduced) {
    FocusEntrance(
        bubbleFrom = 1f,
        piecesFrom = 1f,
        travels = false,
        bubbleFades = true,
        lift = snap(),
        fade = motion.state(),
    )
} else {
    FocusEntrance(
        bubbleFrom = PressDepth.Button.longPressScale,
        piecesFrom = WaiveraMotionScheme.POP_START_SCALE,
        travels = true,
        bubbleFades = false,
        lift = motion.pop,
        fade = motion.state(),
    )
}

/**
 * The pressed message, drawn as its bubble with the body only (no reactions, thread chip or
 * time). A long one is clamped to the height it is given and scrolls inside itself, with fades
 * that say there is more ([ScrollingBubbleBody], v7 item 2). TalkBack reads it whole.
 */
@Composable
private fun LiftedBubble(model: MessageActionsModel, mine: Boolean, onLink: (String) -> Unit, modifier: Modifier) {
    val colors = WaiveraTheme.colors
    val message = model.message
    val shape = ChatDimens.bubbleShape(isMine = mine, groupedWithPrevious = false, groupedWithNext = false)
    val fill = if (mine) colors.myBubble else colors.surface
    Box(
        modifier = modifier
            .testTag(FOCUS_BUBBLE_TAG)
            .shadow(elevation = WaiveraElevation.Overlay, shape = shape, clip = false)
            .background(fill, shape)
            .then(if (mine) Modifier else Modifier.border(WaiveraSpacing.Hairline, colors.border, shape))
            .clip(shape),
    ) {
        ScrollingBubbleBody(fade = fill) {
            Column(
                modifier = Modifier
                    .defaultMinSize(minWidth = ChatDimens.BubbleMinWidth)
                    .padding(
                        horizontal = WaiveraSpacing.MediumSmall - WaiveraSpacing.Hairline,
                        vertical = WaiveraSpacing.Small,
                    ),
            ) {
                InMyBubbleColors(inMyBubble = mine) {
                    val rich = model.rich
                    val text = message.plainText().ifEmpty {
                        message.attachment?.let {
                            stringResource(R.string.feature_chat_row_attachment, it.originalName.orEmpty())
                        }.orEmpty()
                    }
                    if (rich != null) {
                        RichText(document = rich, onLink = onLink, inMyBubble = mine)
                    } else {
                        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.text)
                    }
                }
            }
        }
    }
}

/**
 * [content] top first, inside at most the height the bubble is given. A message taller than that
 * is clamped and scrolls vertically inside the bubble (owner's pick, v7 item 2: a, b and c):
 *
 * - a fade over about the last two lines, in the bubble's own [fade] fill so it reads on my plum
 *   bubble in dark as on a white one in light, says the message goes on;
 * - it goes once the end is scrolled into view, and a matching fade at the top shows once the
 *   start has scrolled away.
 *
 * The scroll is the standard nested-scroll-aware [verticalScroll], on only when the message is
 * clamped, so a short message neither scrolls nor offers TalkBack a scroll action. A drag on the
 * bubble is the bubble's: it never reaches the scrim behind, so scrolling never closes the
 * overlay. The menu and the reactions are siblings, so they stay where they are. With animations
 * removed the fades switch without fading.
 */
@Composable
private fun ScrollingBubbleBody(fade: Color, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val motion = WaiveraTheme.motion
    val fadeSpec: FiniteAnimationSpec<Float> = remember(motion) { if (motion.reduced) snap() else motion.state() }
    val topFade by animateFloatAsState(
        targetValue = if (scroll.canScrollBackward) 1f else 0f,
        animationSpec = fadeSpec,
        label = "focusTopFade",
    )
    val bottomFade by animateFloatAsState(
        targetValue = if (scroll.canScrollForward) 1f else 0f,
        animationSpec = fadeSpec,
        label = "focusBottomFade",
    )
    val lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    val fadeHeight = with(LocalDensity.current) { (lineHeight * ChatDimens.FOCUS_FADE_LINES).toPx() }
    Box {
        Box(
            modifier = Modifier
                .testTag(FOCUS_BUBBLE_SCROLL_TAG)
                .verticalScroll(scroll, enabled = scroll.maxValue > 0),
        ) {
            content()
        }
        if (topFade > 0f) {
            BubbleFade(fade = fade, height = fadeHeight, alpha = { topFade }, atTop = true, tag = FOCUS_FADE_TOP_TAG)
        }
        if (bottomFade > 0f) {
            BubbleFade(
                fade = fade,
                height = fadeHeight,
                alpha = { bottomFade },
                atTop = false,
                tag = FOCUS_FADE_BOTTOM_TAG,
            )
        }
    }
}

/**
 * One fade over the bubble's top or bottom [height] pixels: the bubble's fill, solid at the edge
 * and clear toward the text. It only draws (no pointer input), so drags pass to the scroll.
 */
@Composable
private fun BoxScope.BubbleFade(fade: Color, height: Float, alpha: () -> Float, atTop: Boolean, tag: String) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .testTag(tag)
            .drawBehind {
                val edge = min(height, size.height)
                val top = if (atTop) 0f else size.height - edge
                val clear = fade.copy(alpha = 0f)
                val brush = if (atTop) {
                    Brush.verticalGradient(
                        0f to fade,
                        FOCUS_FADE_SOLID_STOP to fade,
                        1f to clear,
                        startY = top,
                        endY = edge,
                    )
                } else {
                    Brush.verticalGradient(
                        0f to clear,
                        1f - FOCUS_FADE_SOLID_STOP to fade,
                        1f to fade,
                        startY = top,
                        endY = top + edge,
                    )
                }
                drawRect(
                    brush = brush,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, edge),
                    alpha = alpha(),
                )
            },
    )
}

/** The share of a fade, from its edge, that is the solid fill: the last line under it is mostly hidden. */
private const val FOCUS_FADE_SOLID_STOP = 0.2f

/** The six quick reactions in one pill. The emoji keep their size at any font scale (wave 28 B2). */
@Composable
private fun ReactionPill(onReact: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val emojiSp = with(LocalDensity.current) { ChatDimens.FocusEmojiGlyph.toSp() }
    Row(
        modifier = modifier
            .testTag(FOCUS_PILL_TAG)
            .shadow(elevation = WaiveraElevation.Overlay, shape = WaiveraShapes.Pill, clip = false)
            .background(colors.raised, WaiveraShapes.Pill)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Pill)
            .padding(ChatDimens.FocusPillPadding),
        horizontalArrangement = Arrangement.spacedBy(ChatDimens.FocusPillGap),
    ) {
        Reactions.QUICK.forEach { emoji ->
            WaiveraPressable(
                onClick = { onReact(emoji) },
                onClickLabel = stringResource(R.string.feature_chat_react_with, emoji),
                shape = WaiveraShapes.Tile,
                modifier = Modifier
                    .size(ChatDimens.EmojiTarget)
                    .semantics { contentDescription = emoji },
            ) {
                Text(
                    text = emoji,
                    style = TextStyle(fontSize = emojiSp, lineHeight = emojiSp, textAlign = TextAlign.Center),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * Reply in thread, Copy text, Edit message and Bookmark, then Delete message under a hairline in
 * the danger colour. Each shows only when the model allows it. Copy turns into a popped check
 * reading "Copied"; Bookmark shows whether the message is already saved.
 */
@Composable
private fun ActionMenu(model: MessageActionsModel, actions: MessageFocusActions, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val bookmarked = model.message.isBookmarked
    Column(
        modifier = modifier
            .shadow(elevation = WaiveraElevation.Overlay, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.raised, WaiveraShapes.SheetCard)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.SheetCard)
            .clip(WaiveraShapes.SheetCard)
            .padding(vertical = WaiveraSpacing.ExtraSmall),
    ) {
        if (model.canReply) {
            FocusAction(WaiveraIcons.Reply, stringResource(R.string.feature_chat_action_reply), actions.onReply)
        }
        if (model.canCopy) {
            FocusAction(
                icon = if (model.copied) WaiveraIcons.Check else ChatIcons.Copy,
                label = stringResource(
                    if (model.copied) R.string.feature_chat_action_copied else R.string.feature_chat_action_copy,
                ),
                onClick = actions.onCopy,
                accent = model.copied,
            )
        }
        if (model.canEdit) {
            FocusAction(ChatIcons.Edit, stringResource(R.string.feature_chat_action_edit), actions.onEdit)
        }
        FocusAction(
            icon = if (bookmarked) ChatIcons.BookmarkFilled else WaiveraIcons.Bookmark,
            label = stringResource(
                if (bookmarked) R.string.feature_chat_action_bookmarked else R.string.feature_chat_action_bookmark,
            ),
            onClick = actions.onBookmark,
            accent = bookmarked,
        )
        if (model.canDelete) {
            WaiveraDivider(Modifier.padding(vertical = WaiveraSpacing.ExtraSmall))
            FocusAction(
                ChatIcons.Delete,
                stringResource(R.string.feature_chat_action_delete),
                actions.onDelete,
                danger = true,
            )
        }
    }
}

/** One menu row: a 48dp-tall target with a violet glyph (danger for Delete) and its label. */
@Composable
private fun FocusAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    accent: Boolean = false,
) {
    val colors = WaiveraTheme.colors
    val glyph = if (danger) colors.dangerText else colors.accent
    val text = when {
        danger -> colors.dangerText
        accent -> colors.accent
        else -> colors.text
    }
    WaiveraPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), depth = PressDepth.Row) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .padding(
                    horizontal = WaiveraSpacing.Medium,
                    vertical =
                    WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall,
                ),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = glyph,
                modifier = Modifier
                    .size(WaiveraSizes.IconTileSmallGlyph)
                    .popScale(rememberPopScale(key = icon)),
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = text)
        }
    }
}
