package io.lukia.core.infrastructure

import android.content.Context
import androidx.core.content.FileProvider
import io.lukia.core.domain.document.IncomingDocument
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.document.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies land in app-private internal storage and are then addressed through the
 * app's own [FileProvider].
 *
 * The provider is the point of this, and not decoration. Everything downstream -
 * the analyser reading the file's size, the workspace making a working copy of
 * it - reaches a document through `ContentResolver`, because that is what a
 * picked `content://` URI requires. A bare path would need both of them taught a
 * second way to open a document; a URI from a provider the app owns needs no
 * grant, cannot be revoked, and arrives at those two as the same kind of thing
 * the picker already hands them.
 */
class AndroidIncomingDocumentStore(
    private val context: Context,
) : IncomingDocumentStore {

    private val directory: File
        get() = File(context.filesDir, INCOMING_DIRECTORY)

    override suspend fun keep(document: IncomingDocument): PickedDocument =
        withContext(Dispatchers.IO) {
            val bytes = document.read()
            requireNotNull(bytes) { "Could not read ${document.fileName}" }

            val target = File(
                directory,
                IncomingDocumentNaming.fileName(bytes, document.extension),
            )

            directory.mkdirs()
            target.write(bytes)

            val uri = FileProvider.getUriForFile(context, authority(), target)

            PickedDocument(
                // The sender's name, not the one on disk: the shelf should show
                // the book the reader shared, not a hash of it.
                fileName = document.fileName,
                extension = document.extension,
                location = uri.toString(),
            )
        }

    /**
     * Built from the package name rather than written out, so the debug build's
     * suffixed id resolves to its own provider instead of the release one's.
     */
    private fun authority(): String = "${context.packageName}$AUTHORITY_SUFFIX"

    /**
     * Writes through a scratch file and renames it into place, so a write cut
     * short by the process dying never leaves a half-written file under the name
     * the library is about to store.
     */
    private fun File.write(bytes: ByteArray) {
        val partial = File(parentFile, "$name$PARTIAL_SUFFIX")

        try {
            partial.writeBytes(bytes)
            delete()

            check(partial.renameTo(this)) { "Could not store a copy of $name" }
        } finally {
            partial.delete()
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}

internal const val INCOMING_DIRECTORY = "incoming"

/**
 * Matches the `android:authorities` of the provider declared in the app's
 * manifest. Kept here beside its only user so the two are read together.
 */
internal const val AUTHORITY_SUFFIX = ".documents"
