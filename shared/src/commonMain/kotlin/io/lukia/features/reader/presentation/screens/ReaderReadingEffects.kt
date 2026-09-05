package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * What scrolling the book does besides move it: putting the chrome away, and
 * remembering where the reader stopped.
 *
 * Kept out of the screen because none of it draws anything - these are three
 * standing arrangements between the scroll position and the rest of the app, and
 * a screen that reads as a layout is easier to be sure of than one with them
 * threaded through it.
 */

/**
 * Reading puts the chrome away on a phone, where the bar is a tenth of the
 * screen and every scroll is a deliberate swipe.
 *
 * A wide window keeps it: there the bar costs almost nothing, and a mouse wheel
 * is not a statement of intent - chrome that vanished on every notch of it would
 * be gone whenever the reader looked for it. It still goes away on a click,
 * which is a decision rather than a side effect.
 */
@Composable
internal fun HideChromeWhileScrolling(
    listState: LazyListState,
    compact: Boolean,
    chrome: ReaderChromeState,
) {
    LaunchedEffect(listState, compact) {
        if (!compact) return@LaunchedEffect

        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { chrome.hideChrome() }
    }
}

/**
 * Opening where the book was left off, and recording where it is left off next.
 *
 * The opening scroll is done as a scroll rather than as the list's starting
 * index because the stored page arrives after the list does - the file has to be
 * opened before its length is known, and a page past the end is not a place to
 * start. Nothing has been drawn yet at that point, so there is no jump to see.
 *
 * Only a document whose pages are its own opens itself here. A reflowable book
 * has no pages until it has been laid out for this window, so it opens itself at
 * the fraction of the book that was stored - see [TextPageList].
 *
 * Where the reader stopped is recorded when a scroll settles rather than as it
 * moves: a fling through a long book passes hundreds of pages nobody saw, and
 * storing each of them would be hundreds of writes to arrive at the one page
 * that mattered.
 *
 * [laidOut] is false while a reflowable book is still being measured, and is
 * what keeps a position from being stored against a length that is still
 * climbing - see the note on the recording effect below.
 */
@Composable
internal fun KeepReadingPosition(
    listState: LazyListState,
    document: ReaderDocument?,
    initialPosition: ReadingProgress?,
    pageCount: Int,
    laidOut: Boolean,
    onPageSettled: (pageIndex: Int, pageCount: Int) -> Unit,
) {
    LaunchedEffect(listState, initialPosition, document) {
        if (document?.content !is PagedContent) return@LaunchedEffect

        val page = initialPosition?.pageIndex ?: return@LaunchedEffect

        // Only a list that has not been anywhere yet.
        //
        // [initialPosition] is where the book *opened* and is deliberately
        // never moved off that, so this is an instruction to carry out once
        // rather than a position to keep following. A turned device runs it
        // again - the composition goes down and the ViewModel stands - and
        // without this the reader is sent back to where they started, and then
        // that is stored as where they got to. A list that is already
        // somewhere has either been scrolled by the reader or has just
        // restored its own position, and both are a better answer than this
        // one.
        if (listState.firstVisibleItemIndex != 0) return@LaunchedEffect
        if (listState.firstVisibleItemScrollOffset != 0) return@LaunchedEffect

        if (page > 0) listState.scrollToItem(page)
    }

    // Read through the state rather than captured, so a caller passing a fresh
    // lambda each composition does not tear this down and rebuild it mid-scroll.
    val settled by rememberUpdatedState(onPageSettled)

    // Restarted when the length changes, not only when the book does. A
    // reflowable book has no pages until it has been laid out for this window -
    // which happens after the document arrives - and a reader who opens a book
    // and never scrolls would otherwise have their position recorded against a
    // length of nothing, and so not recorded at all.
    //
    // Nothing at all until the book has been measured to the end. A page number
    // is only a position alongside the count it is one of, and the count of a
    // book still being laid out is on its way to several times what it says -
    // so a sitting that ended in that window stored a page four hundred of
    // sixty, and reopening it read that as two thirds of the way through.
    LaunchedEffect(listState, document, pageCount, laidOut) {
        if (document == null || !laidOut) return@LaunchedEffect

        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, page) -> page }
            .distinctUntilChanged()
            .collect { page -> settled(page, pageCount) }
    }
}
