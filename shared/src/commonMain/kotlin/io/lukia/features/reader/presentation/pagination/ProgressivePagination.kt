package io.lukia.features.reader.presentation.pagination

import io.lukia.core.infrastructure.availableProcessors
import io.lukia.features.reader.domain.text.TextSection
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A book laid out for the window, a longer beginning of it at a time.
 *
 * What this is for is the wait somebody sits through to read a page. Measuring a
 * long book is seconds of work - a few thousand paragraphs, each one laid out
 * against the font at the size asked for - and measuring all of it before
 * showing any of it means the reader watches a spinner for the last chapter of a
 * book they opened to read the first line of.
 *
 * So the book is cut into runs that lay out independently - see [chunkStarts] -
 * the runs are measured at the same time as each other, and what comes out of
 * here is the beginning of the book, then more of it, then all of it. Every one
 * of those is a real book as far as anything above is concerned: its pages are
 * the pages that book will have, and only how many there turn out to be is still
 * moving.
 *
 * The pages themselves are exactly the pages a single pass would have produced.
 * Nothing here changes where a page ends; it changes when the reader is told.
 *
 * [sizesPerWorker] is asked for one measurer per worker rather than handed one,
 * because measuring text is not something two threads may do through the same
 * one - see [BlockSizes] and `rememberPagination`. The same worker measures the
 * same runs on every layout, so what a measurer has learned about a book is
 * still worth something the next time the type size moves.
 */
internal fun paginateProgressively(
    sections: List<TextSection>,
    pageHeightPx: Int,
    sizesPerWorker: List<BlockSizes>,
): Flow<PaginatedBook> = flow {
    if (sections.isEmpty() || pageHeightPx <= 0 || sizesPerWorker.isEmpty()) {
        emit(PaginatedBook.Empty)
        return@flow
    }

    coroutineScope {
        // Handed over rather than shared. The one coroutine that decides what to
        // measure and the one that hands the book over own nothing between them
        // but this, which is what keeps a book that is growing at one end and
        // being read at the other from needing a lock.
        val runs = Channel<Deferred<PaginatedChunk>>(Channel.UNLIMITED)

        launch { dispatchRuns(sections, pageHeightPx, sizesPerWorker, runs) }

        emitInOrder(runs)
    }
}.flowOn(Dispatchers.Default)

/**
 * Sets every run of the book being measured at once.
 *
 * Which measurer a run gets is settled by where the run is in the book rather
 * than by whoever is free, so that a re-layout asks the same measurer about the
 * same chapters and what it has cached is still worth something. A lock per
 * measurer is what keeps two runs off the same one, and holding them all is also
 * what keeps the number of runs being measured at once down to the number of
 * measurers there are.
 */
private suspend fun dispatchRuns(
    sections: List<TextSection>,
    pageHeightPx: Int,
    sizesPerWorker: List<BlockSizes>,
    runs: SendChannel<Deferred<PaginatedChunk>>,
) = coroutineScope {
    val lanes = List(sizesPerWorker.size) { Mutex() }
    val starts = chunkStarts(sections)

    starts.forEachIndexed { index, start ->
        val lane = index % lanes.size
        val end = starts.getOrElse(index + 1) { sections.size }
        val run = sections.subList(start, end)

        runs.send(
            async {
                lanes[lane].withLock {
                    paginateChunk(run, sizesPerWorker[lane], pageHeightPx, start)
                }
            },
        )
    }

    runs.close()
}

/**
 * Waits for the runs in the order they are read in, and hands over the book as
 * far as it goes each time it has grown.
 *
 * In order, and never out of it: a page number is a position in the book, so a
 * run that finished early cannot be shown until everything in front of it has -
 * there would be no saying what number its first page was.
 *
 * Not once per run, though. A long book is a hundred of them, and a hundred
 * books arriving is a hundred times that the reader's list is rebuilt, the
 * contents are put back on their pages and the position is followed across a
 * re-layout. Held to one every [PUBLISH_INTERVAL] instead, which is often enough
 * that the page count climbs while somebody watches it and rare enough to cost
 * nothing. The first goes out at once, because it is the one somebody is waiting
 * for, and the last always goes out, because it is the only one that can say the
 * book is now as long as it is going to be.
 */
private suspend fun FlowCollector<PaginatedBook>.emitInOrder(
    runs: ReceiveChannel<Deferred<PaginatedChunk>>,
) {
    val pages = mutableListOf<TextPage>()
    val sectionPages = mutableListOf<Int>()

    var published = TimeSource.Monotonic.markNow()
    var handedOver = false

    while (true) {
        val run = runs.receiveCatching().getOrNull() ?: break

        val chunk = run.await()
        val firstPage = pages.size

        pages += chunk.pages
        // A run counts its pages from its own beginning, so where it landed in
        // the book has to be added back before anything can look a section up.
        sectionPages += chunk.sectionPages.map { it + firstPage }

        if (!handedOver || published.elapsedNow() >= PUBLISH_INTERVAL) {
            published = TimeSource.Monotonic.markNow()
            handedOver = true

            emit(PaginatedBook(pages.toList(), sectionPages.toList(), complete = false))
        }
    }

    emit(PaginatedBook(pages.toList(), sectionPages.toList(), complete = true))
}

/**
 * How many runs are measured at once.
 *
 * One per core, capped, for the reasons `readersFor` is capped: a phone that has
 * eight of them does not have eight cores' worth of thermal headroom, and every
 * worker holds a measurer and the layouts it has cached. Past a handful the book
 * is not measured meaningfully sooner and the reader's battery notices.
 */
internal fun paginationWorkers(): Int =
    availableProcessors().coerceIn(1, MAX_PAGINATION_WORKERS)

private const val MAX_PAGINATION_WORKERS = 4

/** How often a growing book is worth handing over while it grows. */
private val PUBLISH_INTERVAL = 150.milliseconds
