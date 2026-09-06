package io.lukia.core.infrastructure

import io.lukia.core.domain.document.IncomingDocument
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.document.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Kept beside the working copies, under the same per-user directory everything
 * else the desktop build stores between runs lives in.
 *
 * The desktop has no share sheet to receive from. What it has instead are the
 * two ways a desktop is handed a document at all - dropped on the window, or
 * named on the command line by whatever opened it - and both arrive here through
 * `deliverIncomingDocuments`.
 *
 * Copied rather than pointed at, even though a desktop path is one the app could
 * keep. A book dragged out of a downloads folder is a book that will be tidied
 * out of it, and a shelf holding a path to a file the reader has since deleted
 * is a shelf that fails to open a book it is still showing.
 */
class JvmIncomingDocumentStore(
    /** Injectable so tests can work in a temporary directory. */
    private val directory: File = File(lukiaAppDirectory(), INCOMING_DIRECTORY),
) : IncomingDocumentStore {

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

            PickedDocument(
                // The sender's name, not the one on disk: the shelf should show
                // the book the reader shared, not a hash of it.
                fileName = document.fileName,
                extension = document.extension,
                location = target.absolutePath,
            )
        }

    /**
     * Writes through a scratch file and moves it into place, so a write cut
     * short by the process dying never leaves a half-written file under the name
     * the library is about to store.
     */
    private fun File.write(bytes: ByteArray) {
        val partial = File(parentFile, "$name$PARTIAL_SUFFIX")

        try {
            partial.writeBytes(bytes)
            Files.move(partial.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}

internal const val INCOMING_DIRECTORY = "incoming"
