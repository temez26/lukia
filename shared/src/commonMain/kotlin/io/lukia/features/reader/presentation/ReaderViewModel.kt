package io.lukia.features.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.domain.reading.PdfLayoutRepository
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.domain.outline.asTableOfContents
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.domain.page.pageSentences
import io.lukia.features.reader.domain.text.TextExtraction
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.presentation.rendering.FigureImageCache
import io.lukia.features.reader.presentation.rendering.PageSentenceCache
import io.lukia.features.reader.presentation.rendering.ReaderLayoutChoice
import io.lukia.features.reader.presentation.rendering.RenderedPageCache
import io.lukia.features.reader.presentation.rendering.renderWidthFor
import io.lukia.features.reader.presentation.translation.ReaderTranslationsFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val location: String,
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val settingsRepository: ReaderSettingsRepository,
    reading: BookReadingFactory,
    layoutRepository: PdfLayoutRepository,
    translations: ReaderTranslationsFactory,
    /**
     * Where the outline is read off the file.
     *
     * Injected rather than reached for, because this is the one piece of work
     * here that a caller cannot wait for: it is started beside the open book
     * rather than as part of it, so nothing the reader holds finishes when it
     * does. A test that has to take its dispatchers away at the end otherwise
     * races it, and a scan of a long PDF is long enough to lose.
     */
    private val outlineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Where a page's words are gathered out of its glyphs. Injectable for the
     * same reason [outlineDispatcher] is: a test that takes its dispatchers away
     * should not race work nobody is waiting for.
     */
    private val sentenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /**
     * Everything about translating what is on the page. Held here so the reader
     * screen sees one object, but implemented apart - see [ReaderTranslations].
     */
    internal val translation = translations.create(viewModelScope)

    /**
     * Where this book was left off and how long it has been read for - see
     * [BookReading]. It writes as it goes, so leaving the reader needs nothing
     * from here.
     */
    private val record = reading.create(location, viewModelScope)

    /**
     * Whether this book is read as its own pages or as text, and what came of
     * asking for the second - see [ReaderLayoutChoice].
     */
    private val layoutChoice = ReaderLayoutChoice(location, layoutRepository) {
        settingsRepository.settings.value.defaultPdfLayout
    }

    val pdfLayout: StateFlow<PdfLayout> = layoutChoice.layout
    val layoutOffered: StateFlow<Boolean> = layoutChoice.offered
    val textExtraction: StateFlow<TextExtraction?> = layoutChoice.extraction
    val noTextLayer: StateFlow<Boolean> = layoutChoice.noTextLayer

    private val _uiState = MutableStateFlow<UiState<ReaderDocument>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReaderDocument>> = _uiState.asStateFlow()

    /**
     * Where to open, once it is known - null while the book is still being
     * opened and the stored position still being read.
     *
     * The length it was stored against travels with it, because a reflowable
     * book's pages are the reader's own: the same book is a different number of
     * pages in a different window, so a page number only means something
     * alongside the count it was one of.
     *
     * Settled once per opening and never changed, so the screen can treat it as
     * an instruction to carry out rather than a position to follow - a value
     * that tracked the reader would send them back to the start every time it
     * arrived.
     */
    val initialPosition: StateFlow<ReadingProgress?> = record.initialPosition

    /**
     * Exposed straight from the repository rather than copied into this
     * ViewModel: the settings outlive the open book, and mirroring them here
     * would be one more thing that could disagree with what was stored.
     */
    val settings: StateFlow<ReaderSettings> = settingsRepository.settings

    private val _outline = MutableStateFlow<ReaderOutline>(ReaderOutline.Loading)
    val outline: StateFlow<ReaderOutline> = _outline.asStateFlow()

    fun updateSettings(settings: ReaderSettings) {
        settingsRepository.update(settings)
    }

    private val renderedPages = RenderedPageCache()

    init {
        open()
    }

    /**
     * The opening in progress, so that a second one can stop the first.
     *
     * It matters now that a book can take a while to open: reading a long PDF's
     * text is seconds of work, and somebody who changes their mind halfway
     * through would otherwise have the book they abandoned arrive over the book
     * they asked for.
     */
    private var opening: Job? = null

    private fun open() {
        // Belt and braces: [reopen] has already cancelled by the time it calls
        // this, and the first opening of all has nothing to cancel. It stays so
        // that "only one opening at a time" holds for this function on its own
        // rather than only for the one route into it that remembers.
        opening?.cancel()

        opening = viewModelScope.launch {
            val layout = layoutChoice.restore()

            // getOrThrow re-raises the use case's own failure so that it and an
            // unanticipated throw are both narrowed in the same place.
            val state = runCatchingApp {
                openDocumentUseCase(location, layout, layoutChoice::onProgress).getOrThrow()
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Failure(it.toAppError()) },
            )

            // Cancelled between the file being opened and this line, which is
            // the one window the suspension points cannot cover. What was
            // opened is handed straight back rather than left holding the file.
            if (!isActive) {
                state.dataOrNull?.close()
                return@launch
            }

            _uiState.value = state

            if (state is UiState.Success) {
                layoutChoice.settle(state.data, openedAs = layout)
                record.restore(state.data)
                loadOutline(state.data)

                // Only once there is a book on screen: a file that could not be
                // opened is not an evening's reading.
                record.begin()
            }
        }
    }

    /**
     * Reads this book the other way.
     *
     * The book is opened again rather than converted where it sits: what a PDF
     * is as text and what it is as pages are two different documents made from
     * one file, and one of them holds a handle on that file. Where the reader
     * was is not lost with it - the position is stored as they read, and both
     * ways of reading restore it as a fraction of the book.
     */
    fun setPdfLayout(layout: PdfLayout) {
        if (layoutChoice.layout.value == layout) return

        viewModelScope.launch {
            layoutChoice.choose(layout)
            reopen()
        }
    }

    /** The reader has seen that this PDF has no text in it to re-set. */
    fun dismissNoTextLayerNotice() {
        layoutChoice.dismissNotice()
    }

    /**
     * Records where the reader has come to rest, and that they are still there.
     *
     * Called when a scroll settles rather than as it moves: a fling through a
     * long book passes hundreds of pages the reader never saw, and storing each
     * of them would be hundreds of writes to arrive at the one page that
     * mattered.
     */
    fun onPageSettled(pageIndex: Int, pageCount: Int) {
        // Nothing to remember about a book that never opened.
        if (_uiState.value.dataOrNull == null) return

        record.onPageSettled(pageIndex, pageCount)
    }

    private val pageSentences = PageSentenceCache()

    /**
     * What is written on page [index], or an empty list when nothing is - a
     * scan, a page that is one photograph, or a platform that cannot read one.
     *
     * All of those reach the reader the same way, which is that tapping the page
     * does nothing. None of them is worth telling somebody about.
     */
    suspend fun sentencesOn(index: Int): List<PageSentence> {
        val pages = (_uiState.value.dataOrNull?.content as? PagedContent)?.pages ?: return emptyList()

        return pageSentences.sentencesOn(index) {
            // Off the main thread for the grouping as well as the reading. The
            // document switches for itself before it touches the file, but what
            // comes back is every glyph on the page, and gathering those into
            // words and sentences is a pass over all of them - which would
            // otherwise run wherever this was asked from. The reader asks from
            // composition, as each page arrives.
            withContext(sentenceDispatcher) {
                val glyphs = runCatchingApp { pages.pageText(index) }.getOrNull()
                glyphs?.let { pageSentences(it) }.orEmpty()
            }
        }
    }

    /**
     * The chapter list being read, so that a book put away can stop it.
     *
     * Its own job rather than part of [opening], which deliberately does not
     * wait for it - see [loadOutline]. That is also what made it outlive a
     * reopening: cancelling the opening left this running, to publish the
     * abandoned book's chapters over the ones being read, having gone to a
     * closed document to get them.
     */
    private var outlining: Job? = null

    /**
     * Read in the background as soon as the book is open, rather than when the
     * contents panel asks for it.
     *
     * On platforms whose PDF renderer has no outline of its own this means
     * scanning the file - far too much to do while the reader waits with a panel
     * open, and far too much for the thread drawing the pages. Starting it here
     * means it is almost always ready before anyone looks.
     */
    private fun loadOutline(document: ReaderDocument) {
        outlining = viewModelScope.launch {
            val entries = withContext(outlineDispatcher) {
                runCatchingApp { document.content.outline?.asTableOfContents() }.getOrNull()
            }

            _outline.value = entries
                ?.let { ReaderOutline.Chapters(it) }
                ?: ReaderOutline.Unavailable
        }
    }

    fun retry() {
        reopen()
    }

    /**
     * Puts the book back to before it was opened, and opens it again.
     *
     * Everything held about the open book goes with it: the document's own
     * handle, the translations anchored into it, and the text read off its
     * pages. All three are about the document that is being replaced - anchors
     * point at sections a re-set book numbers differently, and a page's
     * sentences are a fact about a page that may no longer exist.
     */
    private fun reopen() {
        // Stopped first, before any of the putting back.
        //
        // Everything below is about a book no longer being opened, and an
        // opening still in flight can still publish - putting the abandoned book
        // back over the top of the reset, and leaving its file handle where
        // nothing will close it. The app never sees that, since its main
        // dispatcher is one thread and none of this suspends; cancelling here
        // rather than on the way into [open] makes that a property of this code
        // rather than of the dispatcher.
        //
        // The chapter list goes with it. It is not part of the opening - see
        // [outlining] - so cancelling that alone left it running against a
        // document about to be closed, to publish its chapters over the book
        // that replaced it.
        opening?.cancel()
        outlining?.cancel()

        closeDocument()
        translation.clear()
        pageSentences.clear()
        _uiState.value = UiState.Loading
        _outline.value = ReaderOutline.Loading
        record.forgetOpeningPosition()
        open()
    }

    /**
     * The page at [index], drawn to fit [widthPx], or null if that page cannot
     * be drawn.
     *
     * Called once per page as it scrolls into view. Rendering runs off the main
     * thread inside the document itself; what this adds is the cache, without
     * which every page would be redrawn each time it scrolled back into view.
     */
    suspend fun page(index: Int, widthPx: Int): ImageBitmap? {
        // Only a fixed-layout document has pages to draw. A reflowable one is
        // set by the reader itself and never comes through here.
        val pages = (_uiState.value.dataOrNull?.content as? PagedContent)?.pages ?: return null
        if (index !in 0 until pages.pageCount) return null

        val targetWidthPx = renderWidthFor(widthPx)

        return renderedPages.page(index, targetWidthPx) {
            pages.renderPage(index, targetWidthPx)
        }
    }

    /**
     * The open document holds a file handle on the working copy, so leaving the
     * reader has to give it back rather than waiting for a finalizer.
     */
    override fun onCleared() {
        closeDocument()
    }

    private fun closeDocument() {
        _uiState.value.dataOrNull?.close()
        renderedPages.clear()
        FigureImageCache.clear()
    }
}
