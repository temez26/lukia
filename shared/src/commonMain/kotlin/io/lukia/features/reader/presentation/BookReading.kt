package io.lukia.features.reader.presentation

import io.lukia.core.domain.ReadingProgress
import io.lukia.core.domain.ReadingProgressRepository
import io.lukia.core.domain.ReadingSessionRepository
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
 * what the reader comes back to; the time is what their profile counts.
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

    /**
     * The book is on screen, and its clock starts.
     *
     * Called once the document has actually opened, so a file that could not be
     * opened is not recorded as an evening's reading.
     */
    fun begin() {
        time.begin()
    }

    /**
     * Where this book was left off, or its beginning if it has never been
     * opened - which is the same instruction, so an unread book is not a case
     * of its own.
     *
     * A book nobody has opened is given a length of nothing rather than the
     * document's own count. For a PDF the two would be the same; for a
     * reflowable book the count here is a page count, and what the document can
     * say is how many divisions it was written in - a different number in a
     * different unit, and one this must not pass off as the other. Nothing is
     * the honest answer, and the reader opens at the beginning either way.
     */
    suspend fun restore(document: ReaderDocument) {
        val stored = runCatchingApp { positions.progressFor(location) }.getOrNull()
        val content = document.content

        _initialPosition.value = when {
            stored == null -> ReadingProgress(pageIndex = 0, pageCount = 0)

            // A fixed-layout document's pages are its own, so a stored page past
            // the end of one is a file that has been replaced by a shorter one
            // under the same name - and no place to open at. A reflowable book
            // has no pages until it has been laid out for a window, so there is
            // nothing here to clamp it against; the page list does that, against
            // the layout it just made.
            content is PagedContent -> ReadingProgress(
                pageIndex = stored.pageIndex.coerceIn(0, (content.partCount - 1).coerceAtLeast(0)),
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
 * the scope belongs to the ViewModel and cannot be injected beside the two
 * stores, so without this the ViewModel would hold both of them only to pass
 * them on.
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
