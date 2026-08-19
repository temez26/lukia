package io.lukia.features.import.infrastructure.cover

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * NOTE: uses Apple's PDFKit via cinterop. Not compiled/verified on macOS -
 * please build and exercise PDF cover extraction on device/simulator before
 * relying on it.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun renderPdfCover(pdfBytes: ByteArray): ByteArray? {
    val document = PDFDocument(data = pdfBytes.toNSData())
    if (document.pageCount == 0uL) return null

    val page = document.pageAtIndex(0uL) ?: return null
    val targetSize = CGSizeMake(240.0, 320.0)
    val thumbnail = page.thumbnailOfSize(targetSize, kPDFDisplayBoxMediaBox)

    return UIImagePNGRepresentation(thumbnail)?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val result = ByteArray(length)
    if (length > 0) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length.toULong())
        }
    }
    return result
}
