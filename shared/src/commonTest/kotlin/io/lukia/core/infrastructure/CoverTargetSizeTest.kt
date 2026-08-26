package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverTargetSizeTest {

    @Test
    fun artworkLargerThanTheLimitIsScaledToIt() {
        val target = coverTargetSize(width = 1600, height = 2400, maxEdge = 800)

        assertEquals(CoverSize(width = 533, height = 800), target)
    }

    @Test
    fun theLongerEdgeIsWhatIsHeldToTheLimit() {
        val target = coverTargetSize(width = 2400, height = 1200, maxEdge = 800)

        assertEquals(CoverSize(width = 800, height = 400), target)
    }

    /** Nothing to do is said as null, so the original bytes are kept as they are. */
    @Test
    fun artworkAlreadySmallEnoughIsLeftAlone() {
        assertNull(coverTargetSize(width = 400, height = 600, maxEdge = 800))
        assertNull(coverTargetSize(width = 800, height = 800, maxEdge = 800))
    }

    @Test
    fun artworkOfNoSizeAtAllIsNotSomethingToScale() {
        assertNull(coverTargetSize(width = 0, height = 0, maxEdge = 800))
    }

    /** A pixel is the smallest a side can be, however long and thin the artwork. */
    @Test
    fun aSideThatWouldRoundToNothingKeepsOnePixel() {
        val target = coverTargetSize(width = 4000, height = 3, maxEdge = 800)

        assertEquals(CoverSize(width = 800, height = 1), target)
    }
}
