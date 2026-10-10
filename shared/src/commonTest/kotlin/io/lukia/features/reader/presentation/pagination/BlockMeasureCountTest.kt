package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.text.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How often filling a page asks a block how tall it is.
 *
 * A performance property rather than a behavioural one, and here because it is
 * the kind that comes back: every question the page-filling rule asks reads
 * naturally as `sizes.height(block)`, and four of those in one pass looked
 * perfectly reasonable until somebody counted. It measured 4.3 calls per block.
 *
 * It matters because of what a real [BlockMetrics] does with the question -
 * rebuild the block's runs into an `AnnotatedString` and compare a whole
 * paragraph against the measurer's cache - and because on iOS the whole layout
 * runs on the thread drawing the reader. See `measuringContext`.
 *
 * The counts below are exact rather than bounded. A ceiling would pass just as
 * happily at three as at one, and three is the bug.
 */
class BlockMeasureCountTest {

    @Test
    fun measuresEachBlockOnceWhenBlocksFitWhole() {
        val counting = CountingSizes()

        // Thirty blocks of thirty against a page of a hundred: three to a page,
        // and every fourth one starting a new page rather than fitting the old.
        // Both routes through the fill are covered, and neither cuts.
        paginate(
            sections = listOf(chapter("One", *Array(30) { filler(30) })),
            sizes = counting,
            pageHeightPx = PAGE_HEIGHT,
        )

        assertEquals(30, counting.heightCalls, "one measure per block")
        assertEquals(0, counting.fittingCalls, "nothing here is cut")
    }

    @Test
    fun measuresOnlyTheHalvesItMakesWhenABlockHasToBeCut() {
        val counting = CountingSizes()

        // One block of 250 against a page of 100: cut twice, into three pieces.
        // The block itself is measured once, and each head the cut produces is a
        // block that did not exist before and has to be measured on its own.
        paginate(
            sections = listOf(chapter("One", filler(250))),
            sizes = counting,
            pageHeightPx = PAGE_HEIGHT,
        )

        assertTrue(
            counting.heightCalls <= 5,
            "one per block plus one per head made, got ${counting.heightCalls}",
        )
    }

    @Test
    fun stillPaginatesTheSameWayItDidBeforeTheCountWasCut() {
        // The guard on the above: a measure count is only worth holding if the
        // pages are still the pages. Blocks of 40 against a page of 100 put two
        // on a page with 20 to spare - the case where a third block would fit
        // the page only if the rule got the remaining room wrong.
        val book = pagesOf(chapter("One", *Array(6) { filler(40) }))

        assertEquals(
            listOf(
                listOf(0 to 0, 0 to 1),
                listOf(0 to 2, 0 to 3),
                listOf(0 to 4, 0 to 5),
            ),
            book.places(),
        )
    }
}

/** [CharacterHeights], counting what it is asked. */
private class CountingSizes(private val inner: BlockSizes = CharacterHeights) : BlockSizes {

    var heightCalls = 0
        private set

    var fittingCalls = 0
        private set

    override val spacingPx: Int get() = inner.spacingPx

    override fun height(block: TextBlock): Int {
        heightCalls++

        return inner.height(block)
    }

    override fun fittingLength(block: TextBlock, heightPx: Int): Int {
        fittingCalls++

        return inner.fittingLength(block, heightPx)
    }
}
