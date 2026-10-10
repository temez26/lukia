package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the app records about one reader in one book: where they got to, and how
 * long they spent getting there.
 *
 * Split from [ReaderViewModel] because opening a book is one job and keeping the
 * record of it is another - and because the two halves of that record are
 * written for different reasons and read by different screens. The position is
 * what the reader comes back to; the time is what the statistics count.
 *
 * The pair travels together rather than as two dependencies because they are
 * written at the same moment by the same event: a page settling is both "this is
 * where I am" and "I am still here".
 */
internal class BookReading(
    private val location: String,
    private val positions: ReadingProgressRepository,
    sessions: ReadingSessionRepository,
    private val scope: CoroutineScope,
) {

    private val time = ReadingTimeRecorder(location, sessions, scope)

    private val _initialPosition = MutableStateFlow<ReadingProgress?>(null)

    /** Null until the stored position has been read, which the page list waits for. */
    val initialPosition: StateFlow<ReadingProgress?> = _initialPosition.asStateFlow()

    /** Whether a book actually opened. */
    private var opened = false

    /**
     * Whether the reader is in front of somebody.
     *
     * Starts true because a screen is composed to be looked at; what moves it is
     * the app going behind something else - see [onScreen].
     */
    private var onScreen = true

    /**
     * The book is open, and its clock starts.
     *
     * Called once the document has actually opened, so a file that could not be
     * opened is not recorded as an evening's reading.
     */
    fun begin() {
        opened = true

        startIfBeingRead()
    }

    /**
     * The reader has come to the front of the app, or gone from it.
     *
     * Both halves matter. Going tells the clock to stop, which is the whole
     * point: a book left open behind another app was counted as read for ten
     * minutes and written to the database thirty times on the way. Coming back
     * starts a fresh sitting, so the stretch in between is credited to nobody.
     *
     * Held here beside [opened] rather than passed straight to the recorder,
     * because the two questions have to be asked together. A book that opens
     * while the app is already behind something would otherwise start a sitting
     * nobody is watching, with nothing left to come and stop it.
     */
    fun onScreen(visible: Boolean) {
        onScreen = visible

        if (visible) startIfBeingRead() else time.pause()
    }

    private fun startIfBeingRead() {
        if (opened && onScreen) time.begin()
    }

    /**
     * Where this book was left off, or its beginning if it has never been
     * opened - which is the same instruction, so an unread book is not a case
     * of its own.
     *
     * An unopened book is given a length of nothing rather than the document's
     * own count. For a PDF the two agree; for a reflowable book this count is
     * pages and the document can only say how many divisions it was written in -
     * a different number in a different unit. Nothing is the honest answer, and
     * the reader opens at the beginning either way.
     */
    suspend fun restore(document: ReaderDocument) {
        val stored = runCatchingApp { positions.progressFor(location) }.getOrNull()
        val content = document.content

        _initialPosition.value = when {
            stored == null -> ReadingProgress(pageIndex = 0, pageCount = 0)

            // A fixed-layout document's pages are its own, so the stored place
            // is found among them here - see [pageAmong]. A reflowable book has
            // no pages until it has been laid out for a window, so there is
            // nothing here to place it against; the page list does that,
            // against the layout it just made.
            content is PagedContent -> ReadingProgress(
                pageIndex = stored.pageAmong(content.partCount),
                pageCount = content.partCount,
            )

            else -> stored
        }
    }

    /** Forgets where the book opened, for one being opened again another way. */
    fun forgetOpeningPosition() {
        _initialPosition.value = null
    }

    /**
     * Records where the reader has come to rest, and that they are still there.
     *
     * A length of nothing is a book that has not been laid out yet rather than
     * one that has been read to the end of, so it is not a position to store.
     */
    fun onPageSettled(pageIndex: Int, pageCount: Int) {
        if (pageCount <= 0) return

        // A page settling is the only sign this app gets that somebody is still
        // reading rather than that the book is open on a desk.
        time.onActivity()

        scope.launch {
            runCatchingApp {
                positions.save(
                    location = location,
                    progress = ReadingProgress(pageIndex = pageIndex, pageCount = pageCount),
                )
            }
        }
    }
}

/**
 * Builds one of these once there is a book and a scope to run it in.
 *
 * The same arrangement as [ReaderTranslationsFactory] and for the same reason:
 * the scope belongs to the ViewModel and cannot be injected beside the stores,
 * so without this the ViewModel would hold both only to pass them on.
 *
 * Public only because it names a ViewModel's dependency, and that ViewModel is
 * reached from outside this feature.
 */
class BookReadingFactory(
    private val positions: ReadingProgressRepository,
    private val sessions: ReadingSessionRepository,
) {

    internal fun create(location: String, scope: CoroutineScope): BookReading =
        BookReading(location, positions, sessions, scope)
}

/**
 * The page of a document [pageCount] long that this position is on.
 *
 * As far through as it was stored, when it was stored against another length:
 * a PDF read as text is as many pages as the window made of it, so page 353 of
 * 745 there is page 223 of the file's own 472, not its page 353. Never past the
 * end either way, which is also what a file replaced by a shorter one under the
 * same name needs.
 */
private fun ReadingProgress.pageAmong(pageCount: Int): Int {
    val last = (pageCount - 1).coerceAtLeast(0)
    val stored = this.pageCount

    if (stored <= 0 || stored == pageCount) return pageIndex.coerceIn(0, last)

    return (pageIndex.toLong() * pageCount / stored).toInt().coerceIn(0, last)
}
