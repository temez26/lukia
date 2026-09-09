package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.presentation.rendering.renderWidthFor
import io.lukia.features.reader.presentation.screens.chromeHeight
import io.lukia.features.reader.presentation.translation.BookTranslations
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/** Gap between pages, which is what makes them read as separate sheets. */
internal val PageGap: Dp = Space.lg

/** Breathing room at the end of the book. */
internal val PageListPadding: Dp = Space.md

@Composable
internal fun PageList(
    pages: PagedContent,
    listState: LazyListState,
    pageScale: Float,
    pageColor: Color,
    pageFilter: ColorFilter?,
    translations: BookTranslations,
    onToggleChrome: () -> Unit,
    onPageScaleChange: (Float) -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassageHold: (PassageSelection) -> Unit,
    onPassageInquiry: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    sentencesOnPage: suspend (index: Int) -> List<PageSentence>,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?,
) {
    val dimens = LocalLukiaDimens.current
    val pageSizes = pages.pages.pageSizes

    // Held here while a pinch is in progress and committed when it ends. Writing
    // every intermediate scale into the settings would persist it to storage
    // sixty times a second, and the page follows the fingers just as closely
    // from here.
    var pinchScale by remember { mutableStateOf<Float?>(null) }
    val effectiveScale = pinchScale ?: pageScale

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // One measurement for the whole book: every page is drawn to the same
        // width, so working it out per page would be the same answer computed
        // hundreds of times.
        val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
            maxWidth
        } else {
            minOf(maxWidth, dimens.maxReaderWidth)
        }
        val pageWidth = columnWidth * effectiveScale
        val renderWidthPx = with(LocalDensity.current) { renderWidthFor(pageWidth.roundToPx()) }

        // Zoomed in, the page is wider than the window and the extra width is
        // the point - so the book is laid out at its own width and panned,
        // rather than squeezed back into the space available.
        val contentWidth = maxOf(pageWidth, maxWidth)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    scale = effectiveScale,
                    range = ReaderSettings.MIN_PAGE_SCALE..ReaderSettings.MAX_PAGE_SCALE,
                    onPinch = { pinchScale = it },
                    onPinchEnd = {
                        pinchScale?.let(onPageScaleChange)
                        pinchScale = null
                    },
                )
                .pointerInput(Unit) {
                    // Beside the pages rather than on one, so there is nothing
                    // here to point at - but the chrome answers to two taps
                    // wherever it is reached from, so that it stays one gesture
                    // the reader learns once.
                    detectTapGestures(onDoubleTap = { onToggleChrome() })
                }
                .horizontalScroll(rememberScrollState()),
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PageGap),
                contentPadding = PaddingValues(
                    // Clears the floating chrome, so the first page is not behind
                    // it when the book opens.
                    top = chromeHeight(),
                    bottom = PageListPadding,
                ),
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight(),
            ) {
                items(count = pageSizes.size) { index ->
                    DocumentPage(
                        index = index,
                        pageCount = pageSizes.size,
                        pageSize = pageSizes[index],
                        width = pageWidth,
                        renderWidthPx = renderWidthPx,
                        pageColor = pageColor,
                        pageFilter = pageFilter,
                        translations = translations.page(index),
                        onToggleChrome = onToggleChrome,
                        onPassageSelected = onPassageSelected,
                        onPassageHold = onPassageHold,
                        onPassageInquiry = onPassageInquiry,
                        onPassagesRequested = onPassagesRequested,
                        translateOnScreen = translateOnScreen,
                        gestures = gestures,
                        sentencesOnPage = sentencesOnPage,
                        renderPage = renderPage,
                    )
                }
            }
        }
    }
}

/**
 * Pinch to zoom, without taking the drag that scrolls the book.
 *
 * Only claims the gesture once a second finger is down: one finger stays a
 * scroll, two become a zoom. A plain transform gesture would swallow both.
 *
 * A composable extension rather than a `composed` modifier: `composed` makes a
 * chain opaque to Compose's modifier comparison, so the whole chain is rebuilt
 * on every recomposition instead of reused. The `rememberUpdated` values below
 * are what let the gesture keep one `pointerInput` across a changing scale.
 */
@Composable
internal fun Modifier.pinchToZoom(
    scale: Float,
    range: ClosedFloatingPointRange<Float>,
    onPinch: (Float) -> Unit,
    onPinchEnd: () -> Unit,
): Modifier {
    val currentScale by rememberUpdatedState(scale)
    val currentOnPinch by rememberUpdatedState(onPinch)
    val currentOnPinchEnd by rememberUpdatedState(onPinchEnd)

    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var pinched = false

            do {
                val event = awaitPointerEvent()

                if (event.changes.size > 1) {
                    val zoom = event.calculateZoom()

                    if (zoom != 1f) {
                        pinched = true
                        currentOnPinch((currentScale * zoom).coerceIn(range))
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (pinched) currentOnPinchEnd()
        }
    }
}
