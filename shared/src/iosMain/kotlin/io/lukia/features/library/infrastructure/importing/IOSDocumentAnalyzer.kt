package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.toByteArray
import io.lukia.features.library.domain.DocumentAnalyzer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IOSDocumentAnalyzer(
    private val diagnostics: Diagnostics = Diagnostics.None,
) : DocumentAnalyzer {

    override suspend fun analyze(
        document: PickedDocument,
    ): DocumentInfo {
        val url = NSURL.URLWithString(document.location)
            ?: throw IllegalArgumentException(
                "Invalid document URL: ${document.location}",
            )

        val fileManager = NSFileManager.defaultManager
        val path = url.path
            ?: throw IllegalArgumentException(
                "Document URL has no path: ${document.location}",
            )

        val exists = fileManager.fileExistsAtPath(path)

        if (!exists) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}",
            )
        }

        val attributes = fileManager.attributesOfItemAtPath(
            path,
            null,
        )

        if (attributes?.get(NSFileType) == NSFileTypeDirectory) {
            throw IllegalArgumentException(
                "Path is not a file: ${document.location}",
            )
        }

        val size = (attributes?.get(NSFileSize) as? NSNumber)
            ?.longLongValue
            ?: 0L

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            runCatching {
                fileManager.contentsAtPath(path)?.toByteArray()
            }.getOrNull()
                ?.let { extractContents(type, it, diagnostics) }
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
}
