package io.lukia.features.library.infrastructure.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.domain.DocumentAnalyzer

class AndroidDocumentAnalyzer(
    private val context: Context,
    private val diagnostics: Diagnostics = Diagnostics.None,
) : DocumentAnalyzer {

    override suspend fun exists(document: PickedDocument): Boolean =
        sizeOf(Uri.parse(document.location)) != null

    override suspend fun analyze(
        document: PickedDocument,
    ): DocumentInfo {
        val uri = Uri.parse(document.location)

        val size = sizeOf(uri)
        require(size != null) { "File does not exist: ${document.location}" }

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
                ?.let { extractContents(type, it, diagnostics) }
                ?: DocumentContents.Empty
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = true,
            sizeBytes = size,
            coverImage = contents.coverImage,
            metadata = contents.metadata,
        )
    }

    /**
     * The document's size, or null when there is no document there any more.
     *
     * A row coming back at all is what says the file exists - a provider that
     * has lost it answers with an empty cursor - so the two questions are one
     * query. A row without a readable size is still a document; it reads as
     * zero bytes, which is what the shelf showed before this was asked for.
     */
    private fun sizeOf(uri: Uri): Long? {
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        }.getOrNull() ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return@use null

            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

            if (sizeIndex >= 0 && !it.isNull(sizeIndex)) it.getLong(sizeIndex) else 0L
        }
    }
}
