package io.lukia.features.library.infrastructure.importing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A storage provider holding a tree of folders and books, answering the one
 * query a folder scan makes: the children of a document id.
 *
 * A tree rather than a fake scanner, because the scan exists to walk somebody
 * else's provider - the depth cap, the sub-folders, the half-row a provider may
 * return - and a stand-in would answer all of that with whatever it was written
 * to believe.
 *
 * [tree] maps a folder's document id to its children, each a name paired with
 * whether it is itself a folder.
 */
class TestDocumentsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val parent = DocumentsContract.getDocumentId(uri)
        val children = tree[parent] ?: return if (parent in refusing) null else emptyCursor()

        return MatrixCursor(Columns).apply {
            children.forEach { child ->
                val id = if (parent.isEmpty()) child.name else "$parent/${child.name}"
                val mimeType = when {
                    child.isFolder -> DocumentsContract.Document.MIME_TYPE_DIR
                    else -> "application/octet-stream"
                }

                addRow(arrayOf(id, child.name.takeUnless { child.nameless }, mimeType))
            }
        }
    }

    private fun emptyCursor(): Cursor = MatrixCursor(Columns)

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    /** One thing inside a folder. [nameless] is the row a provider returns half of. */
    data class Child(val name: String, val isFolder: Boolean = false, val nameless: Boolean = false)

    companion object {
        const val AUTHORITY = "io.lukia.shared.test.storage"

        private val Columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        /** Set by a test before it scans. Read by whichever instance Robolectric built. */
        var tree: Map<String, List<Child>> = emptyMap()

        /** Document ids whose query fails, as an unreadable sub-folder really does. */
        var refusing: Set<String> = emptySet()

        fun reset() {
            tree = emptyMap()
            refusing = emptySet()
        }
    }
}
