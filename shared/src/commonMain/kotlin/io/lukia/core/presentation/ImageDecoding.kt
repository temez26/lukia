package io.lukia.core.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/**
 * These bytes as a picture, or nothing where they are not one.
 *
 * Every picture the app draws comes out of a file somebody else wrote - a plate
 * inside an EPUB, an image lifted off a PDF page, a cover stored at import - so
 * bytes that will not decode are an ordinary Tuesday rather than an exceptional
 * one, and each caller already has something to draw instead.
 *
 * What this exists for is that `decodeToImageBitmap` fails differently on
 * different platforms. Skia raises on bytes it cannot read, so on iOS and
 * desktop catching around the call is the whole story. Android's `BitmapFactory`
 * returns null instead, and Kotlin's platform types let that null straight
 * through into an `ImageBitmap` that answers no question ever asked of it: the
 * decode "succeeds", and the NullPointerException arrives later, wherever the
 * width is first read. That was a crash while paginating a book with an
 * undecodable plate in it, and the same hollow bitmap was reachable from the
 * reader's page and from every cover on the shelf.
 *
 * Asking for the size here is what makes the two platforms one answer: a picture
 * that cannot say how big it is has not been decoded, whichever way its platform
 * chose to say so.
 */
internal fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? =
    runCatching { decodeToImageBitmap().takeIf { it.width > 0 && it.height > 0 } }.getOrNull()
