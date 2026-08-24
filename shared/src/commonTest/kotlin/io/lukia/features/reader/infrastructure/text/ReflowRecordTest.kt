package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.BlockAlignment
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.domain.plainText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A converted book written down and read back.
 *
 * The whole point of the record is that what comes out is the book that went
 * in, so most of what follows is that sentence made specific: every kind of
 * block, the marks inside a sentence, the pictures byte for byte. The rest is
 * the other half of the promise - that a note which no longer describes the
 * book on disk is refused rather than believed.
 */
class ReflowRecordTest {

    @Test
    fun aBookComesBackAsTheBookThatWentIn() {
        val restored = assertNotNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE))

        assertEquals(book.sections.size, restored.sections.size)
        assertEquals("Loomings", restored.sections.first().title)
        assertEquals(book.outline, restored.outline)
    }

    @Test
    fun everyKindOfBlockSurvives() {
        val restored = assertNotNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE))
        val blocks = restored.sections.flatMap { it.blocks }

        assertEquals(
            book.sections.flatMap { it.blocks }.map { it::class.simpleName },
            blocks.map { it::class.simpleName },
        )
    }

    @Test
    fun theMarksInsideASentenceSurvive() {
        val restored = assertNotNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE))
        val paragraph = restored.sections.first().blocks.filterIsInstance<TextBlock.Paragraph>()

        assertEquals("Call me Ishmael.", paragraph.first().text.plainText())
        assertEquals(setOf(Emphasis.Italic), paragraph.first().text.last().emphasis)
    }

    @Test
    fun whatABookAskedForItsOwnParagraphSurvives() {
        val restored = assertNotNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE))
        val quote = restored.sections.flatMap { it.blocks }.filterIsInstance<TextBlock.Quote>()

        assertEquals(BlockAlignment.Center, quote.single().alignment)
    }

    /** The reason the pictures are not written as text: they come back exact. */
    @Test
    fun aPictureComesBackByteForByte() {
        val restored = assertNotNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE))
        val figures = restored.sections.flatMap { it.blocks }.filterIsInstance<TextBlock.Figure>()

        assertContentEquals(PLATE, figures.single().bytes)
        assertEquals("A whale.", figures.single().description)
    }

    /**
     * Three states, and the middle one is the point: a document that has no
     * contents is not a document whose contents could not be read.
     */
    @Test
    fun aBookWithNoContentsIsNotABookThatCouldNotBeAsked() {
        val unaskable = ReflowableContent(sections = book.sections, outline = null)
        val empty = ReflowableContent(sections = book.sections, outline = emptyList())

        assertNull(assertNotNull(readRecord(unaskable.asRecord(1L), 1L)).outline)
        assertEquals(emptyList(), assertNotNull(readRecord(empty.asRecord(1L), 1L)).outline)
    }

    @Test
    fun aNoteAboutADifferentFileIsRefused() {
        assertNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE + 1))
    }

    @Test
    fun somethingThatIsNotANoteAtAllIsRefused() {
        assertNull(readRecord("not a converted book".encodeToByteArray(), SOURCE_SIZE))
        assertNull(readRecord(ByteArray(0), SOURCE_SIZE))
    }

    /**
     * What a write cut short by the app being killed leaves behind. Every
     * length in the file is a claim about bytes that are no longer there, and
     * believing one is an index out of bounds on the way into a book.
     */
    @Test
    fun aNoteCutShortIsRefusedRatherThanBelieved() {
        val record = book.asRecord(SOURCE_SIZE)

        for (kept in record.indices step 7) {
            assertNull(
                readRecord(record.copyOfRange(0, kept), SOURCE_SIZE),
                "a record of $kept of ${record.size} bytes was read as a book",
            )
        }
    }

    /**
     * Cut exactly where the last picture begins, which is what a write that ran
     * out of disk leaves. The structure parses perfectly and the book is still
     * refused, because a conversion that did not finish being written is one to
     * do again rather than one to show with a hole in it.
     */
    @Test
    fun aNoteMissingThePicturesItPromisedIsRefused() {
        val record = book.asRecord(SOURCE_SIZE)
        val withoutPlate = record.copyOfRange(0, record.size - PLATE.size)

        assertNull(readRecord(withoutPlate, SOURCE_SIZE))
    }

    @Test
    fun anAlignmentThisVersionHasNeverHeardOfIsDropped() {
        val restored = assertNotNull(readRecord(book.asRecord(SOURCE_SIZE), SOURCE_SIZE))
        val heading = restored.sections.first().blocks.first()

        assertIs<TextBlock.Heading>(heading)
        assertEquals(1, heading.level)
    }

    private companion object {
        const val SOURCE_SIZE = 4_096L

        val PLATE = ByteArray(512) { (it * 7).toByte() }

        val book = ReflowableContent(
            sections = listOf(
                TextSection(
                    title = "Loomings",
                    blocks = listOf(
                        TextBlock.Heading(listOf(TextRun("Loomings")), level = 1),
                        TextBlock.Paragraph(
                            listOf(
                                TextRun("Call me "),
                                TextRun("Ishmael.", setOf(Emphasis.Italic)),
                            ),
                        ),
                        TextBlock.ListItem(listOf(TextRun("A grim mouth")), marker = "•"),
                        TextBlock.Quote(
                            listOf(TextRun("Whenever it is a damp, drizzly November")),
                            alignment = BlockAlignment.Center,
                        ),
                        TextBlock.Code("val whale = white"),
                        TextBlock.Separator,
                        TextBlock.Figure(PLATE, description = "A whale."),
                    ),
                ),
                TextSection(title = null, blocks = listOf(TextBlock.Paragraph(listOf(TextRun("End."))))),
            ),
            outline = listOf(
                OutlineEntry(title = "Loomings", pageIndex = 0, level = 0, blockIndex = 2),
            ),
        )
    }
}
