package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.domain.DocumentAnalyzer
import java.io.File

class DesktopDocumentAnalyzer : DocumentAnalyzer {

    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        val file = File(document.location)

        if (!file.exists()) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}",
            )
        }

        if (!file.isFile) {
            throw IllegalArgumentException(
                "Path is not a file: ${document.location}",
            )
        }

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            runCatching { file.readBytes() }
                .getOrNull()
                ?.let { extractContents(type, it) }
                ?: DocumentContents.Empty
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = file.length(),
            coverImage = contents.coverImage,
            metadata = contents.metadata,
        )
    }
}
