package io.lukia.features.library.infrastructure.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.importing.DocumentContents
import io.lukia.core.infrastructure.importing.DocumentSource
import io.lukia.core.infrastructure.importing.extractContents
import io.lukia.core.infrastructure.sha256Hex
import io.lukia.features.library.domain.ContentHasher
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.infrastructure.BookFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDocumentAnalyzer(
    private val context: Context,
    private val diagnostics: Diagnostics = Diagnostics.None,
) : DocumentAnalyzer, ContentHasher, BookFileReader {

    override suspend fun exists(document: PickedDocument): Boolean =
        withContext(Dispatchers.IO) { sizeOf(Uri.parse(document.location)) != null }

    override suspend fun analyze(
        document: PickedDocument,
    ): DocumentInfo {
        val uri = Uri.parse(document.location)

        val size = withContext(Dispatchers.IO) { sizeOf(uri) }
        require(size != null) { "File does not exist: ${document.location}" }

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            // Opened rather than read - see [DocumentSource]. Opening moves to
            // the dispatcher that waits on providers, because a document URI is
            // answered by whichever one owns it and that may be a cloud drive;
            // the extraction stays on the dispatcher sized to the cores, where
            // the import already is, because what it does is inflate and render.
            openedSource(uri)?.use { extractContents(type, it, diagnostics) }
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

    override suspend fun open(document: PickedDocument): DocumentSource? =
        withContext(Dispatchers.IO) { AndroidDocumentSource.open(context, Uri.parse(document.location)) }

    /** Read through the same source the import opens, a window at a time - see [sha256Hex]. */
    override suspend fun contentHash(document: PickedDocument): String? = withContext(Dispatchers.IO) {
        open(document)?.use { source -> runCatching { source.sha256Hex() }.getOrNull() }
    }

    private suspend fun openedSource(uri: Uri): AndroidDocumentSource? =
        withContext(Dispatchers.IO) { AndroidDocumentSource.open(context, uri) }

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
