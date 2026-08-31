package io.lukia.features.reader.infrastructure.pdf

import io.lukia.features.reader.domain.page.PageSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Turning the matrix a PDF draws a picture with into a rectangle on the page as
 * it is read.
 *
 * Two conversions at once, and both are easy to get subtly wrong: PDF measures
 * upwards from the bottom left and everything above this measures downwards from
 * the top left, and a page may be stored a quarter-turn from the way it is
 * shown. Getting either wrong puts a picture somewhere else in the book - which
 * is not a crash, and so is exactly the kind of thing only a test finds.
 */
class PdfImagePlacementTest {

    private val page = PageSize(width = 600f, height = 800f)

    @Test
    fun aPictureNearTheTopOfThePageComesBackNearTheTop() {
        // Drawn 700 points up a page 800 tall, which is near its head.
        val bounds = bounds(x = 100f, y = 700f, width = 200f, height = 50f)

        assertEquals(100f, bounds.left)
        assertEquals(50f, bounds.top)
        assertEquals(300f, bounds.right)
        assertEquals(100f, bounds.bottom)
    }

    @Test
    fun aPictureAtTheFootOfThePageComesBackAtTheFoot() {
        val bounds = bounds(x = 0f, y = 0f, width = 600f, height = 100f)

        assertEquals(700f, bounds.top)
        assertEquals(800f, bounds.bottom)
    }

    /**
     * A matrix may scale negatively, which draws the image flipped and puts its
     * corner at the far end. The part of the page it covers is the same.
     */
    @Test
    fun aFlippedPictureCoversTheSamePartOfThePage() {
        val upright = bounds(x = 100f, y = 400f, width = 200f, height = 150f)
        val flipped = bounds(x = 300f, y = 550f, width = -200f, height = -150f)

        assertEquals(upright, flipped)
    }

    /**
     * Turned a quarter clockwise, the page's bottom edge is shown as its left
     * edge - so how far along the bottom a picture sat is how far down the page
     * it now is.
     */
    @Test
    fun aQuarterTurnedPageIsMeasuredTheWayItIsShown() {
        val bounds = bounds(x = 0f, y = 0f, width = 100f, height = 200f, rotation = 90)

        assertEquals(0f, bounds.left)
        assertEquals(0f, bounds.top)
        assertEquals(200f, bounds.right)
        assertEquals(100f, bounds.bottom)
    }

    @Test
    fun anUpsideDownPageIsMeasuredTheWayItIsShown() {
        val bounds = bounds(x = 0f, y = 0f, width = 100f, height = 200f, rotation = 180)

        assertEquals(500f, bounds.left)
        assertEquals(0f, bounds.top)
        assertEquals(600f, bounds.right)
        assertEquals(200f, bounds.bottom)
    }

    @Test
    fun aThreeQuarterTurnedPageIsMeasuredTheWayItIsShown() {
        val bounds = bounds(x = 0f, y = 0f, width = 100f, height = 200f, rotation = 270)

        assertEquals(600f, bounds.left)
        assertEquals(500f, bounds.top)
        assertEquals(800f, bounds.right)
        assertEquals(600f, bounds.bottom)
    }

    /** Rules, borders and the single-pixel tints behind a table are not pictures. */
    @Test
    fun somethingTooSmallToBeAnIllustrationIsNotOne() {
        assertFalse(bounds(x = 50f, y = 50f, width = 500f, height = 2f).isWorthShowing())
        assertFalse(bounds(x = 50f, y = 50f, width = 8f, height = 8f).isWorthShowing())
        assertTrue(bounds(x = 50f, y = 50f, width = 200f, height = 150f).isWorthShowing())
    }

    private fun bounds(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Int = 0,
    ) = pdfImageBounds(
        drawn = PdfRect(x = x, y = y, width = width, height = height),
        page = page,
        rotation = rotation,
    )
}
