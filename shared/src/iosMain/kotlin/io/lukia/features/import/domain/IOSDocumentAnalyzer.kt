package io.lukia.features.import.domain

import platform.Foundation.*

class IOSDocumentAnalyzer : DocumentAnalyzer {

    override suspend fun analyze(
        document: PickedDocument
    ): DocumentInfo {

        val url = NSURL.URLWithString(document.location)
            ?: throw IllegalArgumentException("Invalid document URL")

        val fileManager = NSFileManager.defaultManager

        val exists = fileManager.fileExistsAtPath(url.path ?: "")

        if (!exists) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}"
            )
        }

        val attributes =
            fileManager.attributesOfItemAtPath(
                url.path!!,
                null
            )

        val size =
            (attributes?.get(NSFileSize) as? NSNumber)
                ?.longLongValue ?: 0L

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