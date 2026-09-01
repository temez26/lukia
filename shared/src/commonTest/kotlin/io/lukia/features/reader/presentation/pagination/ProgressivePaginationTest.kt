package io.lukia.features.reader.presentation.pagination

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That a book laid out in pieces is the same book as one laid out in one go.
 *
 * The whole design rests on this: the reader is shown a beginning of the book
 * before the rest of it has been measured, and that is only honest if the pages
 * it is shown are the pages the finished book has. A run cut in the wrong place
 * would push a paragraph onto a page it does not belong on, and nobody would see
 * it until the page numbers stopped matching the ones in a stored position.
 */
class ProgressivePaginationTest {

    /**
     * Twenty named chapters, which is more than enough to be cut into several
     * runs - see `chunkStarts` - and small enough to count by hand if this ever
     * fails.
     */
    private val book = List(20) { index ->
        chapter("Chapter $index", *Array(30) { filler(40) })
    }

    private val workers = List(4) { CharacterHeights }

    @Test
    fun aBookLaidOutARunAtATimeHasTheSamePagesAsOneLaidOutInOneGo() = runTest {
        val whole = paginate(book, CharacterHeights, PAGE_HEIGHT)
        val progressive = paginateProgressively(book, PAGE_HEIGHT, workers, EmptyCoroutineContext)
            .toList()
            .last()

        assertEquals(whole.pageCount, progressive.pageCount)
        assertEquals(whole.places(), progressive.places())
    }

    /** Every section still opens on the page it opened on, which is what the contents point at. */
    @Test
    fun everySectionOpensOnTheSamePage() = runTest {
        val whole = paginate(book, CharacterHeights, PAGE_HEIGHT)
        val progressive = paginateProgressively(book, PAGE_HEIGHT, workers, EmptyCoroutineContext)
            .toList()
            .last()

        book.indices.forEach { section ->
            assertEquals(whole.pageOf(section), progressive.pageOf(section), "section $section")
        }
    }

    /**
     * What the reader is handed part way through is a true beginning of the
     * book: not a sample of it, not the pages reordered, but the first n pages
     * the finished book has.
     */
    @Test
    fun everyBookHandedOverIsABeginningOfTheFinishedOne() = runTest {
        val laid = paginateProgressively(book, PAGE_HEIGHT, workers, EmptyCoroutineContext).toList()
        val whole = laid.last()

        laid.forEach { partial ->
            assertEquals(
                whole.pages.take(partial.pageCount).map { it.blocks },
                partial.pages.map { it.blocks },
            )
        }
    }

    /** Only the last one is the whole book, and it says so. */
    @Test
    fun onlyTheFinishedBookCallsItselfComplete() = runTest {
        val laid = paginateProgressively(book, PAGE_HEIGHT, workers, EmptyCoroutineContext).toList()

        assertTrue(laid.last().complete)
        laid.dropLast(1).forEach { assertFalse(it.complete) }
    }

    /**
     * A book gets longer as it arrives, and never shorter. The page count in the
     * chrome climbs while somebody watches it, which is the one visible cost of
     * showing a book before it has been measured.
     */
    @Test
    fun theBookOnlyEverGetsLonger() = runTest {
        val counts = paginateProgressively(book, PAGE_HEIGHT, workers, EmptyCoroutineContext)
            .toList()
            .map { it.pageCount }

        assertEquals(counts.sorted(), counts)
    }

    /**
     * A book nobody can cut - no chapter names anything - is still laid out,
     * just not in pieces.
     */
    @Test
    fun aBookWithNoNamedChaptersIsLaidOutInOneRun() = runTest {
        val unnamed = List(10) { chapter(null, *Array(30) { filler(40) }) }

        val measured = paginate(unnamed, CharacterHeights, PAGE_HEIGHT)
        val laid = paginateProgressively(unnamed, PAGE_HEIGHT, workers, EmptyCoroutineContext).toList()

        assertEquals(measured.places(), laid.last().places())
        assertTrue(laid.all { it.places() == measured.places() }, "one run, one set of pages")
    }

    @Test
    fun aBookWithNothingInItIsAnEmptyBook() = runTest {
        val laid = paginateProgressively(emptyList(), PAGE_HEIGHT, workers, EmptyCoroutineContext)

        assertEquals(0, laid.toList().single().pageCount)
    }

    /** A window with no height to it is not one to lay a book out for. */
    @Test
    fun aBookIsNotLaidOutForAWindowWithNoRoomInIt() = runTest {
        val laid = paginateProgressively(book, pageHeightPx = 0, sizesPerWorker = workers, EmptyCoroutineContext)

        assertEquals(0, laid.toList().single().pageCount)
    }

    /**
     * One worker is the case a single-core device gets, and the case a book too
     * short to divide gets. It must be the same book.
     */
    @Test
    fun oneWorkerLaysOutTheSameBookAsFour() = runTest {
        val alone = paginateProgressively(book, PAGE_HEIGHT, listOf(CharacterHeights), EmptyCoroutineContext)
            .toList()
        val together = paginateProgressively(book, PAGE_HEIGHT, workers, EmptyCoroutineContext).toList()

        assertEquals(together.last().places(), alone.last().places())
    }
}
