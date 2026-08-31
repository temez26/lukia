package io.lukia.features.reader.infrastructure.pdf

import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.domain.page.TextQuad
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextExtraction
import io.lukia.features.reader.domain.text.plainText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A whole PDF read as text: what becomes of the pages, and what becomes of the
 * paragraphs the page breaks fell in the middle of.
 */
class PdfTextReaderTest {

    /**
     * The sections have to stay one per page of the file, because that is what
     * the book's own table of contents points at - an entry says "page 12", and
     * the reader resolves it as the twelfth section.
     */
    @Test
    fun everyPageOfTheFileIsASectionOfTheBook() = runTest {
        val content = assertNotNullContent(
            readPdfAsText(FakePdfPages(listOf(page("The first."), page("The second.")))),
        )

        assertEquals(2, content.sections.size)
        assertTrue(content.sections.all { it.title == null }, "a named section opens a page")
    }

    @Test
    fun theFilesOwnContentsIsCarriedAcross() = runTest {
        val outline = listOf(OutlineEntry(title = "Loomings", pageIndex = 1, level = 0))
        val pages = FakePdfPages(listOf(page("The first."), page("The second.")), outline)

        assertEquals(outline, assertNotNullContent(readPdfAsText(pages)).outline)
    }

    /**
     * A page break falls where the paper ended, which is very often mid
     * sentence. Left alone, each half is a paragraph that stops in the middle of
     * a thought.
     */
    @Test
    fun aParagraphBrokenByThePageBreakIsPutBackTogether() = runTest {
        val pages = FakePdfPages(
            listOf(
                page("Call me Ishmael. Some years ago, never mind how long"),
                page("precisely, I thought I would sail about a little."),
            ),
        )

        val content = assertNotNullContent(readPdfAsText(pages))
        val joined = assertIs<TextBlock.Paragraph>(content.sections.first().blocks.single())

        assertEquals(
            "Call me Ishmael. Some years ago, never mind how long precisely, " +
                "I thought I would sail about a little.",
            joined.text.plainText(),
        )
        assertTrue(content.sections[1].blocks.isEmpty(), "the tail was left on both pages")
    }

    /** A page that opens a new sentence opens a new paragraph. */
    @Test
    fun aPageThatBeginsASentenceIsLeftWhereItIs() = runTest {
        val pages = FakePdfPages(
            listOf(
                page("Call me Ishmael."),
                page("Some years ago I thought I would sail about a little."),
            ),
        )

        val content = assertNotNullContent(readPdfAsText(pages))

        assertEquals(1, content.sections[0].blocks.size)
        assertEquals(1, content.sections[1].blocks.size)
    }

    /** A word broken over the page break is one word, not two joined by a space. */
    @Test
    fun aHyphenatedWordAcrossThePageBreakIsOneWord() = runTest {
        val pages = FakePdfPages(listOf(page("An exam-"), page("ple of it.")))

        val content = assertNotNullContent(readPdfAsText(pages))
        val joined = assertIs<TextBlock.Paragraph>(content.sections.first().blocks.single())

        assertEquals("An exam-ple of it.", joined.text.plainText())
    }

    @Test
    fun thePicturesOnAPageAreCarriedIntoTheText() = runTest {
        val pages = FakePdfPages(
            pages = listOf(page("A diagram follows."), page("And the text after it.")),
            pictures = listOf(listOf(picture(byteArrayOf(1, 2, 3))), emptyList()),
        )

        val content = assertNotNullContent(readPdfAsText(pages))

        val figure = assertIs<TextBlock.Figure>(content.sections.first().blocks.last())
        assertContentEquals(byteArrayOf(1, 2, 3), figure.bytes)
    }

    /**
     * The publisher's logo, the journal's banner, the watermark: drawn on every
     * page, and a picture between every two paragraphs if it is believed.
     */
    @Test
    fun aPictureOnEveryPageIsTheLetterheadRatherThanAnIllustration() = runTest {
        val logo = byteArrayOf(9, 9, 9)
        val pages = FakePdfPages(
            pages = List(4) { page("Page ${'$'}{it + 1}. Something written on it.") },
            pictures = List(4) { listOf(picture(logo.copyOf())) },
        )

        val content = assertNotNullContent(readPdfAsText(pages))

        assertTrue(
            content.sections.none { section -> section.blocks.any { it is TextBlock.Figure } },
            "the letterhead was re-set into the book",
        )
    }

    /** Three pages out of four is a letterhead; one out of four is a picture. */
    @Test
    fun aPictureOnOnePageIsKept() = runTest {
        val pages = FakePdfPages(
            pages = List(4) { page("Page ${'$'}{it + 1}. Something written on it.") },
            pictures = listOf(listOf(picture(byteArrayOf(4, 5, 6))), emptyList(), emptyList(), emptyList()),
        )

        val content = assertNotNullContent(readPdfAsText(pages))

        assertTrue(content.sections.first().blocks.any { it is TextBlock.Figure })
    }

    /**
     * A scan is pictures of words. Re-setting it would give a list of
     * photographs of pages, which is the fixed layout with the reader's own
     * page controls taken away.
     */
    @Test
    fun aDocumentOfNothingButPicturesIsNotSomethingToReSet() = runTest {
        val pages = FakePdfPages(
            pages = listOf(emptyList(), emptyList()),
            pictures = List(2) { listOf(picture(byteArrayOf(it.toByte()))) },
        )

        assertNull(readPdfAsText(pages))
    }

    /** A scan has pages of pictures of words and no words at all. */
    @Test
    fun aDocumentWithNoTextLayerCannotBeReadAsText() = runTest {
        assertNull(readPdfAsText(FakePdfPages(listOf(emptyList(), emptyList()))))
    }

    @Test
    fun progressIsReportedPageByPage() = runTest {
        val reported = mutableListOf<TextExtraction>()

        readPdfAsText(FakePdfPages(listOf(page("One."), page("Two."), page("Three.")))) {
            reported += it
        }

        assertEquals(listOf(1, 2, 3), reported.map { it.pagesRead })
        assertTrue(reported.all { it.pageCount == 3 })
    }

    /**
     * A long book is read through several handles at once, one run of pages
     * each - so what has to hold is that the book which comes back is the book
     * the one handle produced, in the same order, with the same letterhead
     * found and the same paragraphs joined across the same page breaks.
     */
    @Test
    fun readingThroughSeveralHandlesGivesBackTheSameBook() = runTest {
        val logo = byteArrayOf(9, 9, 9)
        val written = List(PAGES_TO_DIVIDE) { index ->
            // Every third page ends mid-sentence, so the join across the page
            // break falls inside runs and on the seam between two of them.
            if (index % 3 == 0) {
                page("Page ${'$'}{index + 1} began a sentence that runs on")
            } else {
                page("Page ${'$'}{index + 1}. Something written on it.")
            }
        }
        val pictures = List(PAGES_TO_DIVIDE) { index ->
            when {
                index % 2 == 0 -> listOf(picture(logo.copyOf()))
                index == 7 -> listOf(picture(byteArrayOf(4, 5, 6)))
                else -> emptyList()
            }
        }

        val alone = assertNotNullContent(readPdfAsText(FakePdfPages(written, pictures = pictures)))
        val divided = assertNotNullContent(
            readPdfAsText(
                pages = FakePdfPages(written, pictures = pictures),
                alongside = List(3) { FakePdfPages(written, pictures = pictures) },
            ),
        )

        assertEquals(alone.sections.size, divided.sections.size)

        alone.sections.zip(divided.sections).forEachIndexed { index, (expected, actual) ->
            assertEquals(
                expected.blocks.map { it.readable() },
                actual.blocks.map { it.readable() },
                "page ${'$'}{index + 1} came back different",
            )
        }
    }

    /** Every handle still counts its pages towards the one total. */
    @Test
    fun progressAcrossSeveralHandlesCountsTheWholeBook() = runTest {
        val written = List(PAGES_TO_DIVIDE) { page("Page ${'$'}{it + 1}. Written on.") }
        val reported = mutableListOf<TextExtraction>()

        readPdfAsText(
            pages = FakePdfPages(written),
            alongside = List(3) { FakePdfPages(written) },
        ) { reported += it }

        assertEquals(
            (1..PAGES_TO_DIVIDE).toList(),
            reported.map { it.pagesRead },
            "the pages were counted twice or not at all",
        )
        assertTrue(reported.all { it.pageCount == PAGES_TO_DIVIDE })
    }

    /**
     * A block as something two runs of the same book can be compared by:
     * [TextBlock.Figure] is its own identity, so a picture read through one
     * handle is never the picture read through another however alike they are.
     */
    private fun TextBlock.readable(): Any = when (this) {
        is TextBlock.Figure -> "figure:${'$'}{bytes.toList()}:${'$'}description"
        else -> this
    }

    /** One picture, the size a diagram is, well inside the page. */
    private fun picture(bytes: ByteArray) = PageImage(
        bytes = bytes,
        bounds = TextQuad(left = 100f, top = 400f, right = 300f, bottom = 550f),
    )

    private fun assertNotNullContent(content: ReflowableContent?) =
        requireNotNull(content) { "the document came back with no text" }

    /** One page holding one line of ordinary type, well inside the margins. */
    private fun page(text: String): List<PageGlyph> = text.mapIndexed { index, character ->
        PageGlyph(
            text = character.toString(),
            bounds = TextQuad(
                left = index * 6f,
                top = 300f,
                right = index * 6f + 6f,
                bottom = 310f,
            ),
        )
    }

    private class FakePdfPages(
        private val pages: List<List<PageGlyph>>,
        override val outline: List<OutlineEntry>? = null,
        private val pictures: List<List<PageImage>> = emptyList(),
    ) : DocumentPages {

        override val pageSizes: List<PageSize> =
            pages.map { PageSize(width = 595f, height = 842f) }

        override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

        override suspend fun pageText(index: Int): List<PageGlyph>? =
            pages.getOrNull(index)?.takeIf { it.isNotEmpty() }

        override suspend fun pageImages(index: Int): List<PageImage> =
            pictures.getOrElse(index) { emptyList() }

        override fun close() = Unit
    }
}

/** Enough pages that four handles each get a run of them. */
private const val PAGES_TO_DIVIDE = 13
