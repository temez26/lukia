package io.lukia.features.reader.infrastructure.text.reflow

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.infrastructure.fileSizeBytes
import io.lukia.core.infrastructure.readFileBytes
import io.lukia.core.infrastructure.writeFileBytes
import io.lukia.features.reader.domain.ReflowableContent

/**
 * Where a converted book is kept, so that a PDF read as text is read once.
 *
 * Converting a book is the most expensive thing the reader does: every page
 * opened, its characters read out with their positions, the whole inferred back
 * into paragraphs. On a long book that is seconds of work and a progress bar -
 * and before this, seconds every time somebody looked at the same book the other
 * way and looked back.
 *
 * The note is written beside the working copy under the same name, which is not
 * tidiness: a workspace discards a document by deleting everything whose name
 * begins with that document's prefix, so a removed book takes its conversion
 * with it without the workspace being told conversions exist. The length of the
 * copy guards the rest - see `asRecord`.
 *
 * Best effort throughout: a conversion that cannot be written happens again next
 * time, which is what used to happen every time, so there is nothing here worth
 * failing an open over or telling the reader about.
 */
internal fun storedReflow(path: String): ReflowableContent? {
    val sourceSize = fileSizeBytes(path) ?: return null

    return runCatching { readRecord(readFileBytes(path.asRecordPath()), sourceSize) }.getOrNull()
}

internal fun storeReflow(
    path: String,
    content: ReflowableContent,
    diagnostics: Diagnostics = Diagnostics.None,
) {
    val sourceSize = fileSizeBytes(path) ?: return

    runCatching { writeFileBytes(path.asRecordPath(), content.asRecord(sourceSize)) }
        .onFailure {
            // Only the write. A read that fails is the ordinary first open of
            // every book - there is no record yet - and recording that would
            // bury everything else under one entry per PDF ever opened. A write
            // that fails is different: it means this book is converted from
            // scratch every time it is opened, for as long as the reader owns
            // it, and nothing else would ever say so.
            diagnostics.warn(
                area = DiagnosticArea.Reading,
                event = "reflow-cache-write-failed",
                cause = it,
            )
        }
}

/**
 * Appended rather than swapped for the document's own extension, so that the
 * name still begins with the whole of the working copy's - which is what keeps
 * it inside the prefix a workspace discards by.
 */
private fun String.asRecordPath(): String = "$this.text"
