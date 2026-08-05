package io.lukia.features.import.domain

import java.io.File

class DesktopDocumentAnalyzer : DocumentAnalyzer {
    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        val file = File(document.absolutePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: ${document.absolutePath}")
        }
        
        return DocumentInfo(
            fileName = document.fileName,
            extension = document.extension,
            absolutePath = document.absolutePath,
            fileSizeBytes = file.length()
        )
    }
}
