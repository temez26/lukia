package io.lukia.features.bookdetail

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.TestDocuments
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.reader.domain.ReadingProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookDetailsTest {

    private val book = DocumentInfo(
        document = TestDocuments.pdf(location = "/books/one.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L,
    )

    private fun details(pageIndex: Int, pageCount: Int) = BookDetails(
        document = book,
        progress = ReadingProgress(pageIndex = pageIndex, pageCount = pageCount),
    )

    @Test
    fun aBookNeverOpenedHasNoPercentage() {
        val details = BookDetails(document = book)

        assertNull(details.percentRead)
        assertFalse(details.isFinished)
        assertFalse(details.isInProgress)
    }

    /**
     * Counted from the page the reader is on rather than the pages behind them.
     * "0% read" on a book you are looking at is a claim the reader can see is
     * wrong.
     */
    @Test
    fun theFirstPageOfABookIsNotZeroPercent() {
        assertEquals(1, details(pageIndex = 0, pageCount = 300).percentRead)
    }

    @Test
    fun theLastPageIsAHundredPercent() {
        val details = details(pageIndex = 299, pageCount = 300)

        assertEquals(100, details.percentRead)
        assertTrue(details.isFinished)
        assertFalse(details.isInProgress)
    }

    @Test
    fun aBookPartWayThroughIsInProgress() {
        val details = details(pageIndex = 41, pageCount = 100)

        assertEquals(42, details.percentRead)
        assertTrue(details.isInProgress)
        assertFalse(details.isFinished)
    }

    /** A position past the end is a stored value the file has since outgrown. */
    @Test
    fun aPositionPastTheEndDoesNotExceedAHundred() {
        assertEquals(100, details(pageIndex = 500, pageCount = 300).percentRead)
    }

    /** A length of zero would divide by nothing; there is no percentage to give. */
    @Test
    fun aDocumentWithNoLengthHasNoPercentage() {
        assertNull(details(pageIndex = 0, pageCount = 0).percentRead)
    }
}
