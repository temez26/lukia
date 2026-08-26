package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.ReadingProgress
import io.lukia.core.infrastructure.testDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlReadingProgressRepositoryTest {

    private val database = testDatabase()

    private val repository = SqlReadingProgressRepository(
        database = database,
        dispatcher = UnconfinedTestDispatcher(),
    )

    /** Never opened is not the same as opened at page one. */
    @Test
    fun aBookThatWasNeverOpenedHasNoPosition() = runTest {
        assertNull(repository.progressFor("/books/one.pdf"))
    }

    @Test
    fun aPositionComesBackWhereItWasLeft() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 41, pageCount = 300))

        val stored = repository.progressFor("/books/one.pdf")

        assertEquals(41, stored?.pageIndex)
        assertEquals(300, stored?.pageCount)
    }

    @Test
    fun readingOnMovesThePositionRatherThanAddingASecondOne() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 41, pageCount = 300))
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 42, pageCount = 300))

        assertEquals(42, repository.progressFor("/books/one.pdf")?.pageIndex)
    }

    @Test
    fun eachBookIsRememberedSeparately() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 10, pageCount = 100))
        repository.save("/books/two.pdf", ReadingProgress(pageIndex = 20, pageCount = 200))

        assertEquals(10, repository.progressFor("/books/one.pdf")?.pageIndex)
        assertEquals(20, repository.progressFor("/books/two.pdf")?.pageIndex)
    }

    @Test
    fun aPositionCanBeForgotten() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 41, pageCount = 300))

        repository.forget("/books/one.pdf")

        assertNull(repository.progressFor("/books/one.pdf"))
    }

    /**
     * The library can be ordered by what was read last, so a position has to
     * know where it stands against the others. A count rather than a clock -
     * see `ReadingPosition.sq` - so this asserts on the order it puts books in
     * rather than on the numbers themselves.
     */
    @Test
    fun theBookReadMostRecentlyStandsAboveTheRest() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 1, pageCount = 100))
        repository.save("/books/two.pdf", ReadingProgress(pageIndex = 1, pageCount = 100))

        val afterTwo = repository.allProgress()
        assertTrue(afterTwo.getValue("/books/two.pdf").readAt > afterTwo.getValue("/books/one.pdf").readAt)

        // Reading on in the first book puts it back on top, rather than leaving
        // it where it was when it was first opened.
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 2, pageCount = 100))

        val afterMore = repository.allProgress()
        assertTrue(afterMore.getValue("/books/one.pdf").readAt > afterMore.getValue("/books/two.pdf").readAt)
    }

    /** The store stamps it, so whatever a caller passes in is ignored. */
    @Test
    fun aCallerCannotChooseWhereItsBookStands() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 1, pageCount = 100, readAt = 9_999L))

        assertEquals(1L, repository.progressFor("/books/one.pdf")?.readAt)
    }

    /**
     * The length is stored with the page rather than derived from the file, so
     * that how far through a book someone is can be answered without opening
     * it. It has to survive the round trip for that to be worth anything.
     */
    @Test
    fun theLengthOfTheBookIsStoredWithThePage() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 41, pageCount = 300))

        assertEquals(300, repository.progressFor("/books/one.pdf")?.pageCount)
    }
}
