package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.presentation.pagination.BookPlace
import io.lukia.features.reader.presentation.pagination.PaginatedBook
import io.lukia.features.reader.presentation.pagination.rememberPagination
import io.lukia.features.reader.presentation.theme.readerTextStyle
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.presentation.BookTranslations
import io.lukia.features.translate.presentation.PassageSelection

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
    translations: BookTranslations,
    onToggleChrome: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassageHold: (PassageSelection) -> Unit,
    onPassageInquiry: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
    onPagesChange: (PaginatedBook) -> Unit,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    /**
     * Where the reader left off last time, or null while the stored position is
     * still being read.
     *
     * The difference between the two matters now that a book can be laid out
     * faster than its position can be read back: a book that opened at its first
     * page because nobody had said otherwise *yet* would start following a
     * reader who had not arrived, and the place they actually left off would
     * arrive to find the book already claiming to be somewhere.
     */
    openedAt: ReadingProgress?,
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
            openingPage = openedAt?.pageIndex,
        )

        FollowPlaceAcrossLayout(book = book, listState = listState, openedAt = openedAt)
        ReportPages(book = book, onPagesChange = onPagesChange)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .typeGestures(
                    scale = pinchScale ?: settings.textScale,
                    onPinch = { pinchScale = it },
                    onPinchEnd = {
                        pinchScale?.let { onSettingsChange(settings.withTextScale(it)) }
                        pinchScale = null
                    },
                    onToggleChrome = onToggleChrome,
                ),
        ) {
            BookSheets(
                book = book,
                listState = listState,
                sheet = SheetStyle(
                    geometry = sheet,
                    pageColor = pageColor,
                    textStyle = drawnStyle,
                    figureSize = settings.figureSize,
                    resizingType = pinchScale != null,
                ),
                translations = translations,
                translateOnScreen = translateOnScreen,
                gestures = gestures,
                actionsFor = { page ->
                    rememberPageActions(
                        page = page,
                        sections = sections,
                        onToggleChrome = onToggleChrome,
                        onPassageSelected = onPassageSelected,
                        onPassageHold = onPassageHold,
                        onPassageInquiry = onPassageInquiry,
                        onPassagesRequested = onPassagesRequested,
                    )
                },
            )
        }
    }
}

/**
 * The page a stored position names in this layout of the book.
 *
 * The stored page number itself, wherever the book is the length it was stored
 * against - which is what reopening a book almost always is: the same window,
 * the same type, the same book, and so the same pages. No arithmetic can improve
 * on the number that was written down.
 *
 * It is also the only reading of a stored position that can be answered before
 * the book has been laid out from end to end, which is what lets the reader be
 * put on their page while the later chapters are still being measured. A
 * fraction has to be multiplied by a page count, and a page count is the one
 * thing a book that is still growing does not have.
 *
 * A finished book of a different length is a book that has been re-set since -
 * a larger type, a turned phone - and there the fraction is the better answer,
 * because the page it was stored as is a page of a book that no longer exists.
 */
private fun PaginatedBook.openingPage(openedAt: ReadingProgress): Int {
    if (!complete || openedAt.pageCount == pageCount) {
        return openedAt.pageIndex.coerceIn(0, pages.lastIndex)
    }

    val fraction = openedAt.fraction ?: return 0

    return ((fraction * pageCount).toInt() - 1).coerceIn(0, pages.lastIndex)
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
 * The first layout has no place to go back to, so it goes to [openedAt] instead
 * - see [openingPage]. Nothing happens at all until that is known, because a
 * book that started following the reader before they had been put anywhere would
 * take its own first page for their place and never move off it. That is not a
 * hypothetical: a book is now laid out faster than its position is read back.
 *
 * This runs again every time more of a book arrives - a book is laid out a run
 * at a time, and each run makes it longer - so it moves the reader only where
 * they are not already. Without that, a book still being measured would snap
 * back to the top of the page every time it grew, under the finger of somebody
 * scrolling through it.
 *
 * The place is saved rather than merely remembered, because it is the only
 * record of where the reader is now: [openedAt] is where the book *opened* and
 * is deliberately never moved off that. A rotation takes the composition down
 * and leaves the ViewModel standing, so a place held in `remember` came back
 * null and sent the reader to the page they opened at - discarding the whole
 * sitting, and then storing that as their position.
 */
@Composable
private fun FollowPlaceAcrossLayout(
    book: PaginatedBook,
    listState: LazyListState,
    openedAt: ReadingProgress?,
) {
    var place by rememberSaveable(stateSaver = BookPlaceSaver) {
        mutableStateOf<BookPlace?>(null)
    }

    LaunchedEffect(book, openedAt) {
        // Nothing to follow and nowhere to go until both the book and the place
        // it is being opened at are known.
        if (book.pages.isEmpty() || openedAt == null) return@LaunchedEffect

        val known = place

        // A book still being measured has no page for a place it has not
        // reached, and [pageOf] answers for one with its last page - which for
        // the reader's own position is the end of a beginning. Worse, the
        // collector below would then record that as where they are, losing the
        // place before the rest of the book arrived to honour it. A finished
        // book is clamped as it always was: there the last page is the honest
        // answer, because the book really is that long now.
        if (known != null && !book.complete && !book.holds(known)) return@LaunchedEffect

        val target = known?.let(book::pageOf) ?: book.openingPage(openedAt)

        if (target != listState.firstVisibleItemIndex) listState.scrollToItem(target)

        // Only once the reader has been put where they belong, so that the
        // scroll this effect just did is not mistaken for one of theirs.
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { place = book.placeOf(it) }
    }
}

/**
 * A place is two indices into the book, which is all of it that has to survive
 * the composition being taken down and built again.
 *
 * A reader who has not been put anywhere yet saves nothing, and restoring
 * nothing gives the null this started at - so the state that means "not yet"
 * comes back as itself without being spelled out.
 */
private val BookPlaceSaver: Saver<BookPlace?, Any> = listSaver(
    save = { place -> place?.let { listOf(it.sectionIndex, it.blockIndex) }.orEmpty() },
    restore = { saved -> saved.takeIf { it.size == 2 }?.let { BookPlace(it[0], it[1]) } },
)

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
