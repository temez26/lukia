package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.page.PageContent

/**
 * Opens the PDF at [path] for reading, using whatever the platform draws PDFs
 * with. Throws if the file is not a PDF this platform can open.
 *
 * [path] is always a working copy in app storage, which is what lets every
 * platform take a plain file path here.
 */
internal expect fun openPdfPages(path: String): DocumentPages

/**
 * Opens the PDF at [path] to be read rather than drawn - see
 * [io.lukia.features.reader.domain.page.PageContent].
 *
 * Separate from [openPdfPages] because it is opened for a different reason and
 * so should do different work: this is what a conversion opens one of per core,
 * and none of them is ever asked to draw a page, measure one or list the
 * chapters.
 */
internal expect fun openPdfPageContent(path: String): PageContent
