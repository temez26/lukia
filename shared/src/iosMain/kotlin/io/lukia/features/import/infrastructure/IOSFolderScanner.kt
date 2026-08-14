package io.lukia.features.import.infrastructure

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.domain.isSupportedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IOSFolderScanner : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> {

        val url = NSURL.URLWithString(folder.location)
            ?: return emptyList()

        // The picker holds the security scope for this folder, so the
        // enumeration below succeeds without re-acquiring it here.
        val contents = NSFileManager.defaultManager.contentsOfDirectoryAtURL(
            url,
            null,
            NSDirectoryEnumerationSkipsHiddenFiles,
            null
        ).orEmpty()

        return contents
            .filterIsInstance<NSURL>()
            .mapNotNull { fileUrl ->

                val fileName = fileUrl.lastPathComponent
                    ?: return@mapNotNull null

                val extension = fileUrl.pathExtension ?: ""

                if (!isSupportedDocument(extension)) {
                    return@mapNotNull null
                }

                PickedDocument(
                    fileName = fileName,
                    extension = extension,
                    location = fileUrl.absoluteString ?: return@mapNotNull null
                )
            }
    }
}
