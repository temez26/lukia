package io.lukia.features.library.infrastructure.importing

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.domain.MaxFolderScanDepth
import io.lukia.features.library.domain.isSupportedDocument

/**
 * Only what a scan actually reads. `DocumentFile` would answer the same
 * questions, but it asks the provider once per file per question - a folder of
 * three hundred books costs nine hundred queries before a single one is
 * imported. One cursor per directory, with these three columns, is the whole
 * scan.
 */
private val ScanColumns = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
)

/**
 * Enumerates a tree URI granted by the folder picker, sub-folders included. The
 * child URIs are the same shape the file picker produces, so
 * [AndroidDocumentAnalyzer] can resolve them without any special handling.
 */
class AndroidFolderScanner(
    private val context: Context,
) : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> {
        val treeUri = Uri.parse(folder.location)

        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return emptyList()

        val documents = mutableListOf<PickedDocument>()

        // Breadth-first over an explicit queue rather than recursion: the depth
        // is what has to be tracked, and carrying it alongside each folder is
        // simpler than threading it through a call stack.
        var level = listOf(rootDocumentId)
        var depth = 0

        while (level.isNotEmpty() && depth <= MaxFolderScanDepth) {
            val next = mutableListOf<String>()

            level.forEach { documentId ->
                readChildren(treeUri, documentId) { childId, name, mimeType ->
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        next += childId
                    } else {
                        val extension = name.substringAfterLast('.', "")

                        if (isSupportedDocument(extension)) {
                            documents += PickedDocument(
                                fileName = name,
                                extension = extension,
                                location = DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, childId)
                                    .toString(),
                            )
                        }
                    }
                }
            }

            level = next
            depth++
        }

        return documents
    }

    /**
     * Hands every child of [documentId] to [onChild]. A provider that refuses
     * the query yields nothing rather than throwing: one unreadable sub-folder
     * should cost its own contents, not the whole import.
     */
    private inline fun readChildren(
        treeUri: Uri,
        documentId: String,
        onChild: (id: String, name: String, mimeType: String?) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId,
        )

        val cursor: Cursor? = runCatching {
            context.contentResolver.query(childrenUri, ScanColumns, null, null, null)
        }.getOrNull()

        cursor?.use {
            while (it.moveToNext()) {
                val childId = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue

                onChild(childId, name, it.getString(2))
            }
        }
    }
}
