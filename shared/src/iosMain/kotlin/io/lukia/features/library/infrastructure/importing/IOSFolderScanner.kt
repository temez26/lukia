package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.domain.MaxFolderScanDepth
import io.lukia.features.library.domain.isSupportedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSDirectoryEnumerationSkipsPackageDescendants
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IOSFolderScanner : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> {
        val url = NSURL.URLWithString(folder.location)
            ?: return emptyList()

        // The picker holds the security scope for this folder, so the
        // enumeration below succeeds without re-acquiring it here.
        //
        // Package descendants are skipped because a `.pages` or `.rtfd` bundle
        // is a directory pretending to be a file, and walking into one yields
        // its internals rather than a book.
        val enumerator = NSFileManager.defaultManager.enumeratorAtURL(
            url,
            null,
            NSDirectoryEnumerationSkipsHiddenFiles or
                NSDirectoryEnumerationSkipsPackageDescendants,
            null,
        ) ?: return emptyList()

        val documents = mutableListOf<PickedDocument>()

        while (true) {
            val fileUrl = enumerator.nextObject() as? NSURL ?: break

            // `level` is 1 for the folder's own contents, so this prunes the
            // first directory that would take the walk past the cap - and
            // pruning rather than filtering is what stops a deep tree being
            // walked in full only to have most of it thrown away.
            if (enumerator.level.toInt() > MaxFolderScanDepth) {
                enumerator.skipDescendants()
                continue
            }

            val fileName = fileUrl.lastPathComponent ?: continue
            val extension = fileUrl.pathExtension ?: ""

            if (!isSupportedDocument(extension)) {
                continue
            }

            val location = fileUrl.absoluteString ?: continue

            documents += PickedDocument(
                fileName = fileName,
                extension = extension,
                location = location,
            )
        }

        return documents
    }
}
