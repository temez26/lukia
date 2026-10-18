package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.text.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a reflowable book is cut into pages that fit the window.
 */
class PaginatorTest {

    @Test
    fun aPageIsFilledWithWholeBlocksUntilTheNextOneDoesNotFit() {
        val book = pagesOf(chapter(null, filler(60), filler(30), filler(40)))

        assertEquals(
            listOf(listOf(0 to 0, 0 to 1), listOf(0 to 2)),
            book.places(),
        )
    }

    /** Nothing is dropped and nothing is shown twice: a page holds what follows the last. */
    @Test
    fun everyBlockOfTheBookIsOnExactlyOnePage() {
        val book = pagesOf(
            chapter("One", filler(70), filler(70)),
            chapter("Two", filler(30), filler(30), filler(30)),
        )

        assertEquals(
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 1 to 2),
            book.places().flatten(),
        )
    }

    /** A chapter starts on a page of its own, which is what makes it read as a chapter. */
    @Test
    fun aNamedSectionOpensANewPage() {
        val book = pagesOf(
            chapter("One", filler(10)),
            chapter("Two", filler(10)),
        )

        assertEquals(listOf(listOf(0 to 0), listOf(1 to 0)), book.places())
    }

    /**
     * A section with no name of its own is the rest of a chapter - the reader
     * divides an over-long file into several - and breaking the page there would
     * be a break in the middle of a sentence.
     */
    @Test
    fun anUnnamedContinuationRunsStraightOn() {
        val book = pagesOf(
            chapter("One", filler(10)),
            chapter(null, filler(10)),
        )

        assertEquals(listOf(listOf(0 to 0, 1 to 0)), book.places())
    }

    @Test
    fun aBlockTallerThanThePageIsCutAcrossPages() {
        val book = pagesOf(chapter(null, filler(250)))

        assertEquals(3, book.pageCount)
        assertEquals(
            listOf(PAGE_HEIGHT, PAGE_HEIGHT, 50),
            book.pages.map { page -> blockText(page.blocks.single().block).length },
        )
    }

    /** The second half of a paragraph is the same paragraph, and says so. */
    @Test
    fun theTailOfACutBlockIsMarkedAsAContinuation() {
        val book = pagesOf(chapter(null, filler(250)))

        assertFalse(book.pages.first().blocks.single().continued)
        assertTrue(book.pages.drop(1).all { it.blocks.single().continued })
        assertTrue(book.pages.all { it.blocks.single().blockIndex == 0 })
    }

    /**
     * And no piece of it is the whole paragraph, which is what decides whether it
     * can be asked about as one.
     *
     * A translation of a whole paragraph is filed under the block, so asking
     * about a piece of one would put the answer where the *other* piece would
     * find it too - and show one page's translation over another page's words.
     */
    @Test
    fun noPieceOfACutBlockCallsItselfTheWholeBlock() {
        val book = pagesOf(chapter(null, filler(250)))

        assertTrue(book.pages.none { it.blocks.single().whole }, "a piece called itself whole")
    }

    /** A paragraph that was never cut is the whole of itself. */
    @Test
    fun aBlockThatFitsIsTheWholeBlock() {
        val book = pagesOf(chapter(null, paragraph("Call me Ishmael.")))

        assertTrue(book.pages.single().blocks.single().whole)
    }

    /**
     * An illustration cannot be divided, so it gets the page it did not fit on -
     * and is drawn scaled down to it rather than running off the bottom.
     */
    @Test
    fun anUndividableBlockTallerThanThePageGetsAPageOfItsOwn() {
        val plate = TextBlock.Figure(bytes = ByteArray(0), description = null)

        val book = paginate(
            sections = listOf(chapter(null, filler(50), plate, filler(50))),
            sizes = object : BlockSizes by CharacterHeights {
                override fun height(block: TextBlock): Int =
                    if (block is TextBlock.Figure) PAGE_HEIGHT * 3 else CharacterHeights.height(block)
            },
            pageHeightPx = PAGE_HEIGHT,
        )

        assertEquals(
            listOf(listOf(0 to 0), listOf(0 to 1), listOf(0 to 2)),
            book.places(),
        )
    }

    @Test
    fun theSpaceBetweenBlocksCountsAgainstThePage() {
        val book = paginate(
            sections = listOf(chapter(null, filler(50), filler(50))),
            sizes = object : BlockSizes by CharacterHeights {
                override val spacingPx: Int = 10
            },
            pageHeightPx = PAGE_HEIGHT,
        )

        // 50 + 10 + 50 is over the hundred the page has, by the gap alone.
        assertEquals(listOf(listOf(0 to 0), listOf(0 to 1)), book.places())
    }

    @Test
    fun aBookWithNothingInItHasNoPages() {
        assertEquals(0, pagesOf().pageCount)
        assertEquals(0, paginate(listOf(chapter("One", filler(10))), CharacterHeights, 0).pageCount)
    }

    /** A heading goes over with the text it heads rather than ending a page on its own. */
    @Test
    fun aHeadingIsCarriedOverWithTheBlockThatDidNotFit() {
        val book = pagesOf(chapter(null, filler(60), heading("Section"), filler(50)))

        assertEquals(listOf(listOf(0 to 0), listOf(0 to 1, 0 to 2)), book.places())
    }

    /** A chapter's label and its title travel together. */
    @Test
    fun severalHeadingsAtTheFootOfAPageAreCarriedTogether() {
        val book = pagesOf(chapter(null, filler(70), heading("CH 7"), heading("Title"), filler(60)))

        assertEquals(listOf(listOf(0 to 0), listOf(0 to 1, 0 to 2, 0 to 3)), book.places())
    }

    /** Headings that are all a page holds stay, rather than leave an empty page behind. */
    @Test
    fun aPageOfOnlyHeadingsKeepsThem() {
        val book = pagesOf(chapter(null, heading("Title"), filler(98)))

        assertEquals(listOf(listOf(0 to 0), listOf(0 to 1)), book.places())
    }
}
