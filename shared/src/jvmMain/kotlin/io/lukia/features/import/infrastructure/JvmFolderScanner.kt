package io.lukia.features.import.infrastructure

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.domain.MaxFolderScanDepth
import io.lukia.features.import.domain.isSupportedDocument
import java.io.File

/** Walks the folder and everything filed under it, down to [MaxFolderScanDepth]. */
class JvmFolderScanner : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> =
        File(folder.location)
            .walkTopDown()
            .maxDepth(MaxFolderScanDepth)
            // Nothing a reader put in a library on purpose lives in a dot
            // directory, and plenty that would slow the walk down does -
            // `.git`, `.Trash`, a sync client's private cache.
            .onEnter { !it.isHidden }
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
