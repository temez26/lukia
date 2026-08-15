package io.lukia.core.infrastructure

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.domain.WorkingCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class JvmDocumentWorkspace(
    /** Injectable so tests can work in a temporary directory. */
    private val directory: File = defaultWorkspaceDirectory()
) : DocumentWorkspace {

    override suspend fun workingCopy(document: DocumentInfo): WorkingCopy =
        withContext(Dispatchers.IO) {
            val location = document.document.location
            val target = File(
                directory,
                WorkingCopyNaming.fileName(location, document.document.extension)
            )

            // A zero-length copy is treated as absent rather than reused: it is
            // what a copy interrupted before the move would leave behind.
            if (!target.isFile || target.length() == 0L) {
                val source = File(location)
                require(source.isFile) { "File does not exist: $location" }

                directory.mkdirs()
                source.copyInto(target)
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
     * Copies through a scratch file and moves it into place, so a copy cut short
     * by the process dying never leaves a half-written file under the name
     * everything downstream trusts.
     */
    private fun File.copyInto(target: File) {
        val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")

        try {
            copyTo(partial, overwrite = true)
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}

/**
 * Per-user app storage: `%LOCALAPPDATA%\Lukia` where Windows offers it, and a
 * dotted home directory elsewhere, which is what Linux and macOS expect.
 */
private fun defaultWorkspaceDirectory(): File {
    val localAppData = System.getenv("LOCALAPPDATA")

    val base = if (localAppData.isNullOrBlank()) {
        File(System.getProperty("user.home"), ".lukia")
    } else {
        File(localAppData, "Lukia")
    }

    return File(base, "documents")
}
