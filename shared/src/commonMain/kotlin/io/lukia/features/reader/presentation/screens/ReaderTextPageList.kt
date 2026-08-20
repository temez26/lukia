package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.reader.presentation.components.TextPageView
import io.lukia.features.reader.presentation.pagination.PaginatedBook
import io.lukia.features.reader.presentation.pagination.rememberPagination
import io.lukia.features.reader.presentation.theme.readerTextStyle
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.ReaderTranslations
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaElevation

/**
 * A reflowable book, page by page.
 *
 * The same scrolling list of sheets a PDF gets, so moving between a PDF and an
 * EPUB is not moving between two different readers - but where a PDF's sheets
 * are pages its maker set, these are pages this app sets, out of text it has
 * re-typeset at the size the reader asked for. A sheet is exactly one screenful
 * and holds only what was measured to fit it, which is what makes "page 42 of
 * 380" a real answer rather than a count of chapters.
 *
 * The measure never exceeds the window. Above 100% a PDF page is deliberately
 * drawn wider than the space available and panned, because that is the only way
 * to get closer to type whose size was fixed when the file was made; text has no
 * such problem, and a line of prose running off both edges of the window is not
 * a way to read anything. So the width presets set the measure up to the width
 * available and stop there, and getting closer is what the text size is for -
 * which is also what a pinch does here.
 */
@Composable
internal fun TextPageList(
    sections: List<TextSection>,
    listState: LazyListState,
    settings: ReaderSettings,
    pageColor: Color,
    translations: ReaderTranslations,
    onToggleChrome: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
    onPagesChange: (PaginatedBook) -> Unit,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    /** Where the reader left off last time, as a fraction of the book. */
    openAt: Float?,
) {
    // Held here while a pinch is in progress and committed when it ends, so the
    // type follows the fingers without every intermediate size being written to
    // storage - and without the book being laid out again sixty times a second.
    var pinchScale by remember { mutableStateOf<Float?>(null) }

    // The book is laid out for the size that has been settled on. During a pinch
    // the type on screen runs ahead of the layout, which can leave the last line
    // of a page over its edge for as long as the fingers are down; the page is
    // cut to the new size the moment they lift.
    val style = readerTextStyle(settings)
    val drawnStyle = readerTextStyle(settings.withTextScale(pinchScale ?: settings.textScale))

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sheet = pageGeometry(settings.pageScale)

        val book = rememberPagination(
            sections = sections,
            style = style,
            widthPx = with(density) { sheet.textWidth.roundToPx() },
            heightPx = with(density) { sheet.textHeight.roundToPx() },
            figureSize = settings.figureSize,
        )

        FollowPlaceAcrossLayout(book = book, listState = listState, openAt = openAt)
        ReportPages(book = book, onPagesChange = onPagesChange)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    scale = pinchScale ?: settings.textScale,
                    range = ReaderSettings.MIN_TEXT_SCALE..ReaderSettings.MAX_TEXT_SCALE,
                    onPinch = { pinchScale = it },
                    onPinchEnd = {
                        pinchScale?.let { onSettingsChange(settings.withTextScale(it)) }
                        pinchScale = null
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleChrome() })
                },
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PageGap),
                contentPadding = PaddingValues(
                    top = chromeHeight(),
                    bottom = PageListPadding,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count = book.pageCount) { index ->
                    PageSheet(
                        number = index + 1,
                        of = book.pageCount,
                        size = DpSize(sheet.sheetWidth, sheet.pageHeight),
                        color = pageColor,
                    ) { textHeight ->
                        TextPageView(
                            page = book.pages[index],
                            style = drawnStyle,
                            pageHeight = textHeight,
                            figureSize = settings.figureSize,
                            translations = translations,
                            translateOnScreen = translateOnScreen,
                            gestures = gestures,
                            resizingType = pinchScale != null,
                            actionsFor = rememberPageActions(
                                page = book.pages[index],
                                sections = sections,
                                onToggleChrome = onToggleChrome,
                                onPassageSelected = onPassageSelected,
                                onPassagesRequested = onPassagesRequested,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How big a sheet is in this window, and how much of it is text.
 *
 * One measurement for the whole book: every page is drawn to the same size, so
 * working it out per page would be the same answer computed hundreds of times -
 * and the book was laid out against exactly these numbers.
 */
private data class PageGeometry(
    val sheetWidth: Dp,
    val pageHeight: Dp,
    val textWidth: Dp,
    val textHeight: Dp,
)

/**
 * The window with the chrome and the list's own padding taken out of it, and
 * then the sheet's own margins.
 */
@Composable
private fun BoxWithConstraintsScope.pageGeometry(pageScale: Float): PageGeometry {
    val dimens = LocalLukiaDimens.current

    val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
        maxWidth
    } else {
        minOf(maxWidth, dimens.maxReaderWidth)
    }

    val sheetWidth = minOf(columnWidth * pageScale, maxWidth)
    val pageHeight = maxHeight - chromeHeight() - PageListPadding

    return PageGeometry(
        sheetWidth = sheetWidth,
        pageHeight = pageHeight,
        textWidth = sheetWidth - SheetPadding * 2,
        textHeight = pageHeight - SheetPadding * 2,
    )
}

/**
 * Tells whoever is counting the pages how many there are now.
 *
 * The lambda is read through [rememberUpdatedState] rather than captured, so a
 * caller passing a fresh one each composition does not restart this - the layout
 * changing is the only thing worth reporting.
 */
@Composable
private fun ReportPages(book: PaginatedBook, onPagesChange: (PaginatedBook) -> Unit) {
    val report by rememberUpdatedState(onPagesChange)

    LaunchedEffect(book) { report(book) }
}

/**
 * One sheet of the book: a page's worth of paper, and what is set on it.
 *
 * Given the height rather than taking the height its text needs, unlike the
 * blocks inside it. A page whose paper stopped where its words did would be a
 * scrolling list of ragged cards, and the number in the toolbar would be
 * counting something the reader cannot see the edges of.
 */
@Composable
private fun PageSheet(
    number: Int,
    of: Int,
    size: DpSize,
    color: Color,
    content: @Composable (textHeight: Dp) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(size.width)
            .height(size.height)
            .shadow(elevation = LukiaElevation.page)
            .background(color)
            // Text set larger than the page was cut for - which is what a pinch
            // in progress is - stops at the edge of the sheet rather than
            // running over the one below.
            .clipToBounds()
            .padding(SheetPadding)
            // Unlike a rendered page there is real text behind this, so the
            // description says where in the book it is and leaves the reading to
            // the text itself.
            .semantics { contentDescription = "Page $number of $of" },
    ) {
        content(size.height - SheetPadding * 2)
    }
}

/**
 * Keeps the reader where they were when the book is laid out again.
 *
 * A page number does not survive a re-layout: the same place in a book set one
 * step larger is a different page, and a hundred pages further into a long one.
 * What is followed instead is the place - the block at the top of the page they
 * were on - which is a fact about the book rather than about this layout of it.
 * Without this, changing the type size would move the reader to somewhere else
 * in the book, which is the one thing a settings panel must never do.
 *
 * The first layout has no place to go back to, so it goes to [openAt] instead:
 * where the book was left off, as a fraction, because the page count it was
 * stored against was a different window's.
 */
@Composable
private fun FollowPlaceAcrossLayout(
    book: PaginatedBook,
    listState: LazyListState,
    openAt: Float?,
) {
    var place by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(book, openAt) {
        if (book.pages.isEmpty()) return@LaunchedEffect

        val target = place
            ?.let { (section, block) -> book.pageOf(section, block) }
            ?: openAt?.let { ((it * book.pageCount).toInt() - 1).coerceIn(0, book.pages.lastIndex) }

        if (target != null) listState.scrollToItem(target)

        // Only once the reader has been put where they belong, so that the
        // scroll this effect just did is not mistaken for one of theirs.
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { place = book.placeOf(it) }
    }
}
