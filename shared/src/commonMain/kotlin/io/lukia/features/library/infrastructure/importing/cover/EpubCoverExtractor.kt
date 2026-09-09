package io.lukia.features.library.infrastructure.importing.cover

import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.epub.EpubPackage

/**
 * Extracts the cover image embedded in an EPUB archive, if one is declared.
 * Returns null whenever the archive is malformed or no cover can be located,
 * rather than throwing - a missing cover is an expected, non-fatal outcome.
 */
internal fun extractEpubCover(epub: ByteSource): ByteArray? {
    val book = EpubPackage.open(epub) ?: return null
    val coverPath = book.coverImagePath() ?: return null

    return book.read(coverPath)
}
