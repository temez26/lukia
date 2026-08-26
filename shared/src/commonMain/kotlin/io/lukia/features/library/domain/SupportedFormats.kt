package io.lukia.features.library.domain

import io.lukia.core.domain.DocumentType

/**
 * Folder scanners filter on this before handing a file to the import use case,
 * which would otherwise reject it as an unsupported type anyway - skipping it
 * up front keeps "12 imported, 0 failed" from counting every stray file in the
 * folder as a failure.
 */
fun isSupportedDocument(extension: String): Boolean =
    DocumentType.fromExtension(extension) != DocumentType.UNKNOWN
