package io.lukia.core.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.IntSize

/**
 * These bytes as a picture, or nothing where they are not one.
 *
 * Every picture the app draws comes out of a file somebody else wrote - a plate
 * inside an EPUB, an image lifted off a PDF page, a cover stored at import - so
 * bytes that will not decode are ordinary, and each caller has something to draw
 * instead.
 *
 * This exists because `decodeToImageBitmap` fails differently per platform. Skia
 * raises on bytes it cannot read, so catching is the whole story on iOS and
 * desktop. Android's `BitmapFactory` returns null instead, and Kotlin's platform
 * types let it through into an `ImageBitmap` that answers no question asked of
 * it: the decode "succeeds" and the NullPointerException arrives wherever the
 * width is first read - while paginating a book with an undecodable plate, and
 * from the reader's page and every cover on the shelf.
 *
 * Asking for the size here makes the platforms one answer: a picture that cannot
 * say how big it is has not been decoded.
 */
internal fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? =
    runCatching { decodeToImageBitmap().takeIf { it.width > 0 && it.height > 0 } }.getOrNull()

/**
 * How big these bytes would be as a picture, read from the image's header
 * without decoding its pixels; null where they are not a picture this platform
 * reads.
 *
 * For a caller that wants the shape and not the image: laying a book out needs
 * each plate's height, and decoding a plate to measure it cost about 19 ms for a
 * 1600 by 2400 JPEG on the desktop where reading its header cost 0.1 ms - per
 * plate, every time a book was opened.
 *
 * The size the picture is drawn at, so a photograph its camera marked as turned
 * comes back turned, as [decodeToImageBitmapOrNull] would give it.
 */
internal expect fun ByteArray.imageSizeOrNull(): IntSize?
