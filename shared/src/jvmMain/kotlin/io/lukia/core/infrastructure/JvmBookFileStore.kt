package io.lukia.core.infrastructure

import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.BookFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Downloaded books, beside the copies of books handed to the app - see [BookFileStore]. */
class JvmBookFileStore(
    private val directory: File = File(lukiaAppDirectory(), INCOMING_DIRECTORY),
) : BookFileStore {

    override fun locationFor(contentHash: String, extension: String): String =
        File(directory, IncomingDocumentNaming.serverBookName(contentHash, extension)).absolutePath

    override suspend fun open(contentHash: String, extension: String): BookFileWriter = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(locationFor(contentHash, extension))
        val partial = File(directory, "${target.name}$PARTIAL_SUFFIX")

        FileBookWriter(partial.outputStream(), partial, target)
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}

/** Writes to [partial], and moves it to [target] only once the whole book is there. */
private class FileBookWriter(
    private val output: OutputStream,
    private val partial: File,
    private val target: File,
) : BookFileWriter {

    override suspend fun write(bytes: ByteArray, count: Int) = withContext(Dispatchers.IO) {
        output.write(bytes, 0, count)
    }

    override suspend fun commit() = withContext(Dispatchers.IO) {
        output.close()
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        output.close()
        partial.delete()
        Unit
    }
}
