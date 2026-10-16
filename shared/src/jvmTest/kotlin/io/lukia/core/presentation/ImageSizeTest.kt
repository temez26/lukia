package io.lukia.core.presentation

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The size read from a picture's header is the size its decode has. */
class ImageSizeTest {

    @Test
    fun theHeaderSaysWhatTheDecodeWouldForEachFormat() {
        for (format in listOf("jpg", "png", "gif")) {
            val bytes = picture(width = 300, height = 450, format = format)
            val decoded = bytes.decodeToImageBitmapOrNull()!!

            val read = bytes.imageSizeOrNull()

            assertEquals(decoded.width, read?.width, format)
            assertEquals(decoded.height, read?.height, format)
        }
    }

    @Test
    fun bytesThatAreNoPictureHaveNoSize() {
        assertNull("not a picture at all".encodeToByteArray().imageSizeOrNull())
        assertNull(ByteArray(0).imageSizeOrNull())
    }

    private fun picture(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }
}
