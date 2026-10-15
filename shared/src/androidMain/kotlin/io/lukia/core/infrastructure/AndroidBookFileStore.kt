package io.lukia.core.infrastructure

import android.content.Context
import androidx.core.content.FileProvider
import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.BookFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * Downloaded books, beside the copies of books handed to the app and served by
 * the same `FileProvider` - see [AndroidIncomingDocumentStore] - so the
 * reader opens one exactly as it opens the other, and removing the book
 * deletes it the same way.
 */
class AndroidBookFileStore(private val context: Context) : BookFileStore {

    private val directory: File
        get() = File(context.filesDir, INCOMING_DIRECTORY)

    override fun locationFor(contentHash: String, extension: String): String =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", fileFor(contentHash, extension))
            .toString()

    override suspend fun open(contentHash: String, extension: String): BookFileWriter = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(contentHash, extension)
        val partial = File(directory, "${target.name}$PARTIAL_SUFFIX")

        AndroidFileBookWriter(partial.outputStream(), partial, target)
    }

    private fun fileFor(contentHash: String, extension: String) =
        File(directory, IncomingDocumentNaming.serverBookName(contentHash, extension))

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}

/** Writes to [partial], and moves it to [target] only once the whole book is there. */
private class AndroidFileBookWriter(
    private val output: OutputStream,
    private val partial: File,
    private val target: File,
) : BookFileWriter {

    override suspend fun write(bytes: ByteArray, count: Int) = withContext(Dispatchers.IO) {
        output.write(bytes, 0, count)
    }

    override suspend fun commit() = withContext(Dispatchers.IO) {
        output.close()
        target.delete()
        check(partial.renameTo(target)) { "Could not store ${target.name}" }
    }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        output.close()
        partial.delete()
        Unit
    }
}
