package io.lukia.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The bar on a library card and the percentage on the detail screen are drawn
 * from these two properties, so what they agree on is what those two screens
 * agree on.
 */
class ReadingProgressTest {

    @Test
    fun aBookAtItsFirstPageIsAPageIn() {
        val progress = ReadingProgress(pageIndex = 0, pageCount = 100)

        assertEquals(0.01f, progress.fraction)
        // Never rounded down to nothing: a bar showing zero on a book the reader
        // is looking at is a claim they can see is wrong.
        assertEquals(1, progress.percent)
    }

    @Test
    fun aBookAtItsLastPageIsFull() {
        val progress = ReadingProgress(pageIndex = 299, pageCount = 300)

        assertEquals(1f, progress.fraction)
        assertEquals(100, progress.percent)
    }

    @Test
    fun aBookPartWayThroughIsThatFarThrough() {
        val progress = ReadingProgress(pageIndex = 41, pageCount = 100)

        assertEquals(0.42f, progress.fraction)
        assertEquals(42, progress.percent)
    }

    /** A stored position the file has since outgrown must not overflow the bar. */
    @Test
    fun aPositionPastTheEndIsStillFull() {
        val progress = ReadingProgress(pageIndex = 900, pageCount = 300)

        assertEquals(1f, progress.fraction)
        assertEquals(100, progress.percent)
    }

    /** Nothing to measure against, so there is nothing honest to draw. */
    @Test
    fun aDocumentWithNoLengthHasNoFraction() {
        val progress = ReadingProgress(pageIndex = 4, pageCount = 0)

        assertNull(progress.fraction)
        assertNull(progress.percent)
    }

    /** A single-page document is finished the moment it is opened. */
    @Test
    fun aSinglePageDocumentIsFullAtOnce() {
        val progress = ReadingProgress(pageIndex = 0, pageCount = 1)

        assertEquals(1f, progress.fraction)
        assertEquals(100, progress.percent)
    }
}
