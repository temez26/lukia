package io.lukia.core.presentation

import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The platform behaviour [decodeToImageBitmapOrNull] exists to cover, pinned
 * against the platform itself.
 *
 * Android's `BitmapFactory` returns null for bytes it cannot read, and Compose's
 * decoder hands that null straight into an `ImageBitmap` rather than raising -
 * so the decode appears to succeed and the NullPointerException arrives at
 * whichever line first asks how wide the picture is. That was three lines apart
 * in the paginator, a composition away in the reader's page, and a cache away on
 * the library shelf.
 *
 * The first test is what would have caught the crash. The second is why it is a
 * device test and not a common one: it asserts the platform's own behaviour, so
 * a Compose release that starts raising here instead turns it red, and the
 * comment above is the thing to correct.
 */
class AndroidImageDecodingTest {

    /**
     * The half of [io.lukia.core.presentation.ImageDecodingTest] that cannot be
     * common: a real decoder is needed to say that a picture is one, and on this
     * platform a real decoder means a device.
     */
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
    fun theDecoderItselfHandsBackAPictureThatCannotBeMeasured() {
        val hollow = "not a picture, just some words".encodeToByteArray().decodeToImageBitmap()

        val measured = runCatching { hollow.width }

        assertNull(measured.getOrNull(), "Compose now raises on undecodable bytes on Android too")
    }
}
