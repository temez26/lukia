package io.lukia.features.import.domain

import io.lukia.features.import.domain.PickedDocument
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentAnalyzerImpl : DocumentAnalyzer {
    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        return withContext(Dispatchers.IO) {
            val file = File(document.absolutePath)
            val exists = file.exists()
            val size = if (exists) file.length() else 0L
            
            val type = when (document.extension.lowercase()) {
                "pdf" -> DocumentType.PDF
                "epub" -> DocumentType.EPUB
                else -> DocumentType.UNKNOWN
            }

            DocumentInfo(
                document = document,
                type = type,
                exists = exists,
                sizeBytes = size
            )
        }
    }
}
