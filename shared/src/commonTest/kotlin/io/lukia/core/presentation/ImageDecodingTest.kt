package io.lukia.core.presentation

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * That bytes which are not a picture come back as nothing, on every platform.
 *
 * The function under test exists because the platforms disagree about how to
 * fail: Skia raises, Android returns a null bitmap wrapped in an `ImageBitmap`
 * that throws on the first question asked of it. Catching around the call
 * catches only one, which is why the reader crashed paginating a book with an
 * undecodable plate. So these run wherever the common tests run, and the Android
 * half is pinned by `AndroidImageDecodingTest`, which needs a device.
 *
 * That a real picture decodes cannot be here: these also run against the Android
 * target on the host, where `BitmapFactory` is a stub that decodes nothing. Each
 * platform pins the decoding half where a real decoder answers -
 * `DesktopImageDecodingTest`, `IOSImageDecodingTest`, `AndroidImageDecodingTest`
 * - and what is common here is the failing half the reader crashed on.
 */
class ImageDecodingTest {

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

/**
 * The smallest PNG there is: one opaque pixel, header, one row and a checksum.
 *
 * Internal rather than private, because the three platform tests that decode it
 * for real are decoding the same picture, and a second copy of these bytes is a
 * second thing to keep right.
 */
internal fun onePixelPng(): ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAE" +
        "hQGAhKmMIQAAAABJRU5ErkJggg==",
)
