package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.importing.DocumentContents
import io.lukia.core.infrastructure.importing.extractContents
import io.lukia.features.library.domain.DocumentAnalyzer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IOSDocumentAnalyzer(
    private val diagnostics: Diagnostics = Diagnostics.None,
) : DocumentAnalyzer {

    override suspend fun exists(document: PickedDocument): Boolean {
        val path = NSURL.URLWithString(document.location)?.path ?: return false

        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    override suspend fun analyze(
        document: PickedDocument,
    ): DocumentInfo {
        val url = requireNotNull(NSURL.URLWithString(document.location)) {
            "Invalid document URL: ${document.location}"
        }

        val fileManager = NSFileManager.defaultManager
        val path = requireNotNull(url.path) {
            "Document URL has no path: ${document.location}"
        }

        require(fileManager.fileExistsAtPath(path)) {
            "File does not exist: ${document.location}"
        }

        val attributes = fileManager.attributesOfItemAtPath(
            path,
            null,
        )

        require(attributes?.get(NSFileType) != NSFileTypeDirectory) {
            "Path is not a file: ${document.location}"
        }

        val size = (attributes?.get(NSFileSize) as? NSNumber)
            ?.longLongValue
            ?: 0L

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            // Opened rather than read - see [DocumentSource]. The handle is
            // opened on the dispatcher that waits on disks and the extraction
            // runs on the one sized to the cores, which is where the import
            // already is: what the extraction does is inflate and render, and
            // the reads it makes through the handle are kilobytes.
            openedSource(url, size)?.use { extractContents(type, it, diagnostics) }
                ?: DocumentContents.Empty
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = size,
            coverImage = contents.coverImage,
            metadata = contents.metadata,
        )
    }

    private suspend fun openedSource(url: NSURL, size: Long): IOSDocumentSource? =
        withContext(Dispatchers.IO) { IOSDocumentSource.open(url, size) }
}
