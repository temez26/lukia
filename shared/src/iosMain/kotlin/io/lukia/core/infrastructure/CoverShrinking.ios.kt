package io.lukia.core.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

private const val CoverJpegQuality = 0.85

@OptIn(ExperimentalForeignApi::class)
internal actual fun shrinkCoverImage(coverBytes: ByteArray, maxEdge: Int): ByteArray? {
    // The factory rather than the constructor: bytes that are not a picture
    // make it hand back nothing, where the constructor has nothing to hand back
    // and fails instead.
    val image = UIImage.imageWithData(coverBytes.toNSData()) ?: return null

    // Points rather than pixels, which are the same thing for an image made
    // from data: nothing has told it to be drawn at any other scale.
    val target = image.size.useContents {
        coverTargetSize(width.toInt(), height.toInt(), maxEdge)
    } ?: return null

    val width = target.width.toDouble()
    val height = target.height.toDouble()

    // Opaque, at a scale of one: what is wanted is exactly the pixels asked for,
    // over white, because JPEG has nothing to record transparency in.
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return scaled?.let { UIImageJPEGRepresentation(it, CoverJpegQuality) }?.toByteArray()
}
