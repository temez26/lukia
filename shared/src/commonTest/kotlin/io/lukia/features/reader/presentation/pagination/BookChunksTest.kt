package io.lukia.features.reader.presentation.pagination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a book may be cut into runs that lay out on their own.
 *
 * The rule these hold is the one the whole design rests on: a cut is only ever
 * made where a page was going to end anyway. `ProgressivePaginationTest` checks
 * that the pages come out the same; this checks that the cuts are in the places
 * that make it so.
 */
class BookChunksTest {

    /** A chapter is only a place to cut once there is enough behind it to be worth cutting. */
    @Test
    fun aRunIsGrownUntilItIsWorthCuttingAndThenCutAtTheNextChapter() {
        val sections = List(10) { index -> chapter("Chapter $index", *Array(100) { filler(10) }) }

        // Three chapters is three hundred blocks, so the cut falls at the first
        // chapter past the threshold rather than at every chapter there is.
        assertEquals(listOf(0, 3, 6, 9), chunkStarts(sections))
    }

    /** A chapter that does not name itself is the rest of the one before it, not a new one. */
    @Test
    fun anUnnamedSectionIsNeverACut() {
        val sections = List(10) { chapter(null, *Array(100) { filler(10) }) }

        assertEquals(listOf(0), chunkStarts(sections))
    }

    /**
     * A book cut nowhere is the case a single long document gives - see
     * `Sectioning` - and it has to keep working, one run and all.
     */
    @Test
    fun aBookWithNothingToCutIsOneRun() {
        val sections = listOf(chapter("Only", *Array(5000) { filler(10) }))

        assertEquals(listOf(0), chunkStarts(sections))
    }

    @Test
    fun aBookWithNothingInItHasNoRuns() {
        assertEquals(emptyList(), chunkStarts(emptyList()))
    }

    /** The book always starts a run, whether or not its first chapter names itself. */
    @Test
    fun theFirstRunBeginsAtTheBeginning() {
        val named = List(8) { index -> chapter("Chapter $index", *Array(200) { filler(10) }) }
        val unnamed = listOf(chapter(null, filler(10))) + named

        assertEquals(0, chunkStarts(named).first())
        assertEquals(0, chunkStarts(unnamed).first())
    }

    /** Every cut lands on a chapter, because that is the only place a page was going to end. */
    @Test
    fun everyCutFallsOnAChapterThatOpensAPage() {
        val sections = List(30) { index ->
            chapter(if (index % 3 == 0) "Chapter $index" else null, *Array(120) { filler(10) })
        }

        chunkStarts(sections).drop(1).forEach { start ->
            assertTrue(sections[start].title != null, "run at section $start opens no page")
        }
    }
}
