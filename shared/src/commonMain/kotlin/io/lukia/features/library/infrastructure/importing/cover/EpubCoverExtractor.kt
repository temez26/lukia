package io.lukia.features.library.infrastructure.importing.cover

import io.lukia.core.infrastructure.epub.EpubPackage

/**
 * Extracts the cover image embedded in an EPUB archive, if one is declared.
 * Returns null whenever the archive is malformed or no cover can be located,
 * rather than throwing - a missing cover is an expected, non-fatal outcome.
 */
internal fun extractEpubCover(epubBytes: ByteArray): ByteArray? {
    val epub = EpubPackage.open(epubBytes) ?: return null
    val coverPath = epub.coverImagePath() ?: return null

    return epub.read(coverPath)
}
