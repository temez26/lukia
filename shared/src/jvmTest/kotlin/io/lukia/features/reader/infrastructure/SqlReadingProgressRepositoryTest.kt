package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.reader.domain.ReadingProgress
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlReadingProgressRepositoryTest {

    private val database = testDatabase()

    private val repository = SqlReadingProgressRepository(
        database = database,
        dispatcher = UnconfinedTestDispatcher()
    )

    /** Never opened is not the same as opened at page one. */
    @Test
    fun aBookThatWasNeverOpenedHasNoPosition() = runTest {
        assertNull(repository.progressFor("/books/one.pdf"))
    }

    @Test
    fun aPositionComesBackWhereItWasLeft() = runTest {
        repository.save("/books/one.pdf", ReadingProgress(pageIndex = 41, pageCount = 300))

        assertEquals(
            ReadingProgress(pageIndex = 41, pageCount = 300),
            repository.progressFor("/books/one.pdf")
        )
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
