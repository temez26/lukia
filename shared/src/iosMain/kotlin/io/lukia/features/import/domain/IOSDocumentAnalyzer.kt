package io.lukia.features.import.domain

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IOSDocumentAnalyzer : DocumentAnalyzer {

    override suspend fun analyze(
        document: PickedDocument
    ): DocumentInfo {

        val url = NSURL.URLWithString(document.location)
            ?: throw IllegalArgumentException(
                "Invalid document URL: ${document.location}"
            )

        val fileManager = NSFileManager.defaultManager
        val path = url.path
            ?: throw IllegalArgumentException(
                "Document URL has no path: ${document.location}"
            )

        val exists = fileManager.fileExistsAtPath(path)

        if (!exists) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}"
            )
        }

        val attributes = fileManager.attributesOfItemAtPath(
            path,
            null
        )

        if (attributes?.get(NSFileType) == NSFileTypeDirectory) {
            throw IllegalArgumentException(
                "Path is not a file: ${document.location}"
            )
        }

        val size = (attributes?.get(NSFileSize) as? NSNumber)
            ?.longLongValue
            ?: 0L

        val type = when (document.extension.lowercase()) {
            "pdf" -> DocumentType.PDF
            "epub" -> DocumentType.EPUB
            else -> DocumentType.UNKNOWN
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = size
        )
    }
}
