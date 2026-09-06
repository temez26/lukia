package io.lukia.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * That a picture which is a picture decodes, on the desktop.
 *
 * The other half of [ImageDecodingTest], and per platform for the reason given
 * there: the common tests also run against the Android target on the host, whose
 * decoder is a stub. Same bytes on all three, so a disagreement points at the
 * platform rather than at the picture.
 */
class DesktopImageDecodingTest {

    @Test
    fun decodesAPicture() {
        val decoded = assertNotNull(onePixelPng().decodeToImageBitmapOrNull())

        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }
}
