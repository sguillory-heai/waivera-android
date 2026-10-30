package com.waivera.waivera.feature.chat.message

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.designsystem.component.LocalSignedInAccount
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The sheet or dialog on top of a message list. One at a time. */
@Immutable
sealed interface ChatSheet {
    data class Actions(val model: MessageActionsModel) : ChatSheet

    data class ReactionDetails(val messageId: Long, val initialEmoji: String) : ChatSheet

    data class FileActions(val messageId: Long, val fileName: String, val busy: Boolean = false) : ChatSheet

    data class ImagePreview(val messageId: Long) : ChatSheet

    data class ConfirmDelete(val messageId: Long, val inProgress: Boolean = false) : ChatSheet
}

/**
 * What the long-press focus overlay offers for one message (03 §14; threads get the same, 04 §1).
 * [copied] is set once Copy has run, so its row reads "Copied" before the overlay closes.
 */
@Immutable
data class MessageActionsModel(
    val message: Message,
    val rich: RichDocument?,
    val canReact: Boolean,
    val canReply: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val canCopy: Boolean,
    val copied: Boolean = false,
)

/** Who reacted, one tab per emoji present (03 §13). */
@Composable
fun ReactionDetailsContent(
    reactions: List<ReactionSummary>,
    initialEmoji: String,
    nameFor: (String) -> String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    myUserId: String? = null,
) {
    val account = LocalSignedInAccount.current
    var selected by rememberSaveable(initialEmoji) {
        mutableStateOf(reactions.firstOrNull { it.emoji == initialEmoji }?.emoji ?: reactions.firstOrNull()?.emoji)
    }
    val current = reactions.firstOrNull { it.emoji == selected } ?: reactions.firstOrNull()
    Column(modifier = modifier.fillMaxWidth().padding(bottom = WaiveraSpacing.Medium)) {
        Row(
            modifier = Modifier.padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_chat_reactions_title),
                style = MaterialTheme.typography.titleLarge,
                color = WaiveraTheme.colors.text,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_chat_reactions_close),
                onClick = onClose,
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = WaiveraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            reactions.forEach { reaction ->
                WaiveraFilterChip(
                    label = "${reaction.emoji} ${reaction.count}",
                    selected = reaction.emoji == current?.emoji,
                    onClick = { selected = reaction.emoji },
                    modifier = Modifier.widthIn(min = ChatDimens.ReactionTabMinWidth),
                )
            }
        }
        current?.userIds?.forEach { userId ->
            val name = nameFor(userId)
            val avatar = rememberAvatar(userId)
            // "You" is my row: show my own picture or initials, the same as the top bar, not a "Y".
            val me = account?.takeIf { userId == myUserId && it.name.isNotBlank() }
            WaiveraListRow(
                headline = name,
                oneLineName = true,
                leading = {
                    WaiveraAvatar(
                        name = me?.name ?: name,
                        imageModel = me?.imageModel,
                        painter = avatar?.let { remember(it) { BitmapPainter(it) } },
                        size = WaiveraSizes.IconTile,
                        contentDescription = null,
                    )
                },
                trailing = { Text(text = current.emoji, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}

/** "Save to device" and "Open / Share" for a file attachment (03 §8). */
@Composable
fun FileActionsContent(
    fileName: String,
    busy: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter)
            .padding(bottom = WaiveraSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleLarge,
                color = WaiveraTheme.colors.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_chat_action_close),
                onClick = onClose,
            )
        }
        Text(
            text = stringResource(R.string.feature_chat_file_sheet_message),
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
        )
        if (busy) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraBusyIndicator()
                Text(
                    text = stringResource(R.string.feature_chat_file_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                )
            }
        }
        WaiveraButton(
            text = stringResource(R.string.feature_chat_file_save),
            onClick = onSave,
            enabled = !busy,
            fullWidth = true,
            leadingIcon = ChatIcons.Download,
        )
        WaiveraButton(
            text = stringResource(R.string.feature_chat_file_share),
            onClick = onShare,
            enabled = !busy,
            fullWidth = true,
            variant = WaiveraButtonVariant.Secondary,
            leadingIcon = WaiveraIcons.OpenExternal,
        )
    }
}

/**
 * Full-screen image preview: pinch zoom clamped to 1×–5×, double tap toggles 1× and 2.5×, pan
 * while zoomed, and swipe down (or up) to close while not zoomed ([ImagePreviewContent]). Zoom
 * resets whenever the preview closes because the state lives in the dialog.
 */
@Composable
fun ImagePreviewDialog(message: Message, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The window's own dim would stay put while the scrim fades under a swipe, so the scrim
        // is the preview's only backdrop.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val maxPx = with(LocalDensity.current) { ChatDimens.ImageWidth.roundToPx() * PREVIEW_SCALE }
        val image = rememberChatImage(LocalChatImages.current.attachmentModel(message), maxPx)
        ImagePreviewContent(image = image, description = message.attachment?.originalName, onClose = onClose)
    }
}

/** How far a swipe-to-close drag has moved the image from rest, in px. */
@Stable
internal class ImageDismissDrag(initialOffset: Float = 0f) {
    var offset by mutableFloatStateOf(initialOffset)
}

/** The swipe-to-close rules, apart from the gesture so they can be tested on numbers. */
internal object ImageDismiss {
    /** How far through the backdrop's fade a drag of [offsetPx] is, 0 at rest to 1. */
    fun progress(offsetPx: Float, fadeDistancePx: Float): Float =
        if (fadeDistancePx <= 0f) 0f else (abs(offsetPx) / fadeDistancePx).coerceIn(0f, 1f)

    /** The backdrop's alpha at [progress]: the resting scrim, fading in proportion to the drag. */
    fun scrimAlpha(progress: Float): Float = PREVIEW_SCRIM_ALPHA * (1f - progress.coerceIn(0f, 1f))

    /**
     * Whether letting go at [offsetPx] with [velocityPx] per second closes the preview: far enough
     * from rest, or a fling the way the image is going. A fast flick back toward rest keeps it open
     * even past the distance, as the finger has changed its mind.
     */
    fun shouldClose(offsetPx: Float, velocityPx: Float, distancePx: Float, flingVelocityPx: Float): Boolean {
        val fast = abs(velocityPx) >= flingVelocityPx
        val towardRest = offsetPx != 0f && velocityPx != 0f && sign(velocityPx) != sign(offsetPx)
        return when {
            fast && towardRest -> false
            fast -> true
            else -> abs(offsetPx) >= distancePx
        }
    }

    /** Swipe-to-close only at rest scale: a zoomed image pans instead. */
    fun allowsDrag(scale: Float): Boolean = scale <= 1f
}

/**
 * The preview's content. While the image is not zoomed, a vertical drag moves it with the finger
 * and fades the backdrop in proportion; letting go past [ChatDimens.ImageDismissDistance], or with
 * a fling, sends it off the screen on the layout spring and closes, and anything less springs it
 * back. Under reduced motion the image stays where it is and only the backdrop fades with the
 * drag; a close is immediate and a cancel snaps back, as the predictive back preview does. Pinch
 * and double tap zoom, and pan while zoomed, as before. TalkBack keeps the Close button.
 */
@Composable
internal fun ImagePreviewContent(
    image: ChatImageState,
    description: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    drag: ImageDismissDrag = remember { ImageDismissDrag() },
) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val density = LocalDensity.current
    val fadePx = with(density) { ChatDimens.ImageDismissFadeDistance.toPx() }
    val distancePx = with(density) { ChatDimens.ImageDismissDistance.toPx() }
    val flingPx = with(density) { ChatDimens.ImageDismissFlingVelocity.toPx() }
    val currentOnClose by rememberUpdatedState(onClose)
    var heightPx by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, ChatDimens.MAX_ZOOM)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val dragState = rememberDraggableState { delta -> drag.offset += delta }
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    // Read in the draw and layer phases only, so a drag never recomposes the preview.
    val progress = { ImageDismiss.progress(drag.offset, fadePx) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height }
            .drawBehind { drawRect(colors.scrim.copy(alpha = ImageDismiss.scrimAlpha(progress()))) }
            .safeDrawingPadding(),
    ) {
        when (image) {
            is ChatImageState.Ready -> Image(
                bitmap = image.bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = ImageDismiss.allowsDrag(scale),
                        // A finger that catches the image mid-spring takes it from where it is.
                        onDragStarted = { settle?.cancel() },
                        onDragStopped = { velocity ->
                            val close = ImageDismiss.shouldClose(drag.offset, velocity, distancePx, flingPx)
                            if (close && motion.reduced) {
                                currentOnClose()
                            } else {
                                val direction = sign(if (drag.offset != 0f) drag.offset else velocity)
                                val target = if (close) direction * heightPx else 0f
                                settle = scope.launch {
                                    animate(
                                        initialValue = drag.offset,
                                        targetValue = target,
                                        initialVelocity = velocity,
                                        animationSpec = motion.layout(),
                                    ) { value, _ -> drag.offset = value }
                                    if (close) currentOnClose()
                                }
                            }
                        },
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else ChatDimens.DOUBLE_TAP_ZOOM
                            offset = Offset.Zero
                        })
                    }
                    // One finger pans only a zoomed image; at rest it is the swipe to close.
                    .transformable(transform, canPan = { scale > 1f })
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + if (motion.reduced) 0f else drag.offset
                    },
            )
            ChatImageState.Loading -> WaiveraBusyIndicator(
                color = onScrimColor(),
                // On the dark scrim the lilac track would glare; the arc alone reads there.
                trackColor = Color.Transparent,
                size = WaiveraSizes.ProgressLarge,
                strokeWidth = WaiveraSizes.ProgressStrokeLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            ChatImageState.Unavailable -> Text(
                text = stringResource(R.string.feature_chat_image_unavailable),
                color = onScrimColor(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // The chrome fades out with the backdrop, so a swipe reads as the preview leaving.
        val chrome = Modifier.graphicsLayer { alpha = 1f - progress() }
        WaiveraIconButton(
            icon = WaiveraIcons.Close,
            contentDescription = stringResource(R.string.feature_chat_image_close),
            onClick = onClose,
            tint = onScrimColor(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(chrome)
                .padding(WaiveraSpacing.Small)
                .background(colors.scrim, WaiveraShapes.Circle),
        )
        Text(
            text = stringResource(R.string.feature_chat_image_zoom_hint),
            style = MaterialTheme.typography.labelMedium,
            color = onScrimColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(chrome)
                .padding(WaiveraSpacing.Medium)
                .clearAndSetSemantics {},
        )
    }
}

private const val PREVIEW_SCALE = 4
private const val PREVIEW_SCRIM_ALPHA = 0.94f

@WaiveraPreviews
@Composable
private fun FileActionsPreview() {
    WaiveraTheme {
        FileActionsContent(fileName = "care-plan.pdf", busy = true, onSave = {}, onShare = {}, onClose = {})
    }
}

@WaiveraPreviews
@Composable
private fun ReactionDetailsPreview() {
    WaiveraTheme {
        ReactionDetailsContent(
            reactions = listOf(
                ReactionSummary("👍", 2, mine = true, userIds = listOf("me", "jordan")),
                ReactionSummary("❤️", 1, mine = false, userIds = listOf("priya")),
            ),
            initialEmoji = "❤️",
            nameFor = { if (it == "me") "You" else it.replaceFirstChar(Char::uppercase) },
            onClose = {},
        )
    }
}
