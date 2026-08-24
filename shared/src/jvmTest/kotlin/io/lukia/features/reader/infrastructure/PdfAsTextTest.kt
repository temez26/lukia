package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.plainText
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A real PDF read as text, through the real library.
 *
 * What the inference does with glyphs is settled in `PdfPageBlocksTest` and
 * `PdfTextReaderTest` against pages laid out by hand. What is left to prove is
 * the part only a file can answer: that a document set as a heading over two
 * paragraphs comes back as a heading over two paragraphs, at the sizes and
 * positions a real typesetter put them at.
 */
class PdfAsTextTest {

    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-astext-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun aTypesetPageComesBackAsAHeadingAndItsProse() = runTest {
        val file = write(
            Line("Loomings", y = 740f, size = 20f),
            Line("Call me Ishmael. Some years ago, never mind how long", y = 690f),
            Line("precisely, having little or no money in my purse, I", y = 676f),
            Line("thought I would sail about a little.", y = 662f),
        )

        val content = assertIs<ReflowableContent>(openPdfAsText(file))
        val blocks = content.sections.single().blocks

        val heading = assertIs<TextBlock.Heading>(blocks.first())
        assertEquals("Loomings", heading.text.plainText())

        val prose = assertIs<TextBlock.Paragraph>(blocks.last())
        assertTrue(prose.text.plainText().startsWith("Call me Ishmael."))
        assertTrue(prose.text.plainText().endsWith("sail about a little."))
    }

    /** The whole point of the feature: a PDF arrives as a reflowable book. */
    @Test
    fun theBookHasOneSectionPerPageOfTheFile() = runTest {
        val file = write(
            Line("The first page.", y = 700f),
            Line("The second page.", y = 700f, page = 1),
        )

        val content = assertIs<ReflowableContent>(openPdfAsText(file))

        assertEquals(2, content.sections.size)
    }

    /**
     * The pictures come across too, in the place they were printed. This is the
     * whole chain: a real image embedded in a real file, found by walking the
     * page as a renderer would, re-encoded, and set between the paragraphs it
     * was drawn between.
     */
    @Test
    fun aPictureOnThePageIsSetBetweenTheParagraphsItWasDrawnBetween() = runTest {
        val file = write(
            lines = listOf(
                Line("The paragraph above the plate.", y = 740f),
                Line("The paragraph below the plate.", y = 300f),
            ),
            pictures = listOf(Picture(x = 100f, y = 400f, width = 200f, height = 150f)),
        )

        val content = assertIs<ReflowableContent>(openPdfAsText(file))
        val blocks = content.sections.single().blocks

        assertEquals(3, blocks.size, "expected a paragraph, a picture and a paragraph")
        assertIs<TextBlock.Paragraph>(blocks[0])
        assertIs<TextBlock.Figure>(blocks[1])
        assertIs<TextBlock.Paragraph>(blocks[2])
        assertTrue(assertIs<TextBlock.Figure>(blocks[1]).bytes.isNotEmpty())
    }

    /** A rule across the page is drawn as an image, and is not an illustration. */
    @Test
    fun somethingTooSmallToBeAnIllustrationIsLeftBehind() = runTest {
        val file = write(
            lines = listOf(Line("A page with a rule under its heading.", y = 700f)),
            pictures = listOf(Picture(x = 60f, y = 690f, width = 480f, height = 2f)),
        )

        val content = assertIs<ReflowableContent>(openPdfAsText(file))

        assertTrue(content.sections.single().blocks.none { it is TextBlock.Figure })
    }

    /**
     * The reason any of this is kept: converting a book is the most expensive
     * thing the reader does, and somebody comparing the two ways of reading the
     * same book asks for it twice within a few seconds.
     */
    @Test
    fun aBookIsConvertedOnceAndReadBackFromThereAfterwards() = runTest {
        val file = write(Line("Call me Ishmael.", y = 700f))

        var conversions = 0
        val first = assertIs<ReflowableContent>(openPdfAsText(file) { conversions++ })
        val again = assertIs<ReflowableContent>(openPdfAsText(file) { conversions++ })

        assertEquals(1, conversions, "the book was read out of the file more than once")
        assertEquals(first.textOfFirstBlock(), again.textOfFirstBlock())
    }

    /**
     * A note is about one file. Serving it for the book that replaced that file
     * would show the reader the edition they have just got rid of.
     */
    @Test
    fun aBookIsConvertedAgainOnceItsFileHasChanged() = runTest {
        val file = write(Line("Call me Ishmael.", y = 700f))
        val replacement = write(Line("It is a way I have of driving off the spleen.", y = 700f))

        var conversions = 0
        openPdfAsText(file) { conversions++ }
        File(file).writeBytes(File(replacement).readBytes())
        val second = assertIs<ReflowableContent>(openPdfAsText(file) { conversions++ })

        assertTrue(conversions > 1, "the replaced book was served out of the old note")
        assertTrue(second.textOfFirstBlock().startsWith("It is a way"))
    }

    private fun ReflowableContent.textOfFirstBlock(): String =
        assertIs<TextBlock.Paragraph>(sections.first().blocks.first()).text.plainText()

    /** A file of blank pages is a stand-in for a scan: pages, and no words. */
    @Test
    fun aFileWithNothingWrittenInItCannotBeReadAsText() = runTest {
        assertNull(openPdfAsText(write()))
    }

    private fun PDDocument.set(page: PDPage, lines: List<Line>, pictures: List<Picture>) {
        PDPageContentStream(this, page).use { content ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)

            lines.forEach { line ->
                content.beginText()
                content.setFont(font, line.size)
                content.newLineAtOffset(60f, line.y)
                content.showText(line.text)
                content.endText()
            }

            pictures.forEach { picture ->
                val image = LosslessFactory.createFromImage(this, swatch())
                content.drawImage(image, picture.x, picture.y, picture.width, picture.height)
            }
        }
    }

    private data class Line(
        val text: String,
        val y: Float,
        val size: Float = 12f,
        val page: Int = 0,
    )

    /** A picture drawn on the page, in the page's own units. */
    private data class Picture(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val page: Int = 0,
    )

    private fun write(vararg lines: Line): String = write(lines.toList(), emptyList())

    private fun write(lines: List<Line>, pictures: List<Picture>): String {
        val file = File(root, "book-${lines.hashCode()}-${pictures.hashCode()}.pdf")

        PDDocument().use { document ->
            val lastPage = maxOf(
                lines.maxOfOrNull { it.page } ?: 0,
                pictures.maxOfOrNull { it.page } ?: 0,
            )

            val pages = List(lastPage + 1) { PDPage(PDRectangle.A4).also(document::addPage) }

            pages.forEachIndexed { index, page ->
                document.set(
                    page = page,
                    lines = lines.filter { it.page == index },
                    pictures = pictures.filter { it.page == index },
                )
            }

            document.save(file)
        }

        return file.absolutePath
    }

    /** A small checkerboard, which is a real image without being a large one. */
    private fun swatch(): BufferedImage {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)

        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                image.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xFF3366 else 0xFFFFFF)
            }
        }

        return image
    }
}
