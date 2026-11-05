package com.waivera.waivera.feature.tasks.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.R

/**
 * Every page in a scrolling column, each drawn when it comes on screen. Pinch zooms up to
 * [MAX_ZOOM]x around the fingers, as the iOS viewer does; zoomed in, one finger moves around the
 * page in any direction, and a double tap goes back to fit. Pages are drawn again at twice the
 * width once zoomed past [SHARP_FROM], so text stays readable.
 */
@Composable
internal fun PdfPages(aspects: List<Float>, renderPage: suspend (Int, Int) -> ImageBitmap?) {
    val list = rememberLazyListState()
    val zoom = remember { PdfZoom() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom.reset() }) }
            .pointerInput(Unit) { zoomGestures(zoom, list) },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { (maxWidth - WaiveraSpacing.Gutter * 2).roundToPx() }
        val sharpness = if (zoom.scale > SHARP_FROM) 2 else 1
        // Room under the last page, so it can be scrolled into the zoomed window.
        val extraBottom = with(density) { (maxHeight.toPx() * (1f - 1f / zoom.scale)).toDp() }
        LazyColumn(
            state = list,
            userScrollEnabled = zoom.scale == 1f,
            contentPadding = PaddingValues(
                start = WaiveraSpacing.Gutter,
                end = WaiveraSpacing.Gutter,
                top = WaiveraSpacing.Gutter,
                bottom = WaiveraSpacing.Gutter + extraBottom.coerceAtLeast(0.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                },
        ) {
            itemsIndexed(aspects, key = { index, _ -> index }) { index, aspect ->
                val renderWidth = widthPx * sharpness
                val page by produceState<ImageBitmap?>(null, index, renderWidth) {
                    value = renderPage(index, renderWidth)
                }
                val label = stringResource(R.string.feature_tasks_file_page, index + 1, aspects.size)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / aspect)
                        .background(WaiveraTheme.colors.surface, WaiveraShapes.Chip)
                        .semantics { contentDescription = label },
                ) {
                    page?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/** The pages' zoom: [scale] from the top left, then shifted left by [offsetX] (never past an edge). */
internal class PdfZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set

    fun reset() {
        scale = 1f
        offsetX = 0f
    }

    /**
     * Zooms by [factor] keeping the point under [centroid] still, then moves by [pan]. Across, the
     * offset moves; down, the list scrolls (in its own, unzoomed pixels).
     */
    fun change(factor: Float, centroid: Offset, pan: Offset, width: Float, scroll: (Float) -> Unit) {
        val old = scale
        val new = (old * factor).coerceIn(1f, MAX_ZOOM)
        val applied = new / old
        val maxShift = width * new - width
        offsetX = (centroid.x - (centroid.x - offsetX) * applied + pan.x).coerceIn(-maxShift, 0f)
        val scrollBy = centroid.y / old - centroid.y / new - pan.y / new
        scale = new
        if (scrollBy != 0f) scroll(scrollBy)
        if (new == 1f) offsetX = 0f
    }
}

/**
 * Two fingers always zoom; once zoomed, one finger pans too. Read before the list sees the
 * events, so a pinch never also scrolls it.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.zoomGestures(
    zoom: PdfZoom,
    list: LazyListState,
) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val fingers = event.changes.count { it.pressed }
        if (fingers >= 2 || zoom.scale > 1f) {
            val centroid = event.calculateCentroid(useCurrent = false)
            if (centroid.isSpecified) {
                // Read each time, so a rotation mid-session still pans to the real edges.
                val width = size.width.toFloat()
                zoom.change(event.calculateZoom(), centroid, event.calculatePan(), width, list::dispatchRawDelta)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    } while (event.changes.any { it.pressed })
}

private const val MAX_ZOOM = 5f
private const val SHARP_FROM = 1.5f
