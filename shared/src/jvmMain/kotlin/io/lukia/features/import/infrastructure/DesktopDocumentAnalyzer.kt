package io.lukia.features.import.infrastructure

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentType
import io.lukia.features.import.domain.PickedDocument
import java.io.File

class DesktopDocumentAnalyzer : DocumentAnalyzer {

    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        val file = File(document.location)

        if (!file.exists()) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}"
            )
        }

        if (!file.isFile) {
            throw IllegalArgumentException(
                "Path is not a file: ${document.location}"
            )
        }

        val type = when (document.extension.lowercase()) {
            "pdf" -> DocumentType.PDF
            "epub" -> DocumentType.EPUB
            else -> DocumentType.UNKNOWN
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = file.length()
        )
    }
}
