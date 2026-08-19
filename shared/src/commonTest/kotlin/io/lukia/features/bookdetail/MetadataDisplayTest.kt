package io.lukia.features.bookdetail

import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.bookdetail.presentation.authorLine
import io.lukia.features.bookdetail.presentation.bookTitle
import io.lukia.features.bookdetail.presentation.languageName
import io.lukia.features.bookdetail.presentation.lengthLabel
import io.lukia.features.bookdetail.presentation.progressLabel
import io.lukia.features.bookdetail.presentation.publishedYear
import io.lukia.features.bookdetail.presentation.readActionLabel
import io.lukia.features.reader.domain.ReadingProgress
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
     * Calling a reflowable book's sections pages would promise a correspondence
     * to a printed book that reflowed text does not have.
     */
    @Test
    fun aReflowableBookIsMeasuredInSections() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 0, pageCount = 24),
        )

        assertEquals("24 sections", details.lengthLabel())
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

    @Test
    fun aBookPartWayThroughReportsHowFar() {
        val details = BookDetails(
            document = book(),
            progress = ReadingProgress(pageIndex = 41, pageCount = 100),
        )

        assertEquals("42% read", details.progressLabel())
        assertEquals("Continue reading", details.readActionLabel())
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
