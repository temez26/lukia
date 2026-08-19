package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.reader.presentation.components.PageTranslationOverlay
import io.lukia.features.reader.presentation.renderWidthFor
import io.lukia.features.translate.domain.PageSentence
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.sentenceAt
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.ReaderTranslations
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.flow.map

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
    translations: ReaderTranslations,
    onToggleChrome: () -> Unit,
    onPageScaleChange: (Float) -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
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
                    detectTapGestures(onTap = { onToggleChrome() })
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
 * One page. The sheet is laid out at the page's own proportions before anything
 * is drawn into it, so the list's height is settled from the start and scrolling
 * never jumps as pages arrive.
 *
 * [pageFilter] is what makes the reading theme instant: switching it re-draws
 * the page that is already rendered rather than rendering it again.
 */
@Composable
private fun DocumentPage(
    index: Int,
    pageCount: Int,
    pageSize: PageSize,
    width: Dp,
    renderWidthPx: Int,
    pageColor: Color,
    pageFilter: ColorFilter?,
    translations: SentenceTranslations,
    onToggleChrome: () -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    sentencesOnPage: suspend (index: Int) -> List<PageSentence>,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?,
) {
    // Keyed on the width as well as the page: a resized window, or a different
    // zoom, invalidates what was drawn for the old one.
    val page by produceState<ImageBitmap?>(initialValue = null, index, renderWidthPx) {
        value = renderPage(index, renderWidthPx)

        // Draw the next page while this one is being read, so arriving at it
        // does not mean waiting for it. Cancelled along with this page when it
        // scrolls away, which is what stops a fast scroll from queueing work for
        // pages already passed.
        renderPage(index + 1, renderWidthPx)
    }

    // Read once per page and kept: parsing the text out of a page is far too
    // much to do again on every tap, and a reader working through a page taps
    // it several times.
    val sentences by produceState(initialValue = emptyList<PageSentence>(), index) {
        value = sentencesOnPage(index)
    }

    // The one number relating the file to what is on screen. Everything the
    // overlay draws is measured in the page's own units and scaled by this, so
    // a pinch or a resize moves it without recalculating anything.
    val scale = with(LocalDensity.current) { width.toPx() } / pageSize.width

    // The rest of the page, which is the context a sentence on it travels with.
    // Built once per page rather than once per tap: it is every word on the
    // page joined up, and a reader works through a page a tap at a time.
    val pageText = remember(sentences) { sentences.joinToString(" ") { it.text } }

    val currentSentences by rememberUpdatedState(sentences)
    val currentPageText by rememberUpdatedState(pageText)
    val currentOnSelected by rememberUpdatedState(onPassageSelected)
    val currentOnToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentScale by rememberUpdatedState(scale)
    val currentOnRequested by rememberUpdatedState(onPassagesRequested)
    val currentGestures by rememberUpdatedState(gestures)

    // A page is either on screen or it is not - there is nothing on it that can
    // be half of it - so unlike a paragraph of set text it needs nothing
    // measured. Being composed at all is close enough: the list keeps a page or
    // two either side, and translating one page ahead of somebody reading
    // forward is what they asked for anyway.
    var actedOn by remember { mutableStateOf(translateOnScreen.generation) }

    LaunchedEffect(translateOnScreen, sentences) {
        if (sentences.isEmpty()) return@LaunchedEffect
        if (!translateOnScreen.continuous && translateOnScreen.generation == actedOn) {
            return@LaunchedEffect
        }

        actedOn = translateOnScreen.generation
        currentOnRequested(
            sentences.map { sentence ->
                PassageSelection(
                    anchor = TextAnchor.Page(pageIndex = index, sentenceIndex = sentence.index),
                    passage = sentence.text,
                    surroundingText = pageText,
                )
            },
        )
    }

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(pageSize.aspectRatio)
            .shadow(elevation = LukiaElevation.page)
            .background(pageColor)
            // A page is a picture with no text behind it, so this is the only
            // thing a screen reader can be told about it. Without it the whole
            // document is silence.
            .semantics { contentDescription = "Page ${index + 1} of $pageCount" }
            .pointerInput(index) {
                // A page of a PDF answers the same gestures as set text, down to
                // the word: the words are inferred back out of the glyphs along
                // with the sentences, so each of them has a piece of the page of
                // its own to be pointed at and drawn over.
                //
                // The paragraph is the one unit a page cannot offer. A PDF
                // records no paragraphs at all - what this reads as a block is a
                // guess from the space left between lines, and it is not
                // something an anchor into the page can name - so a gesture set
                // to the paragraph takes the sentence instead.
                fun select(position: Offset, unit: PassageUnit?) {
                    if (unit == null) return

                    // Back out of the drawn page into the file's own space,
                    // which is where the sentences know they are.
                    val x = position.x / currentScale
                    val y = position.y / currentScale

                    val sentence = currentSentences.sentenceAt(x, y) ?: return
                    val word = if (unit == PassageUnit.Word) sentence.wordAt(x, y) else null

                    // A gesture set to the word that landed between two of them
                    // asks about nothing, exactly as it does in set text: the
                    // punctuation between two words belongs to neither.
                    if (unit == PassageUnit.Word && word == null) return

                    currentOnSelected(
                        PassageSelection(
                            anchor = TextAnchor.Page(
                                pageIndex = index,
                                sentenceIndex = sentence.index,
                                wordIndex = word?.index,
                            ),
                            passage = word?.text ?: sentence.text,
                            // A page has no chapter of its own to name, and the
                            // rest of the page is the context around a passage.
                            surroundingText = currentPageText,
                            unit = if (word == null) PassageUnit.Sentence else PassageUnit.Word,
                        ),
                    )
                }

                detectTapGestures(
                    onTap = { position ->
                        val sentence = currentSentences.sentenceAt(
                            x = position.x / currentScale,
                            y = position.y / currentScale,
                        )

                        // A tap that landed on no words is a tap on the page: a
                        // margin, a figure, or a page with no text layer at all.
                        // That is how the chrome is put away and brought back,
                        // and so is a tap the reader has set to do nothing.
                        if (sentence == null || currentGestures.tap == null) {
                            currentOnToggleChrome()
                        } else {
                            select(position, currentGestures.tap)
                        }
                    },
                    onLongPress = { position -> select(position, currentGestures.longPress) },
                )
            },
    ) {
        page?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // The sheet is already at the page's proportions, so this only
                // ever scales - it never crops or letterboxes.
                contentScale = ContentScale.FillBounds,
                colorFilter = pageFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }

        PageTranslationOverlay(
            sentences = sentences,
            translations = translations,
            scale = scale,
            pageColor = pageColor,
        )
    }
}

/**
 * Pinch to zoom, without taking the drag that scrolls the book.
 *
 * Only claims the gesture once a second finger is down: one finger stays a
 * scroll, two become a zoom. A plain transform gesture would swallow both.
 *
 * A composable extension rather than a `composed` modifier: `composed` makes a
 * chain opaque to Compose's modifier comparison, so the whole chain it appears in
 * is rebuilt on every recomposition instead of being reused. The `rememberUpdated`
 * values below are what let the gesture keep a single `pointerInput` across a
 * changing scale, which is the thing that actually matters here.
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
