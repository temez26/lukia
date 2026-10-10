package io.lukia.core.infrastructure

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

/**
 * High enough that a cover drawn at a couple of hundred pixels looks no
 * different from the original, low enough that the artwork of a whole library
 * costs a few megabytes rather than a few hundred.
 */
private const val CoverJpegQuality = 85

internal actual fun shrinkCoverImage(coverBytes: ByteArray, maxEdge: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, bounds)

    val target = coverTargetSize(bounds.outWidth, bounds.outHeight, maxEdge) ?: return null

    // Halved on the way out of the decoder rather than after it: a publisher's
    // artwork at full size is tens of megabytes as a bitmap, and this never
    // allocates one. It only ever halves, so the exact size is reached by the
    // draw below.
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), maxEdge)
    }

    val decoded = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)
        ?: return null

    val scaled = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)

    Canvas(scaled).apply {
        drawColor(Color.WHITE)
        drawBitmap(
            decoded,
            null,
            Rect(0, 0, target.width, target.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }

    decoded.recycle()

    return ByteArrayOutputStream()
        .use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, CoverJpegQuality, output)
            output.toByteArray()
        }
        .also { scaled.recycle() }
}

/**
 * The largest power of two the decoder may subsample by while leaving the image
 * at least [maxEdge] across - so the draw that follows scales down rather than
 * back up.
 */
private fun sampleSizeFor(longestEdge: Int, maxEdge: Int): Int {
    var sampleSize = 1

    while (longestEdge / (sampleSize * 2) >= maxEdge) {
        sampleSize *= 2
    }

    return sampleSize
}
