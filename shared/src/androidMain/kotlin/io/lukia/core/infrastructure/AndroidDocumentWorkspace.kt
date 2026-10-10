package io.lukia.core.infrastructure

import android.content.Context
import android.net.Uri
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.WorkingCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies land in app-private internal storage, so they need no permission to
 * read back and are removed with the app.
 *
 * The copy matters more here than on the other platforms: a picked document is
 * addressed by a content:// URI whose permission grant can be revoked, and the
 * platform PDF renderer wants a real file descriptor rather than a stream.
 */
class AndroidDocumentWorkspace(
    private val context: Context,
) : DocumentWorkspace {

    private val directory: File
        get() = File(context.filesDir, WORKSPACE_DIRECTORY)

    override suspend fun workingCopy(document: DocumentInfo): WorkingCopy =
        withContext(Dispatchers.IO) {
            val location = document.document.location
            val target = File(
                directory,
                WorkingCopyNaming.fileName(location, document.document.extension),
            )

            // A zero-length copy is treated as absent rather than reused: it is
            // what a copy interrupted before the rename would leave behind.
            if (!target.isFile || target.length() == 0L) {
                directory.mkdirs()
                copyInto(Uri.parse(location), target)
            }

            WorkingCopy(sourceLocation = location, path = target.absolutePath)
        }

    override suspend fun discard(location: String) {
        withContext(Dispatchers.IO) {
            val prefix = WorkingCopyNaming.prefix(location)
            directory.listFiles()
                ?.filter { it.name.startsWith(prefix) }
                ?.forEach { it.delete() }
        }
    }

    /**
     * Streams through a scratch file and renames it into place, so a copy cut
     * short by the process dying never leaves a half-written file under the name
     * everything downstream trusts.
     */
    private fun copyInto(source: Uri, target: File) {
        val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")

        try {
            val stream = context.contentResolver.openInputStream(source)
                ?: throw IllegalArgumentException("File does not exist: $source")

            stream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }

            check(partial.renameTo(target)) { "Could not store a copy of $source" }
        } finally {
            partial.delete()
        }
    }

    private companion object {
        const val WORKSPACE_DIRECTORY = "documents"
        const val PARTIAL_SUFFIX = ".part"
    }
}
