package io.lukia.features.import.infrastructure

import io.lukia.features.import.domain.CoverImage
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentType
import io.lukia.features.import.domain.PickedDocument
import io.lukia.features.import.infrastructure.cover.extractCover
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

        val type = when (document.extension.lowercase()) {
            "pdf" -> DocumentType.PDF
            "epub" -> DocumentType.EPUB
            else -> DocumentType.UNKNOWN
        }

        val coverBytes = if (type == DocumentType.UNKNOWN) {
            null
        } else {
            runCatching {
                fileManager.contentsAtPath(path)?.toByteArray()
            }.getOrNull()?.let { extractCover(type, it) }
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = size,
            coverImage = coverBytes?.let { CoverImage(it) }
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
