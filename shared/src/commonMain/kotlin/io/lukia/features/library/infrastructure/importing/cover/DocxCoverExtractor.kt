package io.lukia.features.library.infrastructure.importing.cover

import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.archive.ZipArchiveReader

/**
 * The thumbnail Word stores when a document is saved with one.
 *
 * Only the declared thumbnail, never the first picture found in the file. A
 * word-processed document has no cover in the sense a book does, and putting
 * whatever figure happens to appear on page four onto the shelf as though it
 * were one is worse than the placeholder the library draws for a document with
 * no cover at all.
 */
internal fun extractDocxCover(docx: ByteSource): ByteArray? {
    val zip = ZipArchiveReader(docx)

    // The extension follows whatever the saving application produced. EMF and
    // WMF are Windows metafiles rather than images anything here can decode, so
    // they are left out.
    return zip.entryNames()
        .firstOrNull {
            it.startsWith("docProps/thumbnail.") &&
                (it.endsWith(".jpeg") || it.endsWith(".jpg") || it.endsWith(".png"))
        }
        ?.let(zip::readEntry)
}
