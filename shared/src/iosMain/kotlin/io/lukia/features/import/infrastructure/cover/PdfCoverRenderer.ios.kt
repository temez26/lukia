package io.lukia.features.import.infrastructure.cover

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGSizeMake
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation

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
