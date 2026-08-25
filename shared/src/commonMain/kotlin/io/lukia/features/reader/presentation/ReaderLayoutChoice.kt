package io.lukia.features.reader.presentation

import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.reader.domain.PdfLayout
import io.lukia.features.reader.domain.PdfLayoutRepository
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextExtraction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of the two ways of reading this book is in force, and what became of
 * asking for the second one.
 *
 * Split from [ReaderViewModel] for the same reason [ReaderTranslations] is:
 * opening a book and keeping the reader's place is one job, and remembering that
 * this particular PDF is read as text is another. What is here is only the
 * choice - making it, remembering it, and reporting the two things the reader
 * needs to be told about it, which are how far through the conversion the app is
 * and that a file turned out to have no text in it at all.
 */
internal class ReaderLayoutChoice(
    private val location: String,
    private val repository: PdfLayoutRepository,
    /**
     * What a book nobody has chosen for opens as.
     *
     * Asked at the moment it is needed rather than captured once, so a reader
     * who changes the setting and then opens a book they have never opened gets
     * what they have just chosen rather than what was set when this was built.
     */
    private val defaultLayout: () -> PdfLayout,
) {

    private val _layout = MutableStateFlow(defaultLayout())
    val layout: StateFlow<PdfLayout> = _layout.asStateFlow()

    private val _extraction = MutableStateFlow<TextExtraction?>(null)

    /** How far through reading the file's text, or null when nothing is being read. */
    val extraction: StateFlow<TextExtraction?> = _extraction.asStateFlow()

    private val _offered = MutableStateFlow(false)

    /**
     * Whether this book is one the choice applies to at all, which is to say a
     * PDF.
     *
     * Learned from the book the first time it opens, and then kept - which is
     * the whole point of it living here rather than being read off whatever is
     * open. Converting a PDF to text is seconds of work during which nothing is
     * open, and a control that appeared only alongside an open book went missing
     * for exactly that stretch: the reader who had just asked for the
     * conversion, watched the progress and thought better of it had no way back.
     *
     * Not known before a book has ever opened, so the first opening of one
     * stored as text - which is a book converted once and closed before it
     * finished - still has to wait. What that used to be, every reopening of
     * every book read as text, is now answered from the conversion kept beside
     * the file.
     */
    val offered: StateFlow<Boolean> = _offered.asStateFlow()

    private val _noTextLayer = MutableStateFlow(false)

    /**
     * Whether the last opening was asked for as text and could not be given as
     * text, which is a scan - pages of pictures of words, with no words in the
     * file to re-set. The one thing here somebody has to be told, because the
     * book they get is not the book they asked for.
     */
    val noTextLayer: StateFlow<Boolean> = _noTextLayer.asStateFlow()

    /** Whether the choice in hand is the reader's rather than an assumed default. */
    private var settled = false

    /**
     * The choice this book should be opened as: what was stored for it, or what
     * has been chosen since.
     *
     * Read from the store once. Afterwards the choice in hand is the newer of
     * the two by definition, and re-reading would undo a choice whose write
     * failed - which would show the reader the setting they did not pick beside
     * the book they did.
     *
     * A book with nothing stored for it, and a store that cannot be read at all,
     * both open as whatever new books are set to open as. The two are worth
     * collapsing: neither is a choice the reader made about this book, and there
     * is nothing better to show them than the one they made about all of them.
     */
    suspend fun restore(): PdfLayout {
        _noTextLayer.value = false

        if (settled) return _layout.value

        _layout.value = runCatchingApp { repository.layoutFor(location) }.getOrNull()
            ?: defaultLayout()
        settled = true

        return _layout.value
    }

    /** Remembers a new choice. The reader re-opens the book against it. */
    suspend fun choose(layout: PdfLayout) {
        _layout.value = layout
        _noTextLayer.value = false
        settled = true

        runCatchingApp { repository.save(location, layout) }
    }

    fun onProgress(extraction: TextExtraction) {
        _extraction.value = extraction
    }

    /**
     * What the book actually opened as.
     *
     * A PDF asked for as text that came back as pages has no text layer. The
     * choice is put back to the pages rather than left standing, so that opening
     * the book again does not read the whole file a second time to reach the
     * same answer - and so that what the settings panel shows is what the reader
     * is looking at.
     */
    suspend fun settle(document: ReaderDocument) {
        _extraction.value = null

        if (document.info.type == DocumentType.PDF) _offered.value = true

        if (!_layout.value.asText || document.content is ReflowableContent) return

        choose(PdfLayout.Original)
        _noTextLayer.value = true
    }

    /** The reader has been told; the notice is not shown again for this book. */
    fun dismissNotice() {
        _noTextLayer.value = false
    }
}
