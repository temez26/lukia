package io.lukia.core.presentation

import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntSize

/** `BitmapFactory` reading bounds only. It does not turn for a camera's marking, and nor does its decode. */
internal actual fun ByteArray.imageSizeOrNull(): IntSize? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    return IntSize(bounds.outWidth, bounds.outHeight).takeIf { it.width > 0 && it.height > 0 }
}
