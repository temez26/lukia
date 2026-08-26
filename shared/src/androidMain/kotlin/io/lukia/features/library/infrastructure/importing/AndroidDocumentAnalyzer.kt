package io.lukia.features.library.infrastructure.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.features.library.domain.DocumentAnalyzer

class AndroidDocumentAnalyzer(
    private val context: Context,
) : DocumentAnalyzer {

    override suspend fun analyze(
        document: PickedDocument,
    ): DocumentInfo {
        val uri = Uri.parse(document.location)

        val resolver = context.contentResolver

        var exists = false
        var size = 0L

        resolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->

            if (cursor.moveToFirst()) {
                exists = true

                val sizeIndex =
                    cursor.getColumnIndex(OpenableColumns.SIZE)

                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        if (!exists) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}",
            )
        }

        val type = DocumentType.fromExtension(document.extension)

        val contents = if (type == DocumentType.UNKNOWN) {
            DocumentContents.Empty
        } else {
            runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
                ?.let { extractContents(type, it) }
                ?: DocumentContents.Empty
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = exists,
            sizeBytes = size,
            coverImage = contents.coverImage,
            metadata = contents.metadata,
        )
    }
}
