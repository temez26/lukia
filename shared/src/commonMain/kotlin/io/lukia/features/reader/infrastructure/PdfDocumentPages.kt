package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.DocumentPages

/**
 * Opens the PDF at [path] for reading, using whatever the platform draws PDFs
 * with. Throws if the file is not a PDF this platform can open.
 *
 * [path] is always a working copy in app storage, which is what lets every
 * platform take a plain file path here.
 */
internal expect fun openPdfPages(path: String): DocumentPages
