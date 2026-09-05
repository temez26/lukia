package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.page.PageContent

/**
 * Opens the PDF at [path] for reading, using whatever the platform draws PDFs
 * with, or null when the file is not a PDF this platform can open.
 *
 * Null rather than a throw, for two reasons. A damaged PDF then reaches the
 * reader as the same "this file cannot be read" every other format answers
 * with - see `openReaderContent` - rather than as an unexpected failure, which
 * is what it was: PDF was the one format here that threw. And whatever a
 * half-open document had already taken is given back before the null, instead
 * of being left to a finalizer. On Android the file is opened before the
 * renderer that goes on to refuse it, and a document whose construction never
 * finished has no close for anybody to call, so every attempt cost a file
 * handle - and Try again is one press.
 *
 * [path] is always a working copy in app storage, which is what lets every
 * platform take a plain file path here.
 */
internal expect fun openPdfPages(path: String): DocumentPages?

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
