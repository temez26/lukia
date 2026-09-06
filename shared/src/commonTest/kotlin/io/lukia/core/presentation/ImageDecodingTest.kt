package io.lukia.core.presentation

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * That bytes which are not a picture come back as nothing, on every platform.
 *
 * The point of the function under test is that the platforms disagree about how
 * to fail: Skia raises, Android returns a null bitmap wrapped in an `ImageBitmap`
 * that throws on the first question asked of it. Only one of those is caught by
 * catching around the call, which is why the reader crashed paginating a book
 * with an undecodable plate. So these run wherever the common tests run - the
 * JVM on CI, the simulator beside it - and the Android half is pinned by
 * `AndroidImageDecodingTest`, which needs a device to say anything at all.
 */
class ImageDecodingTest {

    @Test
    fun decodesAPicture() {
        val decoded = assertNotNull(onePixelPng().decodeToImageBitmapOrNull())

        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }

    @Test
    fun bytesThatAreNotAPictureAreNothing() {
        assertNull("not a picture, just some words".encodeToByteArray().decodeToImageBitmapOrNull())
    }

    @Test
    fun noBytesAreNothing() {
        assertNull(ByteArray(0).decodeToImageBitmapOrNull())
    }

    /**
     * A plate whose header promises a PNG and whose body stops halfway, which is
     * what a book truncated in transit actually holds - and the case that tells
     * a decoder guessing from the first eight bytes apart from one that is not.
     */
    @Test
    fun aPictureCutInHalfIsNothing() {
        assertNull(onePixelPng().copyOf(20).decodeToImageBitmapOrNull())
    }
}

/** The smallest PNG there is: one opaque pixel, header, one row and a checksum. */
private fun onePixelPng(): ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAE" +
        "hQGAhKmMIQAAAABJRU5ErkJggg==",
)
