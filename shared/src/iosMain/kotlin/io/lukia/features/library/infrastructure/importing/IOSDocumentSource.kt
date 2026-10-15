package io.lukia.features.library.infrastructure.importing

import io.lukia.core.infrastructure.importing.DocumentSource
import io.lukia.core.infrastructure.toByteArray
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation

/**
 * A document being imported, read through a handle on the file the picker
 * returned.
 *
 * See [DocumentSource]. Nothing here loads the file: `contentsAtPath` used to,
 * and on iOS a hundred-megabyte book read to find its title is a hundred
 * megabytes the system takes the process down over rather than pages out.
 *
 * Every read is wrapped in a pool of its own. The handle answers with an
 * autoreleased `NSData`, and a coroutine's worker thread has no run loop to
 * drain what that is added to - so without the pool a book read a window at a
 * time would still end up holding every window it had read.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IOSDocumentSource private constructor(
    private val url: NSURL,
    private val handle: NSFileHandle,
    override val size: Long,
) : DocumentSource {

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= size || length <= 0) return ByteArray(0)

        val count = minOf(length.toLong(), size - offset)

        return autoreleasepool {
            runCatching {
                handle.seekToFileOffset(offset.toULong())
                handle.readDataOfLength(count.toULong()).toByteArray()
            }.getOrDefault(ByteArray(0))
        }
    }

    /**
     * Opened from the URL rather than drawn through the handle above: PDFKit
     * reads a document by mapping the file itself, which is the whole point of
     * handing it a URL instead of the `NSData` this used to make.
     */
    override fun renderFirstPage(): ByteArray? = autoreleasepool {
        // PDFKit has no nullable way in: handed something that is not a PDF, its
        // initialiser returns nothing and the failure surfaces as a broken
        // reference rather than an empty one. Caught here so that this keeps the
        // promise the other platforms keep, which is that an unreadable file has
        // no cover rather than an exception.
        val document = runCatching { PDFDocument(uRL = url) }.getOrNull()
            ?: return@autoreleasepool null

        if (document.pageCount == 0uL) return@autoreleasepool null

        val page = document.pageAtIndex(0uL) ?: return@autoreleasepool null
        val thumbnail = page.thumbnailOfSize(ThumbnailSize, kPDFDisplayBoxMediaBox)

        UIImagePNGRepresentation(thumbnail)?.toByteArray()
    }

    override fun close() {
        runCatching { handle.closeFile() }
    }

    companion object {

        /** The size a cover is drawn at, before it is shrunk for storage. */
        private val ThumbnailSize = CGSizeMake(240.0, 320.0)

        /**
         * The document at [url], or null where it cannot be opened for reading -
         * a file that has gone away, or one outside the scope the picker granted.
         */
        fun open(url: NSURL, size: Long): IOSDocumentSource? = autoreleasepool {
            val handle = runCatching { NSFileHandle.fileHandleForReadingFromURL(url, null) }
                .getOrNull()

            handle?.let { IOSDocumentSource(url = url, handle = it, size = size) }
        }
    }
}
