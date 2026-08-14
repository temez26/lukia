package io.lukia.features.import.infrastructure

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.domain.isSupportedDocument

/**
 * Enumerates a tree URI granted by the folder picker. The child URIs are the
 * same shape the file picker produces, so [AndroidDocumentAnalyzer] can resolve
 * them without any special handling.
 */
class AndroidFolderScanner(
    private val context: Context
) : FolderScanner {

    override suspend fun scan(folder: PickedFolder): List<PickedDocument> {

        val tree = DocumentFile.fromTreeUri(
            context,
            Uri.parse(folder.location)
        ) ?: return emptyList()

        return tree.listFiles()
            .filter { it.isFile }
            .mapNotNull { file ->

                val fileName = file.name ?: return@mapNotNull null

                val extension = fileName.substringAfterLast('.', "")

                if (!isSupportedDocument(extension)) {
                    return@mapNotNull null
                }

                PickedDocument(
                    fileName = fileName,
                    extension = extension,
                    location = file.uri.toString()
                )
            }
    }
}
