package com.waivera.waivera.feature.tasks.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.label

/** A task file, read-only. Owned by `:feature:tasks`; routed by `:app`. */
@Composable
fun TaskFileScreen(taskId: Int, attachmentId: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<TaskFileViewModel, TaskFileViewModel.Factory>(
        key = "task-file-$taskId-$attachmentId",
        creationCallback = { factory -> factory.create(taskId, attachmentId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TaskFileContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        renderPage = viewModel::renderPage,
        modifier = modifier,
    )
}

@Composable
internal fun TaskFileContent(
    state: TaskFileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    renderPage: suspend (Int, Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(
            title = state.name.ifEmpty { stringResource(R.string.feature_tasks_file_viewer) },
            subtitle = state.kind?.label(),
            subtitleMaxLines = 1,
            onBack = onBack,
            titleShortenable = null,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val phase = state.phase) {
                FilePhase.Loading -> LoadingPage()
                FilePhase.Failed -> WaiveraErrorState(
                    message = stringResource(R.string.feature_tasks_file_failed),
                    onRetry = onRetry,
                    modifier = Modifier.padding(WaiveraSpacing.Gutter),
                )
                is FilePhase.Ready -> when (val content = phase.content) {
                    is FileContent.Text -> TextPage(content.text, content.cut)
                    is FileContent.Word -> WordPage(content.html)
                    is FileContent.Image -> ZoomableImage(content.bitmap, state.name)
                    is FileContent.Pdf -> PdfPages(content.aspects, renderPage)
                }
            }
        }
    }
}

@Composable
private fun LoadingPage() {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_tasks_file_loading)
    Column(
        modifier = Modifier
            .padding(WaiveraSpacing.Gutter)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        repeat(SKELETON_LINES) { line ->
            SkeletonText(
                style = MaterialTheme.typography.bodyMedium,
                widthFraction = if (line % 3 == 2) SKELETON_SHORT else 1f,
                pulse = pulse,
            )
        }
    }
}

@Composable
private fun TextPage(text: String, cut: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(WaiveraSpacing.Gutter)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
        SelectionContainer {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.text)
        }
        if (cut) {
            Text(
                text = stringResource(R.string.feature_tasks_file_text_cut),
                style = MaterialTheme.typography.bodySmall,
                color = WaiveraTheme.colors.textSecondary,
            )
        }
    }
}

/** Word documents as text with their headings, lists, bold and links; layout and pictures stay on the web. */
@Composable
private fun WordPage(html: String) {
    val text = remember(html) { AnnotatedString.fromHtml(html) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(WaiveraSpacing.Gutter)
            .navigationBarsPadding(),
    ) {
        SelectionContainer {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.text)
        }
    }
}

/** Pinch to zoom up to [MAX_ZOOM]x and drag around; a double tap goes back to fit. */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, name: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                })
            }
            .transformable(transform),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val SKELETON_LINES = 9
private const val SKELETON_SHORT = 0.6f
private const val MAX_ZOOM = 5f
