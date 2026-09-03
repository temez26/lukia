package io.lukia.features.library

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.authorLine
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.model.lengthLabel
import io.lukia.features.library.presentation.model.progressLabel
import io.lukia.features.library.presentation.model.readActionLabel
import io.lukia.shared.composed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The half of the book's particulars that is now words out of the resources -
 * the credit line, the length and the position - which takes a composition to
 * read and so cannot sit in `commonTest` beside the rest. What a book is called
 * and what language it declares stay in `MetadataDisplayTest`.
 */
class BookDetailsDisplayTest {

    // ---------------------------------------------------------------- authors

    @Test
    fun oneAuthorIsJustTheName() {
        assertEquals(
            "Herman Melville",
            composed { BookMetadata(authors = listOf("Herman Melville")).authorLine() },
        )
    }

    /** Two names read as a credit rather than as a list. */
    @Test
    fun twoAuthorsAreJoinedWithAnd() {
        assertEquals(
            "Ada Lovelace and Charles Babbage",
            composed {
                BookMetadata(authors = listOf("Ada Lovelace", "Charles Babbage")).authorLine()
            },
        )
    }

    @Test
    fun moreThanTwoAuthorsTakeCommasAndAFinalAnd() {
        assertEquals(
            "One, Two and Three",
            composed { BookMetadata(authors = listOf("One", "Two", "Three")).authorLine() },
        )
    }

    @Test
    fun noAuthorsIsNoLine() {
        assertNull(composed { BookMetadata().authorLine() })
    }

    // ----------------------------------------------------------------- length

    /** The length travels with the stored position, so an unopened book has none. */
    @Test
    fun aBookNeverOpenedHasNoLength() {
        assertNull(composed { BookDetails(document = book()).lengthLabel() })
    }

    @Test
    fun aPdfIsMeasuredInPages() {
        val details = BookDetails(
            document = book(fileName = "one.pdf", type = DocumentType.PDF),
            progress = ReadingProgress(pageIndex = 0, pageCount = 348),
        )

        assertEquals("348 pages", composed { details.lengthLabel() })
    }

    /**
     * A reflowable book is measured in pages too - the reader's own, set at the
     * size it was last read at, which is what the stored position counted.
     */
    @Test
    fun aReflowableBookIsMeasuredInThePagesItWasReadIn() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 0, pageCount = 24),
        )

        assertEquals("24 pages", composed { details.lengthLabel() })
    }

    /** The one form comes from the plural resource now, not from an `if`. */
    @Test
    fun oneOfSomethingIsNotPluralised() {
        val details = BookDetails(
            document = book(fileName = "one.pdf", type = DocumentType.PDF),
            progress = ReadingProgress(pageIndex = 0, pageCount = 1),
        )

        assertEquals("1 page", composed { details.lengthLabel() })
    }

    // --------------------------------------------------------------- progress

    @Test
    fun anUnopenedBookReportsThatItHasNotBeenStarted() {
        val details = BookDetails(document = book())

        assertEquals("Not started", composed { details.progressLabel() })
        assertEquals("Start reading", composed { details.readActionLabel() })
    }

    /**
     * The page rather than a percentage: the percentage is already on the cover
     * beside this, and the page is what the reader will be shown the moment they
     * open the book.
     */
    @Test
    fun aBookPartWayThroughReportsThePageItWasLeftOn() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 41, pageCount = 100),
        )

        assertEquals("Page 42 of 100", composed { details.progressLabel() })
        assertEquals("Continue reading", composed { details.readActionLabel() })
    }

    /**
     * A reflowable book set larger since it was last read is shorter than the
     * position stored against it, and "page 400 of 380" is not a place.
     */
    @Test
    fun aPositionPastTheEndOfTheBookIsReportedAsItsLastPage() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 400, pageCount = 380),
        )

        assertEquals("Finished", composed { details.progressLabel() })
        assertEquals(380, details.progress?.pageNumber)
    }

    /** A book whose length is not known yet has no page to report. */
    @Test
    fun aPositionWithNoLengthBehindItReportsNothing() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 0, pageCount = 0),
        )

        assertEquals("Not started", composed { details.progressLabel() })
        assertNull(composed { details.lengthLabel() })
    }

    @Test
    fun aFinishedBookSaysSoOutright() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 99, pageCount = 100),
        )

        assertEquals("Finished", composed { details.progressLabel() })
        assertEquals("Read again", composed { details.readActionLabel() })
    }

    private fun book(
        fileName: String = "moby-dick.epub",
        type: DocumentType = DocumentType.EPUB,
    ) = DocumentInfo(
        document = PickedDocument(
            fileName = fileName,
            extension = fileName.substringAfterLast('.'),
            location = "/books/$fileName",
        ),
        type = type,
        exists = true,
        sizeBytes = 1024L,
    )
}
