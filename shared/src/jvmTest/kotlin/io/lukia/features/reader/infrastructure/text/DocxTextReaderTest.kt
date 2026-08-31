package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.text.Emphasis
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocxTextReaderTest {

    @Test
    fun styledParagraphsBecomeHeadingsAndDivideTheDocument() {
        val content = read(
            paragraph("Chapter One", style = "Heading1") +
                paragraph("Opening words.") +
                paragraph("Chapter Two", style = "Heading1") +
                paragraph("More words."),
        )

        assertNotNull(content)
        assertEquals(listOf("Chapter One", "Chapter Two"), content.sections.map { it.title })
        assertEquals(
            listOf("Chapter One" to 0, "Chapter Two" to 1),
            content.outline.orEmpty().map { it.title to it.pageIndex },
        )
    }

    /** The same style is written `Heading 1` by one producer and `heading1` by another. */
    @Test
    fun headingStylesAreMatchedHoweverTheyAreWritten() {
        val content = read(
            paragraph("One", style = "Heading 1") +
                paragraph("Two", style = "heading1"),
        )

        assertNotNull(content)
        assertEquals(listOf("One", "Two"), content.sections.map { it.title })
    }

    @Test
    fun runFormattingSurvivesAsEmphasis() {
        val body = """
            <w:p>
              <w:r><w:t xml:space="preserve">the </w:t></w:r>
              <w:r><w:rPr><w:i/></w:rPr><w:t>Pequod</w:t></w:r>
              <w:r><w:t xml:space="preserve"> sailed </w:t></w:r>
              <w:r><w:rPr><w:b/></w:rPr><w:t>at dawn</w:t></w:r>
            </w:p>
        """.trimIndent()

        val runs = (read(body)!!.blocks().single() as TextBlock.Paragraph).text

        assertEquals("the Pequod sailed at dawn", runs.plainText())
        assertEquals(
            listOf(emptySet(), setOf(Emphasis.Italic), emptySet(), setOf(Emphasis.Bold)),
            runs.map { it.emphasis },
        )
    }

    /**
     * Word switches a style's bold back off for one run by writing the toggle
     * with a false value, which is not the same as not writing it at all.
     */
    @Test
    fun aToggleSetToFalseIsNotEmphasis() {
        val body = """
            <w:p>
              <w:r><w:rPr><w:b w:val="0"/></w:rPr><w:t>not bold</w:t></w:r>
            </w:p>
        """.trimIndent()

        val runs = (read(body)!!.blocks().single() as TextBlock.Paragraph).text

        assertTrue(runs.all { it.emphasis.isEmpty() })
    }

    /** A run with no properties of its own inherits nothing from the run before it. */
    @Test
    fun formattingDoesNotLeakFromOneRunToTheNext() {
        val body = """
            <w:p>
              <w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r>
              <w:r><w:t xml:space="preserve"> plain</w:t></w:r>
            </w:p>
        """.trimIndent()

        val runs = (read(body)!!.blocks().single() as TextBlock.Paragraph).text

        assertEquals(setOf(Emphasis.Bold), runs.first().emphasis)
        assertEquals(emptySet(), runs.last().emphasis)
    }

    @Test
    fun numberedParagraphsBecomeListItems() {
        val body = """
            <w:p>
              <w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr>
              <w:r><w:t>first</w:t></w:r>
            </w:p>
        """.trimIndent()

        val item = read(body)!!.blocks().single() as TextBlock.ListItem

        assertEquals("first", item.text.plainText())
    }

    @Test
    fun quoteStylesBecomeQuotations() {
        val content = read(paragraph("Call me Ishmael.", style = "IntenseQuote"))

        assertTrue(content!!.blocks().single() is TextBlock.Quote)
    }

    /**
     * Word writes vertical space as empty paragraphs, and every one of them
     * carried over would be a gap that meant nothing once the text was re-set.
     */
    @Test
    fun emptyParagraphsAreNotBlocks() {
        val content = read(paragraph("Words.") + "<w:p/>" + "<w:p><w:r><w:t></w:t></w:r></w:p>")

        assertEquals(1, content!!.blocks().size)
    }

    /** Field instructions and deleted text are both invisible in Word itself. */
    @Test
    fun fieldInstructionsAndDeletedTextAreNotProse() {
        val body = """
            <w:p>
              <w:r><w:t xml:space="preserve">See page </w:t></w:r>
              <w:r><w:instrText>PAGEREF _Ref12345 \h</w:instrText></w:r>
              <w:del><w:r><w:delText>removed</w:delText></w:r></w:del>
              <w:r><w:t>7</w:t></w:r>
            </w:p>
        """.trimIndent()

        assertEquals(
            "See page 7",
            (read(body)!!.blocks().single() as TextBlock.Paragraph).text.plainText(),
        )
    }

    @Test
    fun picturesAreFollowedThroughTheRelationshipTable() {
        val plate = byteArrayOf(9, 9, 9)

        val body = """
            <w:p><w:r><w:drawing><a:blip r:embed="rId7"/></w:drawing></w:r></w:p>
        """.trimIndent()

        val content = read(
            body = body,
            relationships = """<Relationship Id="rId7" Target="media/image1.png"/>""",
            extra = mapOf("word/media/image1.png" to plate),
        )

        val figure = content!!.blocks().filterIsInstance<TextBlock.Figure>().single()

        assertEquals(plate.toList(), figure.bytes.toList())
    }

    @Test
    fun anArchiveWithNoDocumentPartIsNotAWordDocument() {
        assertNull(readDocx("not a zip at all".encodeToByteArray()))
        assertNull(readDocx(zip(mapOf("hello.txt" to "hi"))))
    }

    private fun paragraph(text: String, style: String? = null): String {
        val properties = style?.let { """<w:pPr><w:pStyle w:val="$it"/></w:pPr>""" }.orEmpty()

        return "<w:p>$properties<w:r><w:t>$text</w:t></w:r></w:p>"
    }

    private fun read(
        body: String,
        relationships: String = "",
        extra: Map<String, Any> = emptyMap(),
    ) = readDocx(
        zip(
            mapOf(
                "word/document.xml" to
                    """<?xml version="1.0"?><w:document><w:body>$body</w:body></w:document>""",
                "word/_rels/document.xml.rels" to
                    """<?xml version="1.0"?><Relationships>$relationships</Relationships>""",
            ) + extra,
        ),
    )

    private fun io.lukia.features.reader.domain.ReflowableContent.blocks(): List<TextBlock> =
        sections.flatMap(TextSection::blocks)

    private fun zip(entries: Map<String, Any>): ByteArray {
        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { stream ->
            entries.forEach { (name, content) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(
                    when (content) {
                        is ByteArray -> content
                        else -> content.toString().encodeToByteArray()
                    },
                )
                stream.closeEntry()
            }
        }

        return output.toByteArray()
    }
}
