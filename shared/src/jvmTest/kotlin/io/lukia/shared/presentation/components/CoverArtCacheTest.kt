package io.lukia.shared.presentation.components

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Covers are the most expensive thing the library draws: a stored one is around
 * 800x1100, several megabytes decoded, and a shelf holds hundreds of them.
 * What these tests are about is that scrolling back over a cover already seen
 * costs nothing, and that a library of any size costs a fixed amount of memory.
 */
class CoverArtCacheTest {

    /** A book, named the way the library names one: by where its file is. */
    private fun cover(seed: Int) = "/library/book-$seed.epub"

    /** A cover at the largest size any of them is kept at. */
    private suspend fun large(): ImageBitmap = ImageBitmap(MaxCoverEdge, MaxCoverEdge)

    private val coversThatFit = CoverPixelBudget / (MaxCoverEdge * MaxCoverEdge)

    private suspend fun fill(count: Int) {
        repeat(count) { CoverArtCache.artworkFor(cover(it), ::large) }
    }

    /** Whether the cache had to go back for [cover], which is what it exists to avoid. */
    private suspend fun reDecodes(cover: String): Boolean {
        var decoded = false

        CoverArtCache.artworkFor(cover) {
            decoded = true
            ImageBitmap(10, 10)
        }

        return decoded
    }

    @BeforeTest
    @AfterTest
    fun emptyTheCache() = runTest {
        CoverArtCache.clear()
    }

    @Test
    fun aCoverIsDecodedOnceAndHandedBackTheSameAfterwards() = runTest {
        var decodes = 0
        val decode: suspend () -> ImageBitmap? = {
            decodes++
            ImageBitmap(10, 10)
        }

        val first = CoverArtCache.artworkFor(cover(1), decode)
        val second = CoverArtCache.artworkFor(cover(1), decode)

        assertEquals(1, decodes)
        assertSame(first, second)
    }

    /** The same book asked about twice is the same artwork, whoever asks. */
    @Test
    fun theSameBookIsTheSameCoverHoweverManyCardsAskForIt() = runTest {
        val artwork = CoverArtCache.artworkFor(cover(1)) { ImageBitmap(10, 10) }

        assertSame(artwork, CoverArtCache.artworkFor(cover(1)) { ImageBitmap(20, 20) })
    }

    /**
     * A book read again off disk can have artwork its old entry knows nothing
     * about - the entry is filed under the book, not under the picture.
     */
    @Test
    fun aForgottenCoverIsDrawnAgainNextTimeItIsAskedFor() = runTest {
        CoverArtCache.artworkFor(cover(1), ::large)
        CoverArtCache.forget(cover(1))

        assertEquals(true, reDecodes(cover(1)))
    }

    /**
     * A cover that will not decode is, from the reader's side, a book without
     * one - so nothing is stored and the monogram stands in.
     */
    @Test
    fun artworkThatWillNotDecodeIsNotRemembered() = runTest {
        var decodes = 0

        repeat(2) {
            assertNull(
                CoverArtCache.artworkFor(cover(1)) {
                    decodes++
                    null
                },
            )
        }

        assertEquals(2, decodes)
    }

    /**
     * A whole library's worth of decoded artwork is hundreds of megabytes, and
     * the crash that follows is a worse answer than a stutter - so the oldest
     * goes to make room.
     */
    @Test
    fun theLeastRecentlyUsedCoverIsDroppedOnceTheBudgetIsSpent() = runTest {
        fill(count = coversThatFit + 1)

        assertEquals(true, reDecodes(cover(0)))
    }

    /** Reading a cover again is what keeps it: the oldest is the least used. */
    @Test
    fun aCoverComeBackToOutlivesOneNobodyHasLookedAtSince() = runTest {
        fill(count = coversThatFit)

        // Looking at the first again puts the second at the far end of the queue.
        CoverArtCache.artworkFor(cover(0), ::large)
        CoverArtCache.artworkFor(cover(coversThatFit), ::large)

        assertEquals(false, reDecodes(cover(0)))
        assertEquals(true, reDecodes(cover(1)))
    }

    /**
     * Artwork is kept at the size it is drawn at rather than the size it was
     * stored at. Everything else here rests on that: at full size a screenful of
     * covers would spend the whole budget several times over.
     */
    @Test
    fun artworkIsKeptNoLargerThanItIsEverDrawn() = runTest {
        val artwork = CoverArtCache.artworkFor(cover(1)) { ImageBitmap(1_600, 2_200) }

        assertEquals(MaxCoverEdge, artwork?.artwork?.height)
        assertEquals(581, artwork?.artwork?.width)
    }

    @Test
    fun aCoverSmallEnoughAlreadyIsLeftExactlyAsItIs() = runTest {
        val decoded = ImageBitmap(400, 550)

        assertSame(decoded, CoverArtCache.artworkFor(cover(1)) { decoded }?.artwork)
    }
}
