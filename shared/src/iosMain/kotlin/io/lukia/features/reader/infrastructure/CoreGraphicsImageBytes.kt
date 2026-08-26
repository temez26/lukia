package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceGetNumberOfComponents
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRef
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageCreateWithMask
import platform.CoreGraphics.CGImageMaskCreate
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPDFContentStreamRef
import platform.CoreGraphics.CGPDFDataFormat
import platform.CoreGraphics.CGPDFDictionaryRef
import platform.CoreGraphics.CGPDFStreamCopyData
import platform.CoreGraphics.CGPDFStreamGetDictionary
import platform.CoreGraphics.CGPDFStreamRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithDataProvider
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

/**
 * One picture out of a PDF, as bytes the reader can decode.
 *
 * Two shapes of stored image, and CoreGraphics tells them apart when the stream
 * is copied. A photograph is normally kept as the JPEG it already was, which
 * ImageIO opens as it opens any file. Everything else - a diagram, a
 * screenshot, a scan - is kept as raw samples plus a description of how to read
 * them, which is a width, a depth and a colour space, and has to be assembled
 * into an image by hand.
 *
 * Transparency is stored beside the picture rather than in it: a PDF puts it in
 * a second image, the soft mask, whose grey levels are the first one's opacity.
 * That is why the mask is fetched separately and why a picture that has one is
 * written out as PNG - it is the only one of the two formats with anywhere to
 * put it.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun coreGraphicsImageBytes(
    stream: CGPDFStreamRef,
    dictionary: CGPDFDictionaryRef,
    content: CGPDFContentStreamRef,
): ByteArray? {
    val picture = stream.decodedImage(dictionary, content) ?: return null
    val mask = dictionary.softMask()
    val masked = mask?.let { CGImageCreateWithMask(picture, it) }

    CGImageRelease(mask)

    val bytes = (masked ?: picture).encodedForReader(transparent = masked != null)

    CGImageRelease(masked)
    CGImageRelease(picture)

    return bytes
}

@OptIn(ExperimentalForeignApi::class)
private fun CGPDFStreamRef.decodedImage(
    dictionary: CGPDFDictionaryRef,
    content: CGPDFContentStreamRef,
): CGImageRef? = memScoped {
    val format = alloc<CGPDFDataFormat.Var>()
    val data = CGPDFStreamCopyData(this@decodedImage, format.ptr) ?: return null
    val provider = CGDataProviderCreateWithCFData(data)

    // The provider holds the data for as long as it is alive.
    CFRelease(data)

    if (provider == null) return null

    val image = if (format.value == CGPDFDataFormat.CGPDFDataFormatRaw) {
        provider.sampledImage(dictionary, content)
    } else {
        provider.compressedImage()
    }

    CGDataProviderRelease(provider)

    image
}

/**
 * A stream CoreGraphics could not unfilter into samples, which means it is a
 * file in its own right - JPEG, or the JPEG 2000 some scanners produce.
 * ImageIO reads either without being told which it is holding.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CGDataProviderRef.compressedImage(): CGImageRef? {
    val source = CGImageSourceCreateWithDataProvider(this, null) ?: return null
    val image = CGImageSourceCreateImageAtIndex(source, 0u, null)

    CFRelease(source)

    return image
}

/**
 * Raw samples assembled into an image using the file's own description of them.
 *
 * No alpha: a PDF image never carries any in its own samples, which is what the
 * soft mask exists for. CoreGraphics refuses the combinations it cannot draw -
 * a depth of four bits in colour, say - by returning nothing, which is the same
 * answer this gives for a colour space it could not follow, and reaches the
 * reader as a picture that is quietly not there.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CGDataProviderRef.sampledImage(
    dictionary: CGPDFDictionaryRef,
    content: CGPDFContentStreamRef,
): CGImageRef? {
    val width = dictionary.pdfInteger("Width")?.toULong() ?: return null
    val height = dictionary.pdfInteger("Height")?.toULong() ?: return null
    val depth = dictionary.pdfInteger("BitsPerComponent")?.toULong() ?: return null
    val space = dictionary.pdfObject("ColorSpace")?.let { pdfColourSpace(it, content) } ?: return null

    val bitsPerPixel = depth * CGColorSpaceGetNumberOfComponents(space)

    val image = CGImageCreate(
        width = width,
        height = height,
        bitsPerComponent = depth,
        bitsPerPixel = bitsPerPixel,
        bytesPerRow = (width * bitsPerPixel + BITS_PER_BYTE - 1u) / BITS_PER_BYTE,
        space = space,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value.toUInt(),
        provider = this,
        decode = null,
        shouldInterpolate = true,
        intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
    )

    CGColorSpaceRelease(space)

    return image
}

/**
 * The picture's transparency, as a mask CoreGraphics can apply.
 *
 * Inverted on the way through: a PDF's soft mask is opacity, where zero is
 * see-through, and a CoreGraphics mask is coverage, where zero is painted. The
 * decode array is what turns one into the other without touching the samples.
 *
 * Only a mask stored as samples is used. One stored as a JPEG would have to be
 * decoded and inverted a pixel at a time, and a soft mask is a greyscale ramp
 * that nobody stores lossily - so the rare file that does gets its picture
 * without transparency rather than a pass over every pixel of it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CGPDFDictionaryRef.softMask(): CGImageRef? = memScoped {
    val stream = pdfStream("SMask") ?: return null
    val dictionary = CGPDFStreamGetDictionary(stream) ?: return null

    val width = dictionary.pdfInteger("Width")?.toULong() ?: return null
    val height = dictionary.pdfInteger("Height")?.toULong() ?: return null
    val depth = dictionary.pdfInteger("BitsPerComponent")?.toULong() ?: return null

    val format = alloc<CGPDFDataFormat.Var>()
    val data = CGPDFStreamCopyData(stream, format.ptr) ?: return null
    val provider = if (format.value == CGPDFDataFormat.CGPDFDataFormatRaw) {
        CGDataProviderCreateWithCFData(data)
    } else {
        null
    }

    CFRelease(data)

    if (provider == null) return null

    val mask = CGImageMaskCreate(
        width = width,
        height = height,
        bitsPerComponent = depth,
        bitsPerPixel = depth,
        bytesPerRow = (width * depth + BITS_PER_BYTE - 1u) / BITS_PER_BYTE,
        provider = provider,
        decode = cValuesOf(1.0, 0.0),
        shouldInterpolate = true,
    )

    CGDataProviderRelease(provider)

    mask
}

/**
 * The picture shrunk to something a screen can use and written out.
 *
 * See the desktop's own encoder for the trade between the two formats and for
 * why a book's plates are not kept at the size they were printed at.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CGImageRef.encodedForReader(transparent: Boolean): ByteArray? {
    // Made at a scale of one, so that the image's size in points is its size in
    // pixels and the shrinking below is measuring what it thinks it is.
    val image = UIImage.imageWithCGImage(this)
    val shrunk = image.shrunkToFit(opaque = !transparent) ?: return null

    val data = if (transparent) {
        UIImagePNGRepresentation(shrunk)
    } else {
        UIImageJPEGRepresentation(shrunk, JPEG_QUALITY)
    }

    return data?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.shrunkToFit(opaque: Boolean): UIImage? {
    val longest = size.useContents { maxOf(width, height) }
    if (longest <= MAX_FIGURE_PIXELS) return this

    val scale = MAX_FIGURE_PIXELS / longest
    val width = size.useContents { width * scale }
    val height = size.useContents { height * scale }

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), opaque, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, width, height))
    val shrunk = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return shrunk
}

private const val BITS_PER_BYTE = 8uL

/** See the desktop's own constant: no screen draws a plate larger. */
private const val MAX_FIGURE_PIXELS = 1600.0

/** Enough to keep a diagram's lines clean without storing a photograph twice. */
private const val JPEG_QUALITY = 0.82
