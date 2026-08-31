package io.lukia.features.reader.infrastructure.text.reflow

import io.lukia.core.infrastructure.fileSizeBytes
import io.lukia.core.infrastructure.readFileBytes
import io.lukia.core.infrastructure.writeFileBytes
import io.lukia.features.reader.domain.ReflowableContent

/**
 * Where a converted book is kept, so that a PDF read as text is read once.
 *
 * Converting a book is the most expensive thing the reader does: every page of
 * the file is opened, its characters are read out with their positions, and the
 * whole of it is inferred back into paragraphs. On a long book that is seconds
 * of work and a progress bar - and before this, it was seconds of work every
 * time somebody looked at the same book the other way and looked back.
 *
 * The note is written beside the working copy it was made from, under the same
 * name. That is not tidiness: every workspace discards a document by deleting
 * everything whose name begins with that document's own prefix, so a book
 * removed from the library takes its conversion with it without the workspace
 * being told that conversions exist. What guards the rest is the length of the
 * copy the note was made from - see `asRecord`.
 *
 * Best effort throughout. A conversion that cannot be written is a conversion
 * that happens again next time, which is what used to happen every time, so
 * there is nothing here worth failing an open over or telling the reader about.
 */
internal fun storedReflow(path: String): ReflowableContent? {
    val sourceSize = fileSizeBytes(path) ?: return null

    return runCatching { readRecord(readFileBytes(path.asRecordPath()), sourceSize) }.getOrNull()
}

internal fun storeReflow(path: String, content: ReflowableContent) {
    val sourceSize = fileSizeBytes(path) ?: return

    runCatching { writeFileBytes(path.asRecordPath(), content.asRecord(sourceSize)) }
}

/**
 * Appended rather than swapped for the document's own extension, so that the
 * name still begins with the whole of the working copy's - which is what keeps
 * it inside the prefix a workspace discards by.
 */
private fun String.asRecordPath(): String = "$this.text"
