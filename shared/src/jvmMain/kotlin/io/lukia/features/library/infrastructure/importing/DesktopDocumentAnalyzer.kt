package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.domain.DocumentAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopDocumentAnalyzer(
    private val diagnostics: Diagnostics = Diagnostics.None,
) : DocumentAnalyzer {

    override suspend fun exists(document: PickedDocument): Boolean =
        withContext(Dispatchers.IO) { File(document.location).isFile }

    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        val file = File(document.location)

        require(file.exists()) { "File does not exist: ${document.location}" }
        require(file.isFile) { "Path is not a file: ${document.location}" }

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            // Opened rather than read - see [DocumentSource]. The handle is
            // opened on the dispatcher that waits on disks and the extraction
            // runs on the one sized to the cores, which is where the import
            // already is: what the extraction does is inflate and render, and
            // the reads it makes through the handle are kilobytes.
            openedSource(file)?.use { extractContents(type, it, diagnostics) }
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

    private suspend fun openedSource(file: File): JvmDocumentSource? = withContext(Dispatchers.IO) {
        runCatching { JvmDocumentSource(file) }.getOrNull()
    }
}
