package io.lukia.features.library.infrastructure

import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.importing.DocumentSource

/**
 * Opens a book on the shelf to read its bytes a window at a time - to hash it,
 * or to send it to a sync server - so a book of any size costs one window of
 * memory. Each platform's analyser is what does it, from the same handles the
 * import opens.
 */
fun interface BookFileReader {

    /** Null for a file that cannot be opened now - gone, or not permitted. The caller closes it. */
    suspend fun open(document: PickedDocument): DocumentSource?
}
