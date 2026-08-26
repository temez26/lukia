package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGColorSpaceCreateDeviceCMYK
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceCreateIndexed
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGPDFArrayRef
import platform.CoreGraphics.CGPDFContentStreamGetResource
import platform.CoreGraphics.CGPDFContentStreamRef
import platform.CoreGraphics.CGPDFObjectRef
import platform.CoreGraphics.CGPDFStreamCopyData
import platform.CoreGraphics.CGPDFStreamGetDictionary
import platform.CoreGraphics.CGPDFStreamRef

/**
 * The colour space a PDF image's samples are written in, as CoreGraphics needs
 * it to make sense of them.
 *
 * A PDF names its colour spaces in its own vocabulary, and half of them are
 * descriptions of a space rather than a space: an ICC profile with a component
 * count, a palette with a base space of its own, a name that stands for an
 * entry in the page's resources. CoreGraphics wants a `CGColorSpace`, so the
 * description has to be followed to whichever real space it ends at.
 *
 * What is not followed: `Separation`, `DeviceN` and `Lab`. Each of those is a
 * space plus a function that converts out of it, and running that function is a
 * colour engine rather than a lookup. An image in one of them is left behind
 * rather than shown in the wrong colours - and in a book they are separation
 * plates and spot inks, which is print machinery rather than illustration.
 *
 * The caller owns what comes back and releases it.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun pdfColourSpace(
    space: CGPDFObjectRef,
    content: CGPDFContentStreamRef,
    depth: Int = 0,
): CGColorSpaceRef? {
    if (depth > MAX_COLOUR_SPACE_DEPTH) return null

    space.asName()?.let { return namedColourSpace(it, content, depth) }
    space.asArray()?.let { return describedColourSpace(it, content, depth) }

    return null
}

/**
 * The device spaces every PDF may use without declaring them, and - for
 * anything else - the page's own list of the spaces it named.
 */
@OptIn(ExperimentalForeignApi::class)
private fun namedColourSpace(
    name: String,
    content: CGPDFContentStreamRef,
    depth: Int,
): CGColorSpaceRef? = when (name) {
    "DeviceGray", "CalGray", "G" -> CGColorSpaceCreateDeviceGray()
    "DeviceRGB", "CalRGB", "RGB" -> CGColorSpaceCreateDeviceRGB()
    "DeviceCMYK", "CMYK" -> CGColorSpaceCreateDeviceCMYK()
    else -> CGPDFContentStreamGetResource(content, "ColorSpace", name)
        ?.let { pdfColourSpace(it, content, depth + 1) }
}

@OptIn(ExperimentalForeignApi::class)
private fun describedColourSpace(
    description: CGPDFArrayRef,
    content: CGPDFContentStreamRef,
    depth: Int,
): CGColorSpaceRef? = when (description.pdfName(FAMILY)) {
    "ICCBased" -> description.pdfStream(ICC_PROFILE)?.let { iccColourSpace(it) }
    "Indexed", "I" -> indexedColourSpace(description, content, depth)
    "CalGray" -> CGColorSpaceCreateDeviceGray()
    "CalRGB" -> CGColorSpaceCreateDeviceRGB()
    else -> null
}

/**
 * An ICC profile taken at its component count rather than its contents.
 *
 * The profile says how to turn these numbers into colour exactly; the device
 * space of the same width says how to turn them into colour approximately, and
 * is what every viewer falls back to when a profile will not load. For a
 * picture being re-set into a page of text, the difference is a shade.
 */
@OptIn(ExperimentalForeignApi::class)
private fun iccColourSpace(profile: CGPDFStreamRef): CGColorSpaceRef? =
    when (CGPDFStreamGetDictionary(profile)?.pdfInteger("N")?.toInt()) {
        GRAY_COMPONENTS -> CGColorSpaceCreateDeviceGray()
        RGB_COMPONENTS -> CGColorSpaceCreateDeviceRGB()
        CMYK_COMPONENTS -> CGColorSpaceCreateDeviceCMYK()
        else -> null
    }

/**
 * A palette: one byte per pixel, standing for a colour in a table of them.
 *
 * The table is written either as a string or as a stream, and which one a file
 * uses is a matter of how large it is. CoreGraphics takes a copy of it, so the
 * bytes only have to live as long as the call.
 */
@OptIn(ExperimentalForeignApi::class)
private fun indexedColourSpace(
    description: CGPDFArrayRef,
    content: CGPDFContentStreamRef,
    depth: Int,
): CGColorSpaceRef? {
    val base = description.pdfObject(PALETTE_BASE)
        ?.let { pdfColourSpace(it, content, depth + 1) }
        ?: return null

    val lastIndex = description.pdfInteger(PALETTE_LAST_INDEX)
    val table = description.pdfString(PALETTE_TABLE)?.pdfBytes()
        ?: description.pdfStream(PALETTE_TABLE)?.rawBytes()

    val indexed = if (lastIndex == null || table == null) {
        null
    } else {
        memScoped {
            CGColorSpaceCreateIndexed(base, lastIndex.toULong(), allocArrayOf(table).reinterpret())
        }
    }

    CGColorSpaceRelease(base)

    return indexed
}

/** The bytes a stream holds once CoreGraphics has undone its filters. */
@OptIn(ExperimentalForeignApi::class)
private fun CGPDFStreamRef.rawBytes(): ByteArray? {
    val data = CGPDFStreamCopyData(this, null) ?: return null
    val bytes = data.toByteArray()

    CFRelease(data)

    return bytes
}

private const val MAX_COLOUR_SPACE_DEPTH = 4

private const val GRAY_COMPONENTS = 1
private const val RGB_COMPONENTS = 3
private const val CMYK_COMPONENTS = 4

/** Where each part of a colour space description sits in its array. */
private const val FAMILY = 0
private const val ICC_PROFILE = 1
private const val PALETTE_BASE = 1
private const val PALETTE_LAST_INDEX = 2
private const val PALETTE_TABLE = 3
