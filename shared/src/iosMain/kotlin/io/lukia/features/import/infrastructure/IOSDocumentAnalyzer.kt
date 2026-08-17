package io.lukia.features.import.infrastructure

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.posix.memcpy

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

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            runCatching {
                fileManager.contentsAtPath(path)?.toByteArray()
            }.getOrNull()
                ?.let { extractContents(type, it) }
                ?: DocumentContents.Empty
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = size,
            coverImage = contents.coverImage,
            metadata = contents.metadata
        )
    }

    private fun NSData.toByteArray(): ByteArray {
        val result = ByteArray(length.toInt())
        if (result.isNotEmpty()) {
            result.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
        return result
    }
}
