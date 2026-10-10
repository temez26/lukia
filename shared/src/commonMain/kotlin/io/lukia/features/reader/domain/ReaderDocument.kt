package io.lukia.features.reader.domain

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.storage.WorkingCopy

/**
 * One book, open for reading.
 *
 * [content] is read from [workingCopy], never from the file the user imported -
 * see [WorkingCopy]. The original is not even open while reading.
 */
class ReaderDocument(
    val info: DocumentInfo,
    val workingCopy: WorkingCopy,
    val content: ReaderContent,
) : AutoCloseable {

    override fun close() {
        content.close()
    }
}
