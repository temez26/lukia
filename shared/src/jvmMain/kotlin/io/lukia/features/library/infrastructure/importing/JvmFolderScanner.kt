package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.PickedFolder
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.domain.MaxFolderScanDepth
import io.lukia.features.library.domain.isSupportedDocument
import java.io.File

/** Walks the folder and everything filed under it, down to [MaxFolderScanDepth]. */
class JvmFolderScanner : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> {
        val root = File(folder.location)

        return root
            .walkTopDown()
            .maxDepth(MaxFolderScanDepth)
            // Nothing a reader put in a library on purpose lives in a dot
            // directory, and plenty that would slow the walk down does -
            // `.git`, `.Trash`, a sync client's private cache.
            //
            // The folder the reader actually pointed at is the exception: they
            // chose it, dot or no dot, and a scan of it that came back empty
            // would look like a library that had refused them without saying so.
            .onEnter { it == root || !it.isHiddenDirectory() }
            .filter { it.isFile && isSupportedDocument(it.extension) }
            .map { file ->
                PickedDocument(
                    fileName = file.name,
                    extension = file.extension,
                    location = file.absolutePath,
                )
            }
            .toList()
    }
}

/**
 * `File.isHidden` answers a different question on each platform: on Unix it is
 * the leading dot, on Windows the DOS hidden attribute - which a `.cache`
 * written by a sync client, or a folder copied over from a Mac, does not carry.
 * Asking both ways is what makes the walk skip the same directories wherever
 * the desktop app is run.
 */
private fun File.isHiddenDirectory(): Boolean = isHidden || name.startsWith(".")
