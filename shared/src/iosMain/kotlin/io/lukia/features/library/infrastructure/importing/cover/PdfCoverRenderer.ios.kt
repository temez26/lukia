package io.lukia.features.library.infrastructure.importing.cover

import io.lukia.core.infrastructure.toByteArray
import io.lukia.core.infrastructure.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGSizeMake
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation

@OptIn(ExperimentalForeignApi::class)
internal actual fun renderPdfCover(pdfBytes: ByteArray): ByteArray? {
    // PDFKit has no nullable way in: handed something that is not a PDF, its
    // initialiser returns nothing and the failure surfaces as a broken
    // reference rather than an empty one. Caught here so that this keeps the
    // promise the other platforms keep, which is that an unreadable file has no
    // cover rather than an exception.
    val document = runCatching { PDFDocument(data = pdfBytes.toNSData()) }.getOrNull()
        ?: return null

    if (document.pageCount == 0uL) return null

    val page = document.pageAtIndex(0uL) ?: return null
    val targetSize = CGSizeMake(240.0, 320.0)
    val thumbnail = page.thumbnailOfSize(targetSize, kPDFDisplayBoxMediaBox)

    return UIImagePNGRepresentation(thumbnail)?.toByteArray()
}
