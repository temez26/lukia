package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.importing.DocumentContents
import io.lukia.core.infrastructure.importing.DocumentSource
import io.lukia.core.infrastructure.importing.JvmDocumentSource
import io.lukia.core.infrastructure.importing.extractContents
import io.lukia.core.infrastructure.sha256Hex
import io.lukia.features.library.domain.ContentHasher
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.infrastructure.BookFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopDocumentAnalyzer(
    private val diagnostics: Diagnostics = Diagnostics.None,
) : DocumentAnalyzer, ContentHasher, BookFileReader {

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

    override suspend fun open(document: PickedDocument): DocumentSource? = openedSource(File(document.location))

    /** Read through the same source the import opens, a window at a time - see [sha256Hex]. */
    override suspend fun contentHash(document: PickedDocument): String? = withContext(Dispatchers.IO) {
        open(document)?.use { source -> runCatching { source.sha256Hex() }.getOrNull() }
    }

    private suspend fun openedSource(file: File): JvmDocumentSource? = withContext(Dispatchers.IO) {
        runCatching { JvmDocumentSource(file) }.getOrNull()
    }
}
