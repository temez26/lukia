package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.PdfLayout
import io.lukia.features.reader.domain.TextExtraction

/**
 * What the screen needs to know about how this book is being read, gathered so
 * that the panel and the notice take it as one thing.
 *
 * These travel together for the same reason [ReaderTranslationUi]'s seven do:
 * they are one setting's worth of state and the callbacks that change it, and
 * passing them one by one is what turns a screen into a signature.
 */
@Immutable
internal data class ReaderLayoutUi(
    val layout: PdfLayout = PdfLayout.Original,
    /**
     * Whether this book takes the choice at all - a PDF does, an EPUB never
     * did. Told rather than worked out from the open book, because a book part
     * way through being converted has no open book to ask.
     */
    val offered: Boolean = false,
    /** How far through reading the file's text, or null when nothing is being read. */
    val converting: TextExtraction? = null,
    /** Whether the last opening asked for text and found a document with none. */
    val noTextLayer: Boolean = false,
    val onLayoutChange: (PdfLayout) -> Unit = {},
    val onNoticeDismiss: () -> Unit = {},
) {

    /**
     * What the reader is told while the book is opening.
     *
     * Page by page while a PDF's text is being read, because that is the one
     * wait in the reader long enough to need a number: a spinner over a
     * six-hundred page book is indistinguishable from one that has hung.
     * Everything else opens quickly enough to say only that it is opening.
     */
    val loadingMessage: String
        get() = converting
            ?.let { "Reading text… page ${it.pagesRead} of ${it.pageCount}" }
            ?: "Opening…"
}
