package io.lukia.features.reader.infrastructure.text.reflow

import io.lukia.core.infrastructure.WorkingCopyNaming
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A converted book kept beside the file it was converted from.
 *
 * What matters here is not the bytes - [ReflowRecordTest] has those - but the
 * three things the store promises about a file on disk: that what was written
 * is what comes back, that a note is refused once the document underneath it
 * has changed, and that the note is named so that the library takes it away
 * with the document without knowing it exists.
 */
class ReflowStoreTest {

    private val directory = createTempDirectory("lukia-reflow").toFile()

    @AfterTest
    fun tidy() {
        directory.deleteRecursively()
    }

    @Test
    fun aConvertedBookIsThereTheNextTimeItIsAskedFor() {
        val document = documentOf(bytes = 2_048)

        storeReflow(document.path, book)

        val restored = assertNotNull(storedReflow(document.path))

        assertEquals("Call me Ishmael.", restored.text())
    }

    @Test
    fun aBookThatWasNeverConvertedHasNothingKept() {
        assertNull(storedReflow(documentOf(bytes = 2_048).path))
    }

    @Test
    fun nothingIsKeptForAFileThatIsNotThere() {
        assertNull(storedReflow(File(directory, "doc-missing.pdf").path))
    }

    /**
     * The library replaces a working copy wholesale when it refreshes a
     * document, so a file of a different length is a different book - and
     * showing yesterday's text for it would be the reader's own edition of a
     * book they have just replaced.
     */
    @Test
    fun aNoteIsRefusedOnceTheDocumentUnderneathItHasChanged() {
        val document = documentOf(bytes = 2_048)

        storeReflow(document.path, book)
        document.writeBytes(ByteArray(4_096))

        assertNull(storedReflow(document.path))
    }

    @Test
    fun aNoteThatCannotBeReadIsSimplyNotThere() {
        val document = documentOf(bytes = 2_048)

        storeReflow(document.path, book)
        File("${document.path}.text").writeText("this is not a converted book")

        assertNull(storedReflow(document.path))
    }

    /**
     * How the note gets cleaned up: every workspace discards a document by
     * deleting whatever begins with its prefix, so the note has to begin with
     * it too or it would outlive the book it describes.
     */
    @Test
    fun aNoteIsNamedSoThatDiscardingTheDocumentTakesIt() {
        val location = "/somewhere/Moby-Dick.pdf"
        val document = File(directory, WorkingCopyNaming.fileName(location, "pdf"))

        document.writeBytes(ByteArray(2_048))
        storeReflow(document.path, book)

        val kept = directory.listFiles()
            ?.filter { it.name.startsWith(WorkingCopyNaming.prefix(location)) }
            .orEmpty()

        assertEquals(2, kept.size, "kept: ${kept.map { it.name }}")
        assertTrue(kept.any { it.name.endsWith(".text") })
    }

    private fun documentOf(bytes: Int): File =
        File(directory, "doc-$bytes.pdf").apply { writeBytes(ByteArray(bytes)) }

    private fun ReflowableContent.text(): String =
        sections.flatMap { it.blocks }.filterIsInstance<TextBlock.Paragraph>()
            .single()
            .text
            .plainText()

    private companion object {
        val book = ReflowableContent(
            sections = listOf(
                TextSection(
                    title = null,
                    blocks = listOf(TextBlock.Paragraph(listOf(TextRun("Call me Ishmael.")))),
                ),
            ),
            outline = emptyList(),
        )
    }
}
