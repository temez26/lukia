package io.lukia.features.library.infrastructure.importing

import io.lukia.core.infrastructure.writeFileBytes
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/**
 * [bytes] as a document on disk, opened the way the import opens one.
 *
 * A real file rather than a stand-in: the whole point of [IOSDocumentSource] is
 * that PDFKit is handed the file itself, and a fake that answered from memory
 * would test the one arrangement this no longer uses.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun <R> openedDocument(bytes: ByteArray, block: (IOSDocumentSource) -> R): R {
    val path = NSTemporaryDirectory() + NSUUID().UUIDString() + ".document"

    writeFileBytes(path, bytes)

    return try {
        val source = checkNotNull(IOSDocumentSource.open(NSURL.fileURLWithPath(path), bytes.size.toLong())) {
            "the document could not be opened"
        }

        source.use(block)
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
