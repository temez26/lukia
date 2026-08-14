package io.lukia.features.import.infrastructure

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.domain.isSupportedDocument
import java.io.File

/** Scans the folder itself only - sub-folders are left alone. */
class JvmFolderScanner : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> =
        File(folder.location)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && isSupportedDocument(it.extension) }
            .map { file ->
                PickedDocument(
                    fileName = file.name,
                    extension = file.extension,
                    location = file.absolutePath
                )
            }
}
