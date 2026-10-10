package io.lukia.features.library.infrastructure.importing

import java.io.File

/**
 * [bytes] as a document on disk, opened the way the import opens one.
 *
 * A real file rather than a stand-in: the point of [JvmDocumentSource] is that
 * PDFBox is handed the file itself, and a fake answering from memory would test
 * the one arrangement this no longer uses.
 */
internal fun <R> openedDocument(bytes: ByteArray, block: (JvmDocumentSource) -> R): R {
    val file = File.createTempFile("lukia-source-", ".document")

    return try {
        file.writeBytes(bytes)
        JvmDocumentSource(file).use(block)
    } finally {
        file.delete()
    }
}
