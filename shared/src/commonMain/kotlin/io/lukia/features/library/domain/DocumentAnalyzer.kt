package io.lukia.features.library.domain

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.PickedDocument

interface DocumentAnalyzer {
    suspend fun analyze(document: PickedDocument): DocumentInfo

    /**
     * Whether the file [document] names is still where the library filed it.
     *
     * The cheap half of [analyze], and apart from it because the answer is
     * needed where the expensive half must not run: [analyze] opens the file,
     * reads all of it and renders a cover, and it throws for a book that has
     * gone exactly as it throws for one it cannot make sense of. Those two are
     * different things to a shelf - one is a book to re-read, the other is a
     * book to tell the reader about - and this is what tells them apart.
     */
    suspend fun exists(document: PickedDocument): Boolean
}
