package io.lukia.core.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

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
