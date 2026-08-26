package io.lukia.core.infrastructure

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun shrinkCoverImage(coverBytes: ByteArray, maxEdge: Int): ByteArray? {
    val image = runCatching { ImageIO.read(ByteArrayInputStream(coverBytes)) }.getOrNull()
        ?: return null

    val target = coverTargetSize(image.width, image.height, maxEdge) ?: return null

    // TYPE_INT_RGB rather than ARGB: what is written is JPEG, which has no
    // alpha channel to write one into.
    val scaled = BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_RGB)

    scaled.createGraphics().apply {
        setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        color = Color.WHITE
        fillRect(0, 0, target.width, target.height)
        drawImage(image, 0, 0, target.width, target.height, null)
        dispose()
    }

    return ByteArrayOutputStream().use { output ->
        if (ImageIO.write(scaled, "jpg", output)) output.toByteArray() else null
    }
}
