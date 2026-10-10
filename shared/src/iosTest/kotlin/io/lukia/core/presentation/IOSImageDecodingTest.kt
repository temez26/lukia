package io.lukia.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * That a picture which is a picture decodes, on iOS.
 *
 * The other half of [ImageDecodingTest], and per platform for the reason given
 * there. Same bytes as the desktop and the device tests, so a disagreement
 * points at the platform rather than at the picture.
 */
class IOSImageDecodingTest {

    @Test
    fun decodesAPicture() {
        val decoded = assertNotNull(onePixelPng().decodeToImageBitmapOrNull())

        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }
}
