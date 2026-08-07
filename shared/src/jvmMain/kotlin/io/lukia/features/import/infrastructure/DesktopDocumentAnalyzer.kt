package io.lukia.features.import.domain

import java.io.File

class DesktopDocumentAnalyzer : DocumentAnalyzer {

    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        val file = File(document.location)

        if (!file.exists()) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}"
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