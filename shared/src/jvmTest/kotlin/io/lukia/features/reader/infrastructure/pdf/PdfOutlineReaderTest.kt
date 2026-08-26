package io.lukia.features.reader.infrastructure.pdf

import io.lukia.core.infrastructure.pdf.ByteArrayPdfSource
import io.lukia.core.infrastructure.pdf.MinimalPdf
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.infrastructure.openPdfPages
import org.apache.pdfbox.pdfwriter.compress.CompressParameters
import org.apache.pdfbox.pdmodel.PDDestinationNameTreeNode
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parser exists for Android, but it is common code - so it is tested here,
 * against files a real producer wrote, and cross-checked against PDFBox reading
 * the very same bytes.
 */
class PdfOutlineReaderTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-outline-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun readsAFlatOutline() {
        val file = pdf(pageCount = 6) { document ->
            document.flatOutline("Introduction" to 0, "The Middle" to 2, "The End" to 5)
        }

        assertEquals(
            listOf("Introduction" to 0, "The Middle" to 2, "The End" to 5),
            file.outline()?.map { it.title to it.pageIndex },
        )
    }

    /**
     * The modern default: the catalog, the page tree and the outline all live
     * compressed inside object streams, and the cross-reference table is itself
     * a stream. A reader that only understands the classic layout finds nothing
     * in a file like this - which is most files.
     */
    @Test
    fun readsAnOutlineFromACompressedFile() {
        val file = pdf(pageCount = 6, compress = true) { document ->
            document.flatOutline("Introduction" to 0, "The Middle" to 2, "The End" to 5)
        }

        assertEquals(
            listOf("Introduction" to 0, "The Middle" to 2, "The End" to 5),
            file.outline()?.map { it.title to it.pageIndex },
        )
    }

    @Test
    fun nestedSectionsCarryTheirDepth() {
        val file = pdf(pageCount = 4) { document ->
            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline

            val chapter = document.outlineItem("Chapter One", 0)
            outline.addLast(chapter)
            chapter.addLast(document.outlineItem("A Section", 1))
            chapter.addLast(document.outlineItem("Another Section", 2))
            outline.addLast(document.outlineItem("Chapter Two", 3))
        }

        assertEquals(
            listOf("Chapter One" to 0, "A Section" to 1, "Another Section" to 1, "Chapter Two" to 0),
            file.outline()?.map { it.title to it.level },
        )
    }

    /** LaTeX and friends address every chapter by name rather than by page. */
    @Test
    fun followsNamedDestinations() {
        val file = pdf(pageCount = 4) { document ->
            val destination = PDPageFitWidthDestination().apply { page = document.getPage(2) }
            document.nameDestination("section.1", destination)

            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline

            outline.addLast(
                PDOutlineItem().apply {
                    title = "By Name"
                    action = PDActionGoTo().apply { this.destination = PDNamedDestination("section.1") }
                },
            )
        }

        assertEquals(listOf("By Name" to 2), file.outline()?.map { it.title to it.pageIndex })
    }

    @Test
    fun readsTitlesThatAreNotAscii() {
        val file = pdf(pageCount = 2) { document ->
            document.flatOutline("Kapitel Ä — Einführung" to 0, "Глава 2" to 1)
        }

        assertEquals(
            listOf("Kapitel Ä — Einführung", "Глава 2"),
            file.outline()?.map { it.title },
        )
    }

    @Test
    fun aDocumentWithoutAnOutlineHasNoChapters() {
        assertEquals(emptyList(), pdf(pageCount = 3).outline())
    }

    /**
     * The scan reads the file in windows. An outline that sits past the first
     * one has to be found just the same.
     */
    @Test
    fun readsAnOutlineBeyondTheFirstScanWindow() {
        val file = pdf(pageCount = 60, bulky = true) { document ->
            document.flatOutline("Beginning" to 0, "Far In" to 59)
        }

        assertTrue(file.length() > 1 shl 20, "expected a file larger than one scan window")
        assertEquals(
            listOf("Beginning" to 0, "Far In" to 59),
            file.outline()?.map { it.title to it.pageIndex },
        )
    }

    /** Strings in an encrypted file would come back as ciphertext. */
    @Test
    fun anEncryptedFileReportsNoOutlineRatherThanNonsense() {
        val file = pdf(pageCount = 2) { document ->
            document.flatOutline("Chapter One" to 0)
        }

        assertNull(file.withAppendedTrailer(encrypted = true).outline())
    }

    /**
     * A file whose encryption has been removed keeps the revision that declared
     * it and appends one that does not - which is how a shop selling a DRM-free
     * copy of an encrypted book produces the file. Reading it as encrypted
     * anyway took the chapter list off two of every three real books tried.
     */
    @Test
    fun aFileThatHadItsEncryptionRemovedIsReadAsWhatItNowIs() {
        val file = pdf(pageCount = 2) { document ->
            document.flatOutline("Chapter One" to 0)
        }

        val decrypted = file.withAppendedTrailer(encrypted = true)
            .withAppendedTrailer(encrypted = false)

        assertEquals(listOf("Chapter One"), decrypted.outline()?.map { it.title })
    }

    /**
     * The word turns up in the text of a book often enough, and inside a
     * compressed stream by chance; neither says anything about the file.
     */
    @Test
    fun theWordEncryptInsideTheFileIsNotADeclarationOfEncryption() {
        val file = pdf(pageCount = 2) { document ->
            document.flatOutline("On /Encrypt and other trailers" to 0)
        }

        assertEquals(
            listOf("On /Encrypt and other trailers"),
            file.outline()?.map { it.title },
        )
    }

    /**
     * Appends a revision of the file carrying nothing but a trailer, pointing at
     * the same catalog the file already has - which is what an incremental
     * update is.
     */
    private fun File.withAppendedTrailer(encrypted: Boolean): File {
        val bytes = readBytes()
        val root = Regex("/Root (\\d+) (\\d+) R").find(bytes.decodeToString())
            ?: error("no /Root in the file PDFBox wrote")

        val trailer = buildString {
            append("\ntrailer << /Root ${root.groupValues[1]} ${root.groupValues[2]} R")
            if (encrypted) append(" /Encrypt 9 0 R")
            append(" >>\n")
        }

        return File(directory, "revised-${kotlin.random.Random.nextInt()}.pdf")
            .apply { writeBytes(bytes + trailer.encodeToByteArray()) }
    }

    /**
     * The same bytes the Android device test uses. Reading them identically here
     * is what makes that test's result meaningful: a difference between the two
     * is then the platform, not the file.
     */
    @Test
    fun readsTheHandWrittenFixtureTheDeviceTestUses() {
        val outline = PdfOutlineReader(ByteArrayPdfSource(MinimalPdf.withOutline())).read()

        assertEquals(
            listOf(MinimalPdf.FIRST_CHAPTER_TITLE to 0, MinimalPdf.SECOND_CHAPTER_TITLE to 2),
            outline?.map { it.title to it.pageIndex },
        )
    }

    /** And a real PDF library has to accept it, or the device test proves nothing. */
    @Test
    fun theHandWrittenFixtureIsAValidPdf() {
        val file = File(directory, "fixture.pdf").apply { writeBytes(MinimalPdf.withOutline()) }

        openPdfPages(file.absolutePath).use { pages ->
            assertEquals(MinimalPdf.PAGE_COUNT, pages.pageCount)
            assertEquals(
                listOf(MinimalPdf.FIRST_CHAPTER_TITLE, MinimalPdf.SECOND_CHAPTER_TITLE),
                pages.outline?.map { it.title },
            )
        }
    }

    @Test
    fun aFileThatIsNotAPdfReadsAsNothing() {
        val file = File(directory, "not-a.pdf").apply { writeText("this is not a pdf at all") }

        assertNull(file.outline())
    }

    @Test
    fun anEmptyFileReadsAsNothing() {
        val file = File(directory, "empty.pdf").apply { writeBytes(ByteArray(0)) }

        assertNull(file.outline())
    }

    /** Truncation is what a half-finished download looks like. */
    @Test
    fun aTruncatedFileDoesNotFail() {
        val file = pdf(pageCount = 6) { document ->
            document.flatOutline("Introduction" to 0, "The End" to 5)
        }
        val truncated = File(directory, "truncated.pdf").apply {
            writeBytes(file.readBytes().copyOfRange(0, file.length().toInt() / 2))
        }

        // Whatever it finds is acceptable; failing to open the book is not.
        truncated.outline()
    }

    /**
     * The strongest check available: on the same bytes, this parser and the one
     * the desktop build actually ships must say the same thing.
     */
    @Test
    fun agreesWithPdfBoxOnTheSameFile() {
        listOf(false, true).forEach { compressed ->
            val file = pdf(pageCount = 8, compress = compressed) { document ->
                val outline = PDDocumentOutline()
                document.documentCatalog.documentOutline = outline

                val first = document.outlineItem("Front Matter", 0)
                outline.addLast(first)
                first.addLast(document.outlineItem("Preface", 1))
                outline.addLast(document.outlineItem("Body", 3))
                outline.addLast(document.outlineItem("Index", 7))
            }

            val fromPdfBox = openPdfPages(file.absolutePath).use { it.outline }
            val fromParser = file.outline()

            assertEquals(fromPdfBox, fromParser, "compressed=$compressed")
            assertTrue(fromParser?.isNotEmpty() == true)
        }
    }

    private fun File.outline(): List<OutlineEntry>? =
        PdfOutlineReader(ByteArrayPdfSource(readBytes())).read()

    private fun PDDocument.flatOutline(vararg entries: Pair<String, Int>) {
        val outline = PDDocumentOutline()
        documentCatalog.documentOutline = outline
        entries.forEach { (title, pageIndex) -> outline.addLast(outlineItem(title, pageIndex)) }
    }

    private fun PDDocument.outlineItem(title: String, pageIndex: Int): PDOutlineItem =
        PDOutlineItem().apply {
            this.title = title
            destination = PDPageFitWidthDestination().apply { page = getPage(pageIndex) }
        }

    private fun PDDocument.nameDestination(
        name: String,
        destination: PDPageFitWidthDestination,
    ) {
        val tree = PDDestinationNameTreeNode().apply { names = mapOf(name to destination) }

        documentCatalog.names = PDDocumentNameDictionary(documentCatalog).apply { dests = tree }
    }

    /**
     * @param bulky fills each page with uncompressed text, so the file grows
     *   past the reader's scan window.
     */
    private fun pdf(
        pageCount: Int,
        compress: Boolean = false,
        bulky: Boolean = false,
        configure: (PDDocument) -> Unit = {},
    ): File {
        val file = File(directory, "book-${kotlin.random.Random.nextInt()}.pdf")

        PDDocument().use { document ->
            repeat(pageCount) { document.addPage(PDPage(PDRectangle.A4)) }

            if (bulky) document.pages.forEach { page -> document.fillPage(page) }

            configure(document)

            if (compress) {
                document.save(file, CompressParameters.DEFAULT_COMPRESSION)
            } else {
                document.save(file, CompressParameters.NO_COMPRESSION)
            }
        }

        return file
    }

    private fun PDDocument.fillPage(page: PDPage) {
        PDPageContentStream(this, page, PDPageContentStream.AppendMode.APPEND, false).use { content ->
            content.beginText()
            content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 8f)
            content.newLineAtOffset(20f, 800f)
            repeat(60) {
                content.showText("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(6))
                content.newLineAtOffset(0f, -12f)
            }
            content.endText()
        }
    }
}
