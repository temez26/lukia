package io.lukia.features.library.domain

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.PickedDocument

interface DocumentAnalyzer {
    suspend fun analyze(document: PickedDocument): DocumentInfo

    /**
     * Whether the file [document] names is still where the library filed it.
     *
     * The cheap half of [analyze], apart from it because the answer is needed
     * where the expensive half must not run: [analyze] opens the file, reads all
     * of it and renders a cover, and throws for a book that has gone exactly as
     * for one it cannot make sense of. To a shelf those are different - one is a
     * book to re-read, the other a book to tell the reader about.
     */
    suspend fun exists(document: PickedDocument): Boolean
}
