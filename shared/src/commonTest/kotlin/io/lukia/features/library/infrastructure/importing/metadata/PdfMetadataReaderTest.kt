package io.lukia.features.library.infrastructure.importing.metadata

import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.ByteArraySource
import io.lukia.core.infrastructure.pdf.MinimalPdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfMetadataReaderTest {

    @Test
    fun readsTheInformationDictionary() {
        val metadata = extractPdfMetadata(
            ByteArraySource(
                MinimalPdf.withInfo(
                    "/Title (The Elements of Typographic Style) /Author (Robert Bringhurst)",
                ),
            ),
        )

        assertEquals("The Elements of Typographic Style", metadata?.title)
        assertEquals(listOf("Robert Bringhurst"), metadata?.authors)
    }

    /**
     * Producers write the source document's file name into `/Title` far more
     * often than they write a real one, and showing that as the book's title is
     * worse than showing none.
     */
    @Test
    fun dropsATitleThatIsPlainlyAFileName() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Title (Microsoft Word - report_final_v3.docx) /Author (Jane Roe)")),
        )

        assertNull(metadata?.title)
        // The author is still worth having; only the title was suspect.
        assertEquals(listOf("Jane Roe"), metadata?.authors)
    }

    @Test
    fun dropsATitleThatIsJustAHash() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Title (8f1c4b2e9a77d3e10c5b) /Author (Jane Roe)")),
        )

        assertNull(metadata?.title)
    }

    /**
     * The rule that drops a one-character title is about initials, which is a
     * fact about alphabets. A script that writes a whole word in one character
     * writes titles in one too, and dropping those left the reader looking at a
     * file name where the book had told them its name.
     */
    @Test
    fun keepsAOneCharacterTitleInAScriptThatWritesWordsThatWay() {
        val metadata = extractPdfMetadata(
            // UTF-16 with a byte-order mark, which is how a PDF holds anything
            // PDFDocEncoding cannot spell.
            ByteArraySource(MinimalPdf.withInfo("/Title <FEFF9053>")),
        )

        assertEquals("道", metadata?.title)
    }

    @Test
    fun stillDropsAOneLetterTitle() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Title (A) /Author (Jane Roe)")),
        )

        assertNull(metadata?.title)
    }

    /** `/Author` is one string however many people it names. */
    @Test
    fun splitsSeveralAuthorsOutOfOneField() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Author (Ada Lovelace; Charles Babbage)")),
        )

        assertEquals(listOf("Ada Lovelace", "Charles Babbage"), metadata?.authors)
    }

    @Test
    fun splitsAuthorsJoinedByAnd() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Author (Ada Lovelace and Charles Babbage)")),
        )

        assertEquals(listOf("Ada Lovelace", "Charles Babbage"), metadata?.authors)
    }

    /**
     * A PDF date is the moment the file was generated, which for a scanned book
     * is decades after it was published - so only the year is kept, and only as
     * the year.
     */
    @Test
    fun keepsOnlyTheYearFromAPdfDate() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Author (Jane Roe) /CreationDate (D:20180415103000Z)")),
        )

        assertEquals("2018", metadata?.published)
    }

    @Test
    fun readsKeywordsAsSubjects() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Author (Jane Roe) /Keywords (typography, design, printing)")),
        )

        assertEquals(listOf("typography", "design", "printing"), metadata?.subjects)
    }

    /** Most PDFs carry an Info dictionary with nothing worth showing in it. */
    @Test
    fun aDictionaryWithNothingUsableIsNothing() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Producer (Acrobat Distiller 9.0) /Creator (PScript5.dll)")),
        )

        assertNull(metadata)
    }

    /**
     * What the document says it is written in, which PDF keeps in the catalog
     * rather than the information dictionary.
     *
     * Read because a reader holding a word in a PDF and taking it out to a
     * dictionary was sent there with no source language - the site's own "any
     * language" dash - while the same word in an EPUB arrived with the pair the
     * book declared.
     */
    @Test
    fun readsTheLanguageTheDocumentDeclares() {
        val metadata = extractPdfMetadata(
            ByteArraySource(MinimalPdf.withInfo("/Title (A Real Book)", catalogEntries = "/Lang (en-GB) ")),
        )

        assertEquals("en-GB", metadata?.language)
    }

    /** And reads it from a file that carries no information dictionary at all. */
    @Test
    fun andFromAFileWithNoInformationDictionary() {
        val metadata = extractPdfMetadata(ByteArraySource(MinimalPdf.withCatalog("/Lang (fi) ")))

        assertEquals("fi", metadata?.language)
    }

    /** A file that says nothing still says nothing. */
    @Test
    fun aFileWithNoInfoDictionaryOrLanguageIsNothing() {
        assertNull(extractPdfMetadata(ByteArraySource(MinimalPdf.withOutline())))
    }

    /** Bytes that are not a PDF at all must come back as nothing, not throw. */
    @Test
    fun somethingThatIsNotAPdfIsNothing() {
        assertNull(extractPdfMetadata(ByteArraySource("not a pdf, not even close".encodeToByteArray())))
    }

    /** The switch above it is what every caller actually uses. */
    @Test
    fun theExtractionSwitchRoutesPdfsHere() {
        val metadata = extractMetadata(
            type = io.lukia.core.domain.document.DocumentType.PDF,
            document = ByteArraySource(MinimalPdf.withInfo("/Title (A Real Book) /Author (Jane Roe)")),
        )

        assertEquals("A Real Book", metadata?.title)
    }

    @Test
    fun plainTextCarriesNoMetadataToRead() {
        assertTrue(
            extractMetadata(
                type = io.lukia.core.domain.document.DocumentType.TEXT,
                document = ByteArraySource("Call me Ishmael.".encodeToByteArray()),
            ) == null,
        )
    }
}
