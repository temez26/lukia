package io.lukia.core.presentation

import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin

/**
 * Skia's codec, which reads the header and stops. Its decode turns a picture
 * its camera marked as turned, so the size is turned to match.
 */
internal actual fun ByteArray.imageSizeOrNull(): IntSize? = runCatching {
    val data = Data.makeFromBytes(this)
    try {
        val codec = Codec.makeFromData(data)
        try {
            val turned = codec.encodedOrigin in QuarterTurns
            IntSize(
                width = if (turned) codec.height else codec.width,
                height = if (turned) codec.width else codec.height,
            )
        } finally {
            codec.close()
        }
    } finally {
        data.close()
    }
}.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }

/** The origins that put a picture on its side, so its width and height trade places. */
private val QuarterTurns = setOf(
    EncodedOrigin.LEFT_TOP,
    EncodedOrigin.RIGHT_TOP,
    EncodedOrigin.RIGHT_BOTTOM,
    EncodedOrigin.LEFT_BOTTOM,
)
