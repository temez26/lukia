package io.lukia.features.import.infrastructure

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentType
import io.lukia.features.import.domain.PickedDocument

class AndroidDocumentAnalyzer(
    private val context: Context
) : DocumentAnalyzer {

    override suspend fun analyze(
        document: PickedDocument
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
            null
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
                "File does not exist: ${document.location}"
            )
        }

        val type = when (document.extension.lowercase()) {
            "pdf" -> DocumentType.PDF
            "epub" -> DocumentType.EPUB
            else -> DocumentType.UNKNOWN
        }

        return DocumentInfo(
            document = document,
            type = type,
            exists = exists,
            sizeBytes = size
        )
    }
}