package io.lukia.features.library

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.core.presentation.languageName
import io.lukia.core.presentation.publishedYear
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.model.lengthLabel
import io.lukia.features.library.presentation.model.progressLabel
import io.lukia.features.library.presentation.model.readActionLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetadataDisplayTest {

    private fun book(
        fileName: String = "moby-dick.epub",
        type: DocumentType = DocumentType.EPUB,
        metadata: BookMetadata? = null,
    ) = DocumentInfo(
        document = PickedDocument(
            fileName = fileName,
            extension = fileName.substringAfterLast('.'),
            location = "/books/$fileName",
        ),
        type = type,
        exists = true,
        sizeBytes = 1024L,
        metadata = metadata,
    )

    // ------------------------------------------------------------------ title

    /** A book that names itself names itself better than the file name does. */
    @Test
    fun prefersTheTitleTheDocumentDeclares() {
        val info = book(metadata = BookMetadata(title = "Moby Dick; or, The Whale"))

        assertEquals("Moby Dick; or, The Whale", info.bookTitle())
    }

    @Test
    fun fallsBackToTheFileNameWithoutItsExtension() {
        assertEquals("moby-dick", book().bookTitle())
    }

    @Test
    fun aBlankDeclaredTitleIsNoTitle() {
        assertEquals("moby-dick", book(metadata = BookMetadata(title = "  ")).bookTitle())
    }

    // ---------------------------------------------------------------- authors

    @Test
    fun oneAuthorIsJustTheName() {
        assertEquals("Herman Melville", BookMetadata(authors = listOf("Herman Melville")).authorLine())
    }

    /** Two names read as a credit rather than as a list. */
    @Test
    fun twoAuthorsAreJoinedWithAnd() {
        assertEquals(
            "Ada Lovelace and Charles Babbage",
            BookMetadata(authors = listOf("Ada Lovelace", "Charles Babbage")).authorLine(),
        )
    }

    @Test
    fun moreThanTwoAuthorsTakeCommasAndAFinalAnd() {
        assertEquals(
            "One, Two and Three",
            BookMetadata(authors = listOf("One", "Two", "Three")).authorLine(),
        )
    }

    @Test
    fun noAuthorsIsNoLine() {
        assertNull(BookMetadata().authorLine())
    }

    // --------------------------------------------------------------- language

    @Test
    fun readsALanguageTagAsALanguage() {
        assertEquals("English", BookMetadata(language = "en").languageName())
    }

    /** The region is a distinction for a spell checker, not for a reader. */
    @Test
    fun ignoresTheRegionSubtag() {
        assertEquals("English", BookMetadata(language = "en-GB").languageName())
        assertEquals("Finnish", BookMetadata(language = "fi_FI").languageName())
    }

    /** A tag nobody listed is still more use to show than to hide. */
    @Test
    fun anUnknownTagIsShownAsItself() {
        assertEquals("AY", BookMetadata(language = "ay").languageName())
    }

    @Test
    fun noLanguageIsNoRow() {
        assertNull(BookMetadata().languageName())
    }

    // -------------------------------------------------------------- published

    @Test
    fun takesTheYearOutOfAFullDate() {
        assertEquals("1851", BookMetadata(published = "1851-11-14T00:00:00Z").publishedYear())
    }

    @Test
    fun leavesABareYearAlone() {
        assertEquals("2018", BookMetadata(published = "2018").publishedYear())
    }

    /** A book that says "Spring 1851" is telling the truth in its own way. */
    @Test
    fun passesThroughSomethingThatIsNotAYearFirst() {
        assertEquals("Spring 1851", BookMetadata(published = "Spring 1851").publishedYear())
    }

    // ----------------------------------------------------------------- length

    /** The length travels with the stored position, so an unopened book has none. */
    @Test
    fun aBookNeverOpenedHasNoLength() {
        assertNull(BookDetails(document = book()).lengthLabel())
    }

    @Test
    fun aPdfIsMeasuredInPages() {
        val details = BookDetails(
            document = book(fileName = "one.pdf", type = DocumentType.PDF),
            progress = ReadingProgress(pageIndex = 0, pageCount = 348),
        )

        assertEquals("348 pages", details.lengthLabel())
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

        assertEquals("24 pages", details.lengthLabel())
    }

    @Test
    fun oneOfSomethingIsNotPluralised() {
        val details = BookDetails(
            document = book(fileName = "one.pdf", type = DocumentType.PDF),
            progress = ReadingProgress(pageIndex = 0, pageCount = 1),
        )

        assertEquals("1 page", details.lengthLabel())
    }

    // --------------------------------------------------------------- progress

    @Test
    fun anUnopenedBookReportsThatItHasNotBeenStarted() {
        val details = BookDetails(document = book())

        assertEquals("Not started", details.progressLabel())
        assertEquals("Start reading", details.readActionLabel())
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

        assertEquals("Page 42 of 100", details.progressLabel())
        assertEquals("Continue reading", details.readActionLabel())
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

        assertEquals("Finished", details.progressLabel())
        assertEquals(380, details.progress?.pageNumber)
    }

    /** A book whose length is not known yet has no page to report. */
    @Test
    fun aPositionWithNoLengthBehindItReportsNothing() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 0, pageCount = 0),
        )

        assertEquals("Not started", details.progressLabel())
        assertNull(details.lengthLabel())
    }

    @Test
    fun aFinishedBookSaysSoOutright() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 99, pageCount = 100),
        )

        assertEquals("Finished", details.progressLabel())
        assertEquals("Read again", details.readActionLabel())
    }
}
